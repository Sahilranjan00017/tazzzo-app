package com.tazzzo.app.auth

import com.tazzzo.app.data.auth.AuthFailure
import com.tazzzo.app.data.auth.RemoteAuthDataSource
import com.tazzzo.app.data.auth.toAuthFailure
import com.tazzzo.app.data.remote.ApiException
import io.ktor.client.engine.mock.respond
import io.ktor.http.HttpMethod
import io.ktor.http.HttpStatusCode
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue

class AuthApiTest {

    private suspend fun failure(status: HttpStatusCode, code: String, retryAfter: Long? = null, call: suspend RemoteAuthDataSource.() -> Unit): AuthFailure {
        val remote = RemoteAuthDataSource(apiClient { respond(errorJson(code, retryAfter), status, JSON_HEADERS) })
        return assertFailsWith<ApiException> { remote.call() }.toAuthFailure()
    }

    // --- OTP request ------------------------------------------------------------

    @Test fun otpRequestSendsE164AndReadsChallenge() = runTest {
        var path = ""; var method: HttpMethod? = null; var body = ""; var auth: String? = "unset"
        val remote = RemoteAuthDataSource(apiClient {
            path = it.url.encodedPath; method = it.method; body = it.bodyText(); auth = it.bearer()
            respond("""{"challengeId":"OTP_abcdefghijklmnopqrstuvwxyz1","expiresInSeconds":300,"resendAfterSeconds":45,"requestId":"r"}""", HttpStatusCode.Accepted, JSON_HEADERS)
        })
        val c = remote.requestOtp("+919876543210")
        assertEquals("/v1/auth/otp/request", path)
        assertEquals(HttpMethod.Post, method)
        assertEquals("""{"phone":"+919876543210"}""", body)
        assertNull(auth, "auth endpoints send no Authorization header")
        assertEquals("OTP_abcdefghijklmnopqrstuvwxyz1", c.challengeId)
        assertEquals(300, c.expiresInSeconds)
        assertEquals(45, c.resendAfterSeconds) // taken from the response, not hard-coded
    }

    @Test fun otpRequestRateLimitedCarriesRetryAfter() = runTest {
        val f = failure(HttpStatusCode.TooManyRequests, "OTP_RATE_LIMITED", 120) { requestOtp("+919876543210") }
        assertEquals(AuthFailure.RateLimited(120), f)
    }

    @Test fun otpRequestInvalidRequestAndServerErrors() = runTest {
        assertEquals(AuthFailure.InvalidRequest, failure(HttpStatusCode.BadRequest, "OTP_INVALID_REQUEST") { requestOtp("x") })
        assertEquals(AuthFailure.Server, failure(HttpStatusCode.ServiceUnavailable, "SERVICE_UNAVAILABLE") { requestOtp("+919876543210") })
    }

    // --- OTP verify ---------------------------------------------------------------

    @Test fun otpVerifySendsSixDigitStringAndReturnsGrant() = runTest {
        var body = ""; var path = ""
        val remote = RemoteAuthDataSource(apiClient {
            path = it.url.encodedPath; body = it.bodyText()
            respond("""{"challengeId":"OTP_x","verified":true,"grantId":"GRANT_abcdefghijklmnopqrstuvwxyz1","requestId":"r"}""", HttpStatusCode.OK, JSON_HEADERS)
        })
        assertEquals("GRANT_abcdefghijklmnopqrstuvwxyz1", remote.verifyOtp("OTP_x", "012345"))
        assertEquals("/v1/auth/otp/verify", path)
        assertEquals("""{"challengeId":"OTP_x","otp":"012345"}""", body) // a JSON string: leading zero survives
    }

    @Test fun wrongOtpIs400NotAnAuthSessionError() = runTest {
        assertEquals(AuthFailure.InvalidOtp, failure(HttpStatusCode.BadRequest, "OTP_INVALID") { verifyOtp("OTP_x", "111111") })
    }

    @Test fun expiredOtp() = runTest {
        assertEquals(AuthFailure.ExpiredOtp, failure(HttpStatusCode.BadRequest, "OTP_EXPIRED") { verifyOtp("OTP_x", "111111") })
    }

    @Test fun verifyRateLimited() = runTest {
        assertEquals(AuthFailure.RateLimited(30), failure(HttpStatusCode.TooManyRequests, "OTP_RATE_LIMITED", 30) { verifyOtp("OTP_x", "111111") })
    }

    @Test fun malformedVerifyBodyIsADecodingErrorNotASuccess() = runTest {
        val remote = RemoteAuthDataSource(apiClient { respond("""{"verified":true}""", HttpStatusCode.OK, JSON_HEADERS) })
        assertEquals(AuthFailure.Unknown, assertFailsWith<ApiException> { remote.verifyOtp("OTP_x", "111111") }.toAuthFailure())
    }

    // --- session ------------------------------------------------------------------

    @Test fun sessionRequestAndResponseShape() = runTest {
        var body = ""; var path = ""
        val remote = RemoteAuthDataSource(apiClient {
            path = it.url.encodedPath; body = it.bodyText()
            respond(sessionJson(), HttpStatusCode.OK, JSON_HEADERS)
        })
        val s = remote.createSession("GRANT_g")
        assertEquals("/v1/auth/session", path)
        assertEquals("""{"grantId":"GRANT_g"}""", body)
        assertEquals("CUS_1", s.customerId); assertEquals(900, s.accessTokenExpiresIn)
    }

    @Test fun spentGrantIs401() = runTest {
        assertEquals(AuthFailure.SessionRejected, failure(HttpStatusCode.Unauthorized, "UNAUTHENTICATED") { createSession("GRANT_g") })
    }

    // --- failure mapping ----------------------------------------------------------

    @Test fun transportFailuresAreTransient() = runTest {
        val f = RemoteAuthDataSource(apiClient { throw RuntimeException("offline") })
        val failure = assertFailsWith<ApiException> { f.requestOtp("+919876543210") }.toAuthFailure()
        assertEquals(AuthFailure.Network, failure)
        assertTrue(failure.isTransient)
        assertTrue(AuthFailure.Timeout.isTransient && AuthFailure.Server.isTransient)
        assertTrue(!AuthFailure.InvalidOtp.isTransient)
    }

    @Test fun customerCopyNeverContainsCodesOrDetail() {
        val all = listOf(AuthFailure.InvalidOtp, AuthFailure.ExpiredOtp, AuthFailure.RateLimited(5), AuthFailure.InvalidRequest,
            AuthFailure.SessionRejected, AuthFailure.Network, AuthFailure.Timeout, AuthFailure.Server, AuthFailure.Unknown)
        for (f in all) {
            assertTrue(f.message.isNotBlank())
            assertTrue(!f.message.contains("OTP_") && !f.message.contains("401") && !f.message.contains("internal detail"))
        }
        assertIs<AuthFailure>(AuthFailure.Unknown)
    }
}
