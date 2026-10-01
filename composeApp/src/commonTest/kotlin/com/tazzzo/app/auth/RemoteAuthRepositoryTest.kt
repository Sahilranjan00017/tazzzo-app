package com.tazzzo.app.auth

import com.tazzzo.app.data.auth.AuthSessionManager
import com.tazzzo.app.data.auth.PhoneNumber
import com.tazzzo.app.data.auth.RemoteAuthDataSource
import com.tazzzo.app.data.auth.RemoteAuthRepository
import com.tazzzo.app.data.remote.ApiException
import io.ktor.client.engine.mock.respond
import io.ktor.http.HttpStatusCode
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class RemoteAuthRepositoryTest {
    private class Wire {
        var verifyCalls = 0
        var sessionCalls = 0
        var sessionStatus = HttpStatusCode.OK
        val paths = mutableListOf<String>()
    }

    private fun TestScope.repo(w: Wire, store: InMemorySecureTokenStore = InMemorySecureTokenStore()): RemoteAuthRepository {
        val remote = RemoteAuthDataSource(apiClient { req ->
            w.paths += req.url.encodedPath
            when (req.url.encodedPath) {
                "/v1/auth/otp/request" -> respond("""{"challengeId":"OTP_c","expiresInSeconds":300,"resendAfterSeconds":30}""", HttpStatusCode.Accepted, JSON_HEADERS)
                "/v1/auth/otp/verify" -> { w.verifyCalls++; respond("""{"challengeId":"OTP_c","verified":true,"grantId":"GRANT_g"}""", HttpStatusCode.OK, JSON_HEADERS) }
                "/v1/auth/session" -> {
                    w.sessionCalls++
                    if (w.sessionStatus == HttpStatusCode.OK) respond(sessionJson(), HttpStatusCode.OK, JSON_HEADERS)
                    else respond(errorJson("UNAUTHENTICATED"), w.sessionStatus, JSON_HEADERS)
                }
                else -> respond("", HttpStatusCode.NoContent)
            }
        })
        return RemoteAuthRepository(remote, AuthSessionManager(remote, store, backgroundScope, nowMs = { 1_000_000L }))
    }

    @Test fun requestOtpSendsTheE164Number() = runTest {
        val c = repo(Wire()).requestOtp(PhoneNumber.parse("9876543210")!!)
        assertEquals("OTP_c", c.challengeId); assertEquals(30, c.resendAfterSeconds)
    }

    @Test fun verifyingAloneIsNotSignedInButVerifyPlusSessionIs() = runTest {
        val w = Wire(); val store = InMemorySecureTokenStore(); val r = repo(w, store)
        assertFalse(r.isAuthenticated)
        r.verifyOtp("OTP_c", "123456")
        assertEquals(listOf("/v1/auth/otp/verify", "/v1/auth/session"), w.paths) // grant → session, in that order
        assertTrue(r.isAuthenticated)
        assertEquals("SES_abc.ref1", store.current!!.refreshToken) // tokens persisted securely
    }

    @Test fun onlySixDigitCodesAreEverSent() = runTest {
        val w = Wire(); val r = repo(w)
        for (bad in listOf("1234", "12345", "1234567", "12345a", "")) assertFailsWith<IllegalArgumentException>(bad) { r.verifyOtp("OTP_c", bad) }
        assertTrue(w.paths.isEmpty())
    }

    @Test fun aTransientSessionFailureRetriesTheSameGrantWithoutReverifying() = runTest {
        val w = Wire().apply { sessionStatus = HttpStatusCode.ServiceUnavailable }; val r = repo(w)
        assertFailsWith<ApiException> { r.verifyOtp("OTP_c", "123456") }
        w.sessionStatus = HttpStatusCode.OK
        r.verifyOtp("OTP_c", "123456")
        assertEquals(1, w.verifyCalls, "the one-time code was already consumed")
        assertEquals(2, w.sessionCalls); assertTrue(r.isAuthenticated)
    }

    @Test fun aRejectedGrantIsDiscardedSoTheNextAttemptReverifies() = runTest {
        val w = Wire().apply { sessionStatus = HttpStatusCode.Unauthorized }; val r = repo(w)
        assertFailsWith<ApiException> { r.verifyOtp("OTP_c", "123456") }
        w.sessionStatus = HttpStatusCode.OK; r.verifyOtp("OTP_c", "123456")
        assertEquals(2, w.verifyCalls)
    }

    @Test fun logoutClearsTheSession() = runTest {
        val store = InMemorySecureTokenStore(); val r = repo(Wire(), store)
        r.verifyOtp("OTP_c", "123456"); r.logout()
        assertFalse(r.isAuthenticated); assertEquals(null, store.current)
    }
}
