package com.tazzzo.app.auth

import com.tazzzo.app.data.auth.AuthSessionManager
import com.tazzzo.app.data.auth.RemoteAuthDataSource
import io.ktor.client.engine.mock.respond
import io.ktor.client.request.HttpRequestData
import io.ktor.http.HttpStatusCode
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class AuthSessionManagerTest {

    /** Scripted backend: counts calls, lets a test choose each refresh/logout answer. */
    private class Backend {
        var refreshCalls = 0
        var logoutCalls = 0
        val refreshBodies = mutableListOf<String>()
        val logoutBearers = mutableListOf<String?>()
        var gate: CompletableDeferred<Unit>? = null
        var refreshAnswer: (Int) -> Pair<HttpStatusCode, String> = { n -> HttpStatusCode.OK to refreshJson("acc${n + 1}", "SES_abc.ref${n + 1}") }
        var logoutStatus = HttpStatusCode.NoContent
        var logoutThrows = false
    }

    private var now = 1_000_000L

    /** MockEngine handlers run off the test dispatcher: wait in real time until the refresh is in flight. */
    private suspend fun awaitRefreshInFlight(b: Backend) = withContext(Dispatchers.Default) {
        withTimeout(5_000) { while (b.refreshCalls == 0) delay(5) }
        delay(100) // grace for any erroneous second call to show up
    }

    private fun TestScope.manager(
        b: Backend,
        store: InMemorySecureTokenStore = InMemorySecureTokenStore(),
    ): AuthSessionManager {
        val api = apiClient { req: HttpRequestData ->
            when (req.url.encodedPath) {
                "/v1/auth/session" -> respond(sessionJson(expiresIn = 900), HttpStatusCode.OK, JSON_HEADERS)
                "/v1/auth/refresh" -> {
                    b.refreshCalls++
                    b.refreshBodies += req.bodyText()
                    b.gate?.await()
                    val (st, body) = b.refreshAnswer(b.refreshCalls - 1)
                    respond(body, st, JSON_HEADERS)
                }
                "/v1/auth/logout" -> {
                    b.logoutCalls++
                    b.logoutBearers += req.bearer()
                    if (b.logoutThrows) throw RuntimeException("offline")
                    respond("", b.logoutStatus, JSON_HEADERS)
                }
                else -> respond("{}", HttpStatusCode.NotFound, JSON_HEADERS)
            }
        }
        return AuthSessionManager(RemoteAuthDataSource(api), store, backgroundScope, nowMs = { now })
    }

    // --- session creation + persistence ------------------------------------------------

    @Test fun grantBecomesPersistedSession() = runTest {
        val store = InMemorySecureTokenStore(); val m = manager(Backend(), store)
        assertFalse(m.isAuthenticated)
        m.establish("GRANT_g")
        assertTrue(m.isAuthenticated)
        assertEquals("acc1", m.accessToken())
        val saved = store.current!!
        assertEquals("SES_abc.ref1", saved.refreshToken)
        assertEquals(now + 900_000, saved.accessExpiresAtMs) // expiry from accessTokenExpiresIn, not parsed from the token
    }

    // --- cold start -------------------------------------------------------------------------

    @Test fun coldStartRestoresAValidSessionWithoutANetworkCall() = runTest {
        val b = Backend(); val store = InMemorySecureTokenStore(tokens(expiresAtMs = now + 600_000))
        val m = manager(b, store); m.restore()
        assertTrue(m.isAuthenticated); assertEquals("acc1", m.accessToken()); assertEquals(0, b.refreshCalls)
    }

    @Test fun coldStartWithNoStoredSessionIsGuest() = runTest {
        val m = manager(Backend()); m.restore()
        assertFalse(m.isAuthenticated); assertNull(m.accessToken())
    }

    @Test fun coldStartWithExpiredAccessTokenRefreshesSilently() = runTest {
        val b = Backend(); val store = InMemorySecureTokenStore(tokens(expiresAtMs = now - 1))
        val m = manager(b, store); m.restore()
        assertEquals(1, b.refreshCalls)
        assertEquals("acc1", m.accessToken()); assertEquals("SES_abc.ref1", store.current!!.refreshToken)
        assertTrue(m.isAuthenticated)
    }

    @Test fun coldStartOfflineKeepsARecoverableSession() = runTest {
        val b = Backend().apply { refreshAnswer = { HttpStatusCode.ServiceUnavailable to errorJson("SERVICE_UNAVAILABLE") } }
        val store = InMemorySecureTokenStore(tokens(expiresAtMs = now - 1))
        val m = manager(b, store); m.restore()
        assertTrue(m.isAuthenticated, "a transient failure must not destroy a stored session")
        assertEquals(0, store.clears); assertEquals("SES_abc.ref1", store.current!!.refreshToken)
    }

    @Test fun coldStartWithRejectedRefreshTokenClearsTheSession() = runTest {
        val b = Backend().apply { refreshAnswer = { HttpStatusCode.Unauthorized to errorJson("UNAUTHENTICATED") } }
        val store = InMemorySecureTokenStore(tokens(expiresAtMs = now - 1))
        val m = manager(b, store); m.restore()
        assertFalse(m.isAuthenticated); assertNull(store.current)
    }

    // --- proactive refresh + rotation ----------------------------------------------------------

    @Test fun refreshesProactivelyInsideTheMarginAndNotAgainOnceFresh() = runTest {
        val b = Backend(); val m = manager(b, InMemorySecureTokenStore(tokens(expiresAtMs = now + 10_000))) // < 30s margin
        m.restore()
        assertEquals(1, b.refreshCalls)
        now += 1; assertEquals("acc1", m.accessToken()); assertEquals(1, b.refreshCalls)
    }

    @Test fun anExpiringTokenIsRefreshedWhenARequestAsksForIt() = runTest {
        val b = Backend(); val m = manager(b, InMemorySecureTokenStore(tokens(expiresAtMs = now + 600_000))); m.restore()
        assertEquals("acc1", m.accessToken()); assertEquals(0, b.refreshCalls)
        now += 590_000 // inside the margin of the stored expiry
        assertEquals("acc1", m.accessToken()) // refreshed token has the same name in this script
        assertEquals(1, b.refreshCalls)
    }

    @Test fun rotationSendsTheOldTokenAndPersistsTheNewOneBeforeUse() = runTest {
        val b = Backend(); val store = InMemorySecureTokenStore(tokens("old", "SES_abc.oldref", now + 600_000))
        val m = manager(b, store); m.restore()
        assertTrue(m.recover("old"))
        assertTrue(b.refreshBodies.single().contains("SES_abc.oldref"))
        assertEquals("acc1", m.accessToken())
        assertEquals("SES_abc.ref1", store.current!!.refreshToken)
        assertEquals(now + 900_000, store.current!!.accessExpiresAtMs)
        assertEquals("CUS_1", store.current!!.customerId) // refresh does not return it; it must be retained
    }

    @Test fun rotatedTokenStaysUsableEvenIfTheDiskWriteFails() = runTest {
        val store = InMemorySecureTokenStore(tokens("old", "SES_abc.oldref", now + 600_000)).apply { failSaves = true }
        val m = manager(Backend(), store); m.restore()
        assertTrue(m.recover("old")); assertEquals("acc1", m.accessToken())
    }

    @Test fun reusingAnAlreadyRotatedRefreshTokenEndsTheSession() = runTest {
        val b = Backend().apply { refreshAnswer = { HttpStatusCode.Unauthorized to errorJson("UNAUTHENTICATED") } }
        val store = InMemorySecureTokenStore(tokens("old", "SES_abc.stale", now + 600_000))
        val m = manager(b, store); m.restore()
        assertFalse(m.recover("old"))
        assertFalse(m.isAuthenticated); assertNull(store.current)
    }

    // --- failure classification ---------------------------------------------------------------

    @Test fun transientFailuresKeepTheSession() = runTest {
        for (st in listOf(HttpStatusCode.InternalServerError, HttpStatusCode.ServiceUnavailable)) {
            val store = InMemorySecureTokenStore(tokens("old", "SES_abc.r", now + 600_000))
            val m = manager(Backend().apply { refreshAnswer = { st to errorJson("INTERNAL") } }, store); m.restore()
            assertFalse(m.recover("old"))
            assertTrue(m.isAuthenticated, "$st"); assertEquals(0, store.clears)
        }
    }

    @Test fun networkAndMalformedResponsesKeepTheSession() = runTest {
        val store = InMemorySecureTokenStore(tokens("old", "SES_abc.r", now + 600_000))
        val offline = AuthSessionManager(
            RemoteAuthDataSource(apiClient { throw RuntimeException("offline") }), store, backgroundScope, nowMs = { now })
        offline.restore(); assertFalse(offline.recover("old")); assertTrue(offline.isAuthenticated)
        val garbled = AuthSessionManager(
            RemoteAuthDataSource(apiClient { respond("{}", HttpStatusCode.OK, JSON_HEADERS) }), store, backgroundScope, nowMs = { now })
        garbled.restore(); assertFalse(garbled.recover("old")); assertTrue(garbled.isAuthenticated)
    }

    @Test fun anInvalidRefreshTokenShape400IsDefinitive() = runTest {
        val store = InMemorySecureTokenStore(tokens("old", "bad", now + 600_000))
        val m = manager(Backend().apply { refreshAnswer = { HttpStatusCode.BadRequest to errorJson("INVALID_REQUEST") } }, store); m.restore()
        assertFalse(m.recover("old")); assertFalse(m.isAuthenticated); assertNull(store.current)
    }

    // --- single flight ------------------------------------------------------------------------------

    @Test fun concurrentRecoveriesShareOneRefresh() = runTest {
        val b = Backend().apply { gate = CompletableDeferred() }
        val m = manager(b, InMemorySecureTokenStore(tokens("old", "SES_abc.r", now + 600_000))); m.restore()
        val calls = (1..6).map { async { m.recover("old") } }
        runCurrent(); awaitRefreshInFlight(b)
        assertEquals(1, b.refreshCalls, "all six wait on the same in-flight refresh")
        b.gate!!.complete(Unit)
        assertTrue(calls.awaitAll().all { it })
        assertEquals(1, b.refreshCalls)
    }

    @Test fun aLateCallerWithTheStaleTokenRetriesWithoutAnotherRefresh() = runTest {
        val b = Backend(); val m = manager(b, InMemorySecureTokenStore(tokens("old", "SES_abc.r", now + 600_000))); m.restore()
        assertTrue(m.recover("old"))
        assertTrue(m.recover("old")) // its request was rejected before the rotation finished
        assertEquals(1, b.refreshCalls)
    }

    @Test fun aSharedTransientFailureIsOneCallNotNCalls() = runTest {
        val b = Backend().apply { gate = CompletableDeferred(); refreshAnswer = { HttpStatusCode.ServiceUnavailable to errorJson("SERVICE_UNAVAILABLE") } }
        val m = manager(b, InMemorySecureTokenStore(tokens("old", "SES_abc.r", now + 600_000))); m.restore()
        val calls = (1..4).map { async { m.recover("old") } }
        runCurrent(); awaitRefreshInFlight(b); b.gate!!.complete(Unit)
        assertTrue(calls.awaitAll().none { it })
        assertEquals(1, b.refreshCalls)
    }

    @Test fun recoverWithoutASessionDoesNothing() = runTest {
        val b = Backend(); val m = manager(b)
        assertFalse(m.recover("x")); assertEquals(0, b.refreshCalls)
    }

    // --- logout -----------------------------------------------------------------------------------------

    @Test fun logoutRevokesWithTheCurrentAccessTokenThenClears() = runTest {
        val b = Backend(); val store = InMemorySecureTokenStore(tokens("live", "SES_abc.r", now + 600_000))
        val m = manager(b, store); m.restore()
        m.logout()
        assertEquals(listOf<String?>("Bearer live"), b.logoutBearers)
        assertEquals(0, b.refreshCalls)
        assertFalse(m.isAuthenticated); assertNull(store.current)
    }

    @Test fun logoutStillClearsLocallyWhenTheServerFailsOrIsOffline() = runTest {
        for (mode in 0..2) {
            val b = Backend().apply { when (mode) { 0 -> logoutStatus = HttpStatusCode.Unauthorized; 1 -> logoutStatus = HttpStatusCode.ServiceUnavailable; else -> logoutThrows = true } }
            val store = InMemorySecureTokenStore(tokens("live", "SES_abc.r", now + 600_000))
            val m = manager(b, store); m.restore(); m.logout()
            assertFalse(m.isAuthenticated, "mode $mode"); assertNull(store.current)
            assertEquals(1, b.logoutCalls); assertEquals(0, b.refreshCalls, "a failed logout must not loop through refresh")
        }
    }

    @Test fun logoutWithAnExpiredAccessTokenRefreshesOnceThenRevokes() = runTest {
        val b = Backend(); val store = InMemorySecureTokenStore(tokens("old", "SES_abc.r", now + 600_000))
        val m = manager(b, store); m.restore()
        now += 700_000 // access token expired
        m.logout()
        assertEquals(1, b.refreshCalls)
        assertEquals(1, b.logoutCalls); assertEquals("Bearer acc1", b.logoutBearers.single())
        assertFalse(m.isAuthenticated)
    }

    @Test fun logoutWhenAlreadySignedOutIsHarmless() = runTest {
        val b = Backend(); val m = manager(b); m.logout()
        assertEquals(0, b.logoutCalls); assertFalse(m.isAuthenticated)
    }
}
