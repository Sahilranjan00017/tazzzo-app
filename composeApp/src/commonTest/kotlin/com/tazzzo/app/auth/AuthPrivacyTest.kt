package com.tazzzo.app.auth

import com.tazzzo.app.data.auth.OtpChallenge
import com.tazzzo.app.data.auth.PhoneNumber
import com.tazzzo.app.data.auth.RemoteAuthDataSource
import com.tazzzo.app.data.auth.StoredTokens
import com.tazzzo.app.data.auth.toAuthFailure
import com.tazzzo.app.data.remote.ApiException
import io.ktor.client.engine.mock.respond
import io.ktor.http.HttpStatusCode
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse

/** Secrets must not survive into anything that can be logged, thrown or rendered. */
class AuthPrivacyTest {
    private val phone = "9876543210"
    private val otp = "482913"
    private val access = "ACCESS-TOKEN-VALUE"
    private val refresh = "SES_abc.REFRESH-SECRET-VALUE"

    private fun assertClean(text: String) {
        for (secret in listOf(phone, otp, access, refresh, "REFRESH-SECRET")) assertFalse(text.contains(secret), "leaked into: $text")
    }

    @Test fun valueObjectsAreRedactedInToString() {
        assertClean(StoredTokens(access, refresh, 1L, "CUS_1").toString())
        assertClean(PhoneNumber.parse(phone)!!.toString())
        assertClean(OtpChallenge("OTP_secretchallenge", 300, 30).toString())
        assertFalse(OtpChallenge("OTP_secretchallenge", 300, 30).toString().contains("secretchallenge"))
    }

    @Test fun failedAuthCallsProduceExceptionsWithoutTheRequestPayload() = runTest {
        val remote = RemoteAuthDataSource(apiClient { respond(errorJson("OTP_INVALID"), HttpStatusCode.BadRequest, JSON_HEADERS) })
        val e1 = assertFailsWith<ApiException> { remote.verifyOtp("OTP_c", otp) }
        val e2 = assertFailsWith<ApiException> { remote.requestOtp("+91$phone") }
        val e3 = assertFailsWith<ApiException> { remote.refresh(refresh) }
        for (e in listOf(e1, e2, e3)) {
            assertClean(e.message.orEmpty()); assertClean(e.toString()); assertClean(e.toAuthFailure().message)
            assertFalse(e.message.orEmpty().contains("internal detail")) // backend free text is not surfaced either
        }
    }

    @Test fun transportFailureTextDoesNotCarryThePayload() = runTest {
        val remote = RemoteAuthDataSource(apiClient { throw RuntimeException("connect failed for $phone $otp") })
        val e = assertFailsWith<ApiException> { remote.verifyOtp("OTP_c", otp) }
        assertClean(e.message.orEmpty()); assertClean(e.toAuthFailure().message)
    }

    @Test fun decodedResponsesDoNotPrintTheirTokens() = runTest {
        val remote = RemoteAuthDataSource(apiClient { respond(sessionJson(access, refresh), HttpStatusCode.OK, JSON_HEADERS) })
        assertClean(remote.createSession("GRANT_g").toString())
    }
}
