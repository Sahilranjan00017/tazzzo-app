package com.tazzzo.app.auth

import com.tazzzo.app.data.auth.AuthSessionManager
import com.tazzzo.app.data.auth.RemoteAuthDataSource
import com.tazzzo.app.data.remote.ApiClient
import com.tazzzo.app.data.remote.ApiException
import com.tazzzo.app.data.remote.ApiRequest
import com.tazzzo.app.data.remote.AuthRecovery
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.http.HttpMethod
import io.ktor.http.HttpStatusCode
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** The 401 → recover → retry-once contract of [ApiClient], end to end with the real session manager. */
class ApiClientRecoveryTest {
    private val now = 1_000_000L
    private val cart = ApiRequest(HttpMethod.Get, "/v1/customer/cart")

    private class Wire {
        var refreshCalls = 0
        val cartBearers = mutableListOf<String?>()
        var cartAlways401 = false
    }

    private fun kotlinx.coroutines.test.TestScope.stack(w: Wire, store: InMemorySecureTokenStore): Pair<ApiClient, AuthSessionManager> {
        val authApi = apiClient {
            w.refreshCalls++
            respond(refreshJson("fresh", "SES_abc.refNew"), HttpStatusCode.OK, JSON_HEADERS)
        }
        val m = AuthSessionManager(RemoteAuthDataSource(authApi), store, backgroundScope, nowMs = { now })
        val gateway = ApiClient(baseUrl = BASE, tokenProvider = m, recovery = m, engine = MockEngine { req ->
            if (req.url.encodedPath == "/v1/customer/cart") {
                w.cartBearers += req.bearer()
                if (w.cartAlways401 || req.bearer() != "Bearer fresh") respond(errorJson("UNAUTHENTICATED"), HttpStatusCode.Unauthorized, JSON_HEADERS)
                else respond("{}", HttpStatusCode.OK, JSON_HEADERS)
            } else respond("", HttpStatusCode.NoContent)
        })
        return gateway to m
    }

    @Test fun a401RefreshesAndRetriesTheRequestExactlyOnce() = runTest {
        val w = Wire(); val (api, m) = stack(w, InMemorySecureTokenStore(tokens("stale", "SES_abc.r", now + 600_000))); m.restore()
        assertEquals(200, api.executeUnit(cart).status)
        assertEquals(listOf<String?>("Bearer stale", "Bearer fresh"), w.cartBearers)
        assertEquals(1, w.refreshCalls)
    }

    @Test fun aSecond401EndsRecoveryWithNoLoop() = runTest {
        val w = Wire().apply { cartAlways401 = true }
        val (api, m) = stack(w, InMemorySecureTokenStore(tokens("stale", "SES_abc.r", now + 600_000))); m.restore()
        val e = assertFailsWith<ApiException> { api.executeUnit(cart) }
        assertEquals(2, w.cartBearers.size, "original + exactly one retry")
        assertEquals(1, w.refreshCalls)
        assertTrue(e.message!!.contains("401"))
    }

    @Test fun concurrentRequestsShareOneRefreshAndEachRetriesOnce() = runTest {
        val w = Wire(); val (api, m) = stack(w, InMemorySecureTokenStore(tokens("stale", "SES_abc.r", now + 600_000))); m.restore()
        val results = (1..5).map { async { api.executeUnit(cart).status } }.awaitAll()
        assertTrue(results.all { it == 200 })
        assertEquals(1, w.refreshCalls)
        assertEquals(5, w.cartBearers.count { it == "Bearer fresh" })
        assertEquals(5, w.cartBearers.count { it == "Bearer stale" })
    }

    @Test fun guestRequestsAreNeverRecovered() = runTest {
        val w = Wire(); val (api, _) = stack(w, InMemorySecureTokenStore()) // no session
        assertFailsWith<ApiException> { api.executeUnit(cart) }
        assertEquals(listOf<String?>(null), w.cartBearers); assertEquals(0, w.refreshCalls)
    }

    @Test fun unauthenticatedAndOptedOutRequestsAreNotRetried() = runTest {
        var asked = 0
        val api = ApiClient(baseUrl = BASE, tokenProvider = { "t" }, recovery = AuthRecovery { asked++; true },
            engine = MockEngine { respond(errorJson("UNAUTHENTICATED"), HttpStatusCode.Unauthorized, JSON_HEADERS) })
        assertFailsWith<ApiException> { api.executeUnit(ApiRequest(HttpMethod.Post, "/v1/auth/session", authenticated = false)) }
        assertFailsWith<ApiException> { api.executeUnit(cart.copy(recoverOn401 = false)) }
        assertEquals(0, asked)
    }

    @Test fun anExplicitBearerCallLikeLogoutNeverTriggersRecovery() = runTest {
        var asked = 0; var calls = 0
        val api = ApiClient(baseUrl = BASE, recovery = AuthRecovery { asked++; true },
            engine = MockEngine { calls++; respond(errorJson("UNAUTHENTICATED"), HttpStatusCode.Unauthorized, JSON_HEADERS) })
        assertFailsWith<ApiException> { api.executeUnit(ApiRequest(HttpMethod.Post, "/v1/auth/logout", authenticated = false, bearerToken = "acc")) }
        assertEquals(0, asked); assertEquals(1, calls)
    }

    @Test fun nonAuthFailuresAreNeverRetried() = runTest {
        var asked = 0; var calls = 0
        val api = ApiClient(baseUrl = BASE, tokenProvider = { "t" }, recovery = AuthRecovery { asked++; true },
            engine = MockEngine { calls++; respond(errorJson("INTERNAL"), HttpStatusCode.InternalServerError, JSON_HEADERS) })
        assertFailsWith<ApiException> { api.executeUnit(cart) }
        assertEquals(0, asked); assertEquals(1, calls)
    }

    @Test fun whenRecoveryFailsTheOriginal401SurfacesWithoutARetry() = runTest {
        var calls = 0
        val api = ApiClient(baseUrl = BASE, tokenProvider = { "t" }, recovery = AuthRecovery { false },
            engine = MockEngine { calls++; respond(errorJson("UNAUTHENTICATED"), HttpStatusCode.Unauthorized, JSON_HEADERS) })
        assertFailsWith<ApiException> { api.executeUnit(cart) }
        assertEquals(1, calls)
        assertFalse(false)
    }
}
