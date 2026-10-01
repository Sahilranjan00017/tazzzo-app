package com.tazzzo.app.ui.onboarding

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.tazzzo.app.data.auth.AuthFailure
import com.tazzzo.app.data.auth.AuthRepository
import com.tazzzo.app.data.auth.PhoneNumber
import com.tazzzo.app.data.auth.toAuthFailure
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

enum class AuthStep { Phone, Otp }

/**
 * The ONE phone → OTP flow. Onboarding and the Login route both render it, so
 * there is no second path and no mock left to bypass the real backend.
 *
 * Plain class over Compose snapshot state (no ViewModel framework): the screens
 * own an instance through `remember`, tests construct one with a fake
 * [AuthRepository] and a test scope.
 *
 * Privacy: the phone is held only while the OTP step needs it; the OTP is never
 * stored beyond the cell input and is cleared on failure; nothing is logged.
 */
class AuthFlow(
    private val repo: AuthRepository,
    private val scope: CoroutineScope,
    private val onSignedIn: () -> Unit
) {
    var step by mutableStateOf(AuthStep.Phone); private set
    var phoneInput by mutableStateOf(""); private set
    var otpInput by mutableStateOf(""); private set
    var requesting by mutableStateOf(false); private set
    var verifying by mutableStateOf(false); private set
    var failure by mutableStateOf<AuthFailure?>(null); private set

    /** Seconds until "resend" is allowed. Driven by the backend's `resendAfterSeconds`. */
    var resendRemaining by mutableIntStateOf(0); private set

    private var phone: PhoneNumber? = null
    private var challengeId: String? = null
    private var timer: Job? = null

    val canContinue: Boolean get() = PhoneNumber.parse(phoneInput) != null
    val canResend: Boolean get() = step == AuthStep.Otp && resendRemaining == 0 && !requesting && !verifying
    val canVerify: Boolean get() = step == AuthStep.Otp && otpInput.length == OTP_LENGTH && !verifying

    /** `+91 98765 43210` for the OTP screen's confirmation line. */
    val phoneDisplay: String get() = phone?.display.orEmpty()

    fun onPhoneChanged(raw: String) {
        phoneInput = PhoneNumber.sanitize(raw)
        failure = null
    }

    fun submitPhone() {
        val parsed = PhoneNumber.parse(phoneInput) ?: run { failure = AuthFailure.InvalidRequest; return }
        if (requesting) return
        scope.launch { request(parsed, enterOtpStep = true) }
    }

    /** Typing or pasting. Digits only, max 6; the sixth digit submits. */
    fun onOtpChanged(raw: String) {
        if (verifying) return
        otpInput = raw.filter { it in '0'..'9' }.take(OTP_LENGTH)
        failure = null
        if (otpInput.length == OTP_LENGTH) submitOtp()
    }

    fun submitOtp() {
        val id = challengeId ?: return
        if (!canVerify) return
        val code = otpInput
        scope.launch {
            verifying = true
            failure = null
            try {
                repo.verifyOtp(id, code)
                onSignedIn()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Throwable) {
                val f = e.toAuthFailure()
                failure = f
                when (f) {
                    // A wrong/expired code is spent; a transient failure keeps the digits for one-tap retry.
                    AuthFailure.InvalidOtp, AuthFailure.ExpiredOtp, AuthFailure.SessionRejected -> otpInput = ""
                    is AuthFailure.RateLimited -> {
                        otpInput = ""
                        f.retryAfterSeconds?.let { startTimer(it) }
                    }
                    else -> Unit
                }
                if (f == AuthFailure.ExpiredOtp) stopTimer() // allow an immediate new code
            } finally {
                verifying = false
            }
        }
    }

    fun resend() {
        val p = phone ?: return
        if (!canResend) return
        scope.launch { request(p, enterOtpStep = false) }
    }

    fun changeNumber() {
        stopTimer()
        step = AuthStep.Phone
        otpInput = ""
        challengeId = null
        phone = null
        failure = null
    }

    private suspend fun request(p: PhoneNumber, enterOtpStep: Boolean) {
        requesting = true
        failure = null
        try {
            val c = repo.requestOtp(p)
            phone = p
            challengeId = c.challengeId
            otpInput = ""
            if (enterOtpStep) step = AuthStep.Otp
            startTimer(c.resendAfterSeconds)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Throwable) {
            val f = e.toAuthFailure()
            failure = f
            if (f is AuthFailure.RateLimited && !enterOtpStep) f.retryAfterSeconds?.let { startTimer(it) }
        } finally {
            requesting = false
        }
    }

    private fun startTimer(seconds: Long) {
        timer?.cancel()
        resendRemaining = seconds.coerceIn(0, MAX_RESEND_SECONDS).toInt()
        if (resendRemaining == 0) return
        timer = scope.launch {
            while (resendRemaining > 0) {
                delay(1_000)
                resendRemaining -= 1
            }
        }
    }

    private fun stopTimer() {
        timer?.cancel()
        resendRemaining = 0
    }

    companion object {
        const val OTP_LENGTH = 6
        private const val MAX_RESEND_SECONDS = 3_600L
    }
}
