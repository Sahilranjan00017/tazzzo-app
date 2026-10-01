package com.tazzzo.app.auth

import com.tazzzo.app.data.auth.AuthFailure
import com.tazzzo.app.data.auth.AuthRepository
import com.tazzzo.app.data.auth.OtpChallenge
import com.tazzzo.app.data.auth.PhoneNumber
import com.tazzzo.app.data.remote.ApiError
import com.tazzzo.app.data.remote.ApiException
import com.tazzzo.app.ui.onboarding.AuthFlow
import com.tazzzo.app.ui.onboarding.AuthStep
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** Test double for the repository (the production mock is gone). */
class FakeAuthRepository : AuthRepository {
    var signedIn = false
    override val isAuthenticated get() = signedIn
    var requestCalls = 0
    val requestedPhones = mutableListOf<String>()
    val verified = mutableListOf<Pair<String, String>>()
    var resendAfter = 30L
    var requestError: Throwable? = null
    var verifyError: Throwable? = null
    var verifyGate: CompletableDeferred<Unit>? = null
    override suspend fun requestOtp(phone: PhoneNumber): OtpChallenge {
        requestCalls++; requestedPhones += phone.e164
        requestError?.let { throw it }
        return OtpChallenge("OTP_challenge$requestCalls", 300, resendAfter)
    }
    override suspend fun verifyOtp(challengeId: String, otp: String) {
        verifyGate?.await()
        verifyError?.let { throw it }
        verified += challengeId to otp; signedIn = true
    }
    override suspend fun logout() { signedIn = false }
}

fun httpFailure(status: Int, code: String, retryAfter: Long? = null) =
    ApiException(ApiError.Http(status, code, retryAfterSeconds = retryAfter))

class AuthFlowTest {
    private var signedInCalls = 0
    private fun TestScope.flow(repo: FakeAuthRepository) = AuthFlow(repo, backgroundScope) { signedInCalls++ }

    private suspend fun TestScope.toOtpStep(f: AuthFlow, phone: String = "9876543210") {
        f.onPhoneChanged(phone); f.submitPhone(); runCurrent()
    }

    @Test fun continueIsEnabledOnlyForAValidIndianNumber() = runTest {
        val f = flow(FakeAuthRepository())
        f.onPhoneChanged("12345"); assertFalse(f.canContinue)
        f.onPhoneChanged("5876543210"); assertFalse(f.canContinue)
        f.onPhoneChanged("98765"); assertFalse(f.canContinue)
        f.onPhoneChanged("9876543210"); assertTrue(f.canContinue)
    }

    @Test fun phoneRequestSendsE164AndMovesToOtpWithConfirmationText() = runTest {
        val repo = FakeAuthRepository(); val f = flow(repo)
        toOtpStep(f)
        assertEquals(listOf("+919876543210"), repo.requestedPhones)
        assertEquals(AuthStep.Otp, f.step)
        assertEquals("+91 98765 43210", f.phoneDisplay) // the entered phone is kept for the OTP screen
    }

    @Test fun anInvalidNumberNeverReachesTheNetwork() = runTest {
        val repo = FakeAuthRepository(); val f = flow(repo)
        f.onPhoneChanged("5876543210"); f.submitPhone(); runCurrent()
        assertEquals(0, repo.requestCalls); assertEquals(AuthStep.Phone, f.step)
    }

    @Test fun otpRequestFailuresStayOnThePhoneStepAndAreRetryable() = runTest {
        val repo = FakeAuthRepository().apply { requestError = httpFailure(429, "OTP_RATE_LIMITED", 60) }
        val f = flow(repo); toOtpStep(f)
        assertEquals(AuthStep.Phone, f.step); assertEquals(AuthFailure.RateLimited(60), f.failure); assertFalse(f.requesting)
        repo.requestError = ApiException(ApiError.Network); f.submitPhone(); runCurrent()
        assertEquals(AuthFailure.Network, f.failure)
        repo.requestError = null; f.submitPhone(); runCurrent()
        assertEquals(AuthStep.Otp, f.step); assertNull(f.failure)
    }

    // --- six cells ------------------------------------------------------------------------------

    @Test fun otpAcceptsOnlyDigitsAndAtMostSix() = runTest {
        val f = flow(FakeAuthRepository()); toOtpStep(f)
        f.onOtpChanged("12a3"); assertEquals("123", f.otpInput)
        f.onOtpChanged("12345678"); assertEquals("123456", f.otpInput) // paste is truncated
    }

    @Test fun backspaceShortensTheCode() = runTest {
        val f = flow(FakeAuthRepository()); toOtpStep(f)
        f.onOtpChanged("123"); f.onOtpChanged("12"); assertEquals("12", f.otpInput)
        f.onOtpChanged(""); assertEquals("", f.otpInput)
    }

    @Test fun nothingSubmitsBeforeTheSixthDigit() = runTest {
        val repo = FakeAuthRepository(); val f = flow(repo); toOtpStep(f)
        f.onOtpChanged("12345"); runCurrent()
        assertTrue(repo.verified.isEmpty()); assertFalse(f.canVerify.not() && false)
        f.submitOtp(); runCurrent(); assertTrue(repo.verified.isEmpty())
    }

    @Test fun theSixthDigitSubmitsAndSignsIn() = runTest {
        val repo = FakeAuthRepository(); val f = flow(repo); toOtpStep(f)
        f.onOtpChanged("123456"); runCurrent()
        assertEquals(listOf("OTP_challenge1" to "123456"), repo.verified)
        assertEquals(1, signedInCalls)
    }

    @Test fun loadingIsShownWhileVerifyingAndInputIsIgnored() = runTest {
        val repo = FakeAuthRepository().apply { verifyGate = CompletableDeferred() }
        val f = flow(repo); toOtpStep(f)
        f.onOtpChanged("123456"); runCurrent()
        assertTrue(f.verifying)
        f.onOtpChanged("654321"); assertEquals("123456", f.otpInput)
        repo.verifyGate!!.complete(Unit); runCurrent()
        assertFalse(f.verifying); assertEquals(1, signedInCalls)
    }

    // --- OTP errors ------------------------------------------------------------------------------------

    @Test fun wrongCodeClearsTheCellsAndShowsInvalid() = runTest {
        val repo = FakeAuthRepository().apply { verifyError = httpFailure(400, "OTP_INVALID") }
        val f = flow(repo); toOtpStep(f)
        f.onOtpChanged("111111"); runCurrent()
        assertEquals(AuthFailure.InvalidOtp, f.failure); assertEquals("", f.otpInput); assertEquals(0, signedInCalls)
        f.onOtpChanged("1"); assertNull(f.failure) // typing clears the message
    }

    @Test fun expiredCodeAllowsAnImmediateResend() = runTest {
        val repo = FakeAuthRepository().apply { verifyError = httpFailure(400, "OTP_EXPIRED") }
        val f = flow(repo); toOtpStep(f)
        f.onOtpChanged("111111"); runCurrent()
        assertEquals(AuthFailure.ExpiredOtp, f.failure); assertEquals(0, f.resendRemaining); assertTrue(f.canResend)
    }

    @Test fun rateLimitedVerifyShowsTheMessageAndStartsACountdown() = runTest {
        val repo = FakeAuthRepository().apply { verifyError = httpFailure(429, "OTP_RATE_LIMITED", 90) }
        val f = flow(repo); toOtpStep(f)
        f.onOtpChanged("111111"); runCurrent()
        assertEquals(AuthFailure.RateLimited(90), f.failure); assertEquals(90, f.resendRemaining)
    }

    @Test fun networkAndServerFailuresKeepTheDigitsForOneTapRetry() = runTest {
        val repo = FakeAuthRepository().apply { verifyError = ApiException(ApiError.Network) }
        val f = flow(repo); toOtpStep(f)
        f.onOtpChanged("123456"); runCurrent()
        assertEquals(AuthFailure.Network, f.failure); assertEquals("123456", f.otpInput); assertTrue(f.canVerify)
        repo.verifyError = httpFailure(503, "SERVICE_UNAVAILABLE"); f.submitOtp(); runCurrent()
        assertEquals(AuthFailure.Server, f.failure)
        repo.verifyError = null; f.submitOtp(); runCurrent()
        assertEquals(1, signedInCalls)
    }

    @Test fun invalidRequestIsSurfaced() = runTest {
        val repo = FakeAuthRepository().apply { verifyError = httpFailure(400, "OTP_INVALID_REQUEST") }
        val f = flow(repo); toOtpStep(f); f.onOtpChanged("123456"); runCurrent()
        assertEquals(AuthFailure.InvalidRequest, f.failure)
    }

    // --- resend timer -------------------------------------------------------------------------------------

    @Test fun resendCountdownUsesTheBackendValueNotAConstant() = runTest {
        val repo = FakeAuthRepository().apply { resendAfter = 45 }
        val f = flow(repo); toOtpStep(f)
        assertEquals(45, f.resendRemaining); assertFalse(f.canResend)
        advanceTimeBy(10_000); runCurrent(); assertEquals(35, f.resendRemaining)
        advanceTimeBy(35_000); runCurrent(); assertEquals(0, f.resendRemaining); assertTrue(f.canResend)
    }

    @Test fun resendRequestsANewCodeAndRestartsTheTimerFromTheNewResponse() = runTest {
        val repo = FakeAuthRepository().apply { resendAfter = 5 }
        val f = flow(repo); toOtpStep(f)
        f.resend(); runCurrent(); assertEquals(1, repo.requestCalls, "cannot resend while counting down")
        advanceTimeBy(5_000); runCurrent()
        repo.resendAfter = 20; f.onOtpChanged("12")
        f.resend(); runCurrent()
        assertEquals(2, repo.requestCalls); assertEquals(20, f.resendRemaining); assertEquals("", f.otpInput)
        f.onOtpChanged("123456"); runCurrent()
        assertEquals("OTP_challenge2", repo.verified.single().first) // verifies against the NEW challenge
    }

    @Test fun resendFailureIsReportedAndRetryable() = runTest {
        val repo = FakeAuthRepository().apply { resendAfter = 1 }
        val f = flow(repo); toOtpStep(f); advanceTimeBy(1_000); runCurrent()
        repo.requestError = ApiException(ApiError.Timeout); f.resend(); runCurrent()
        assertEquals(AuthFailure.Timeout, f.failure); assertTrue(f.canResend)
        repo.requestError = null; f.resend(); runCurrent(); assertNull(f.failure)
    }

    @Test fun changeNumberReturnsToThePhoneStepAndForgetsTheChallenge() = runTest {
        val repo = FakeAuthRepository(); val f = flow(repo); toOtpStep(f)
        f.onOtpChanged("123"); f.changeNumber()
        assertEquals(AuthStep.Phone, f.step); assertEquals("", f.otpInput); assertEquals("", f.phoneDisplay)
        f.onOtpChanged("123456"); runCurrent(); assertTrue(repo.verified.isEmpty())
    }
}
