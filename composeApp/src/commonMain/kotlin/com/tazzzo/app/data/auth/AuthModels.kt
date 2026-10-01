package com.tazzzo.app.data.auth

import com.tazzzo.app.data.remote.ApiError
import com.tazzzo.app.data.remote.ApiException
import kotlinx.serialization.Serializable

/**
 * An Indian mobile number, held only as transient flow state.
 *
 * The backend accepts `+91[6-9]xxxxxxxxx`, a bare `[6-9]xxxxxxxxx` or a
 * leading-zero form, and stores the E.164 form. The app always sends E.164.
 * [toString] is redacted: a phone number must not reach a log or error text.
 */
class PhoneNumber private constructor(val localDigits: String) {
    /** Canonical wire form, e.g. `+919876543210`. */
    val e164: String get() = "+91$localDigits"

    /** Customer-facing form, e.g. `+91 98765 43210`. */
    val display: String get() = "+91 ${localDigits.substring(0, 5)} ${localDigits.substring(5)}"

    override fun toString(): String = "PhoneNumber(***)"
    override fun equals(other: Any?): Boolean = other is PhoneNumber && other.localDigits == localDigits
    override fun hashCode(): Int = localDigits.hashCode()

    companion object {
        const val LOCAL_LENGTH = 10
        private val VALID = Regex("^[6-9][0-9]{9}$")

        /**
         * Keeps digits only and reduces a pasted `+91…` / `91…` / `0…` form to
         * the 10 local digits. Never longer than 10 characters.
         */
        fun sanitize(raw: String): String {
            val digits = raw.filter { it in '0'..'9' }
            val local = when {
                digits.length == 12 && digits.startsWith("91") -> digits.substring(2)
                digits.length == 11 && digits.startsWith("0") -> digits.substring(1)
                else -> digits
            }
            return local.take(LOCAL_LENGTH)
        }

        /** Null unless [raw] is exactly 10 local digits starting 6–9. */
        fun parse(raw: String): PhoneNumber? {
            val local = sanitize(raw)
            return if (VALID.matches(local)) PhoneNumber(local) else null
        }
    }
}

/** What `POST /v1/auth/otp/request` returned. */
data class OtpChallenge(val challengeId: String, val expiresInSeconds: Long, val resendAfterSeconds: Long) {
    override fun toString(): String = "OtpChallenge(expiresIn=$expiresInSeconds, resendAfter=$resendAfterSeconds)"
}

/**
 * Session credentials. Persisted ONLY through [SecureTokenStore].
 * [toString] is redacted so a stray `"$tokens"` cannot leak them.
 */
@Serializable
data class StoredTokens(
    val accessToken: String,
    val refreshToken: String,
    /** Wall-clock epoch millis at which the access token stops being valid. */
    val accessExpiresAtMs: Long,
    val customerId: String? = null
) {
    override fun toString(): String = "StoredTokens(***)"
}

/** Everything the login UI can be told went wrong, as data. */
sealed interface AuthFailure {
    data object InvalidOtp : AuthFailure
    data object ExpiredOtp : AuthFailure
    data class RateLimited(val retryAfterSeconds: Long?) : AuthFailure
    data object InvalidRequest : AuthFailure
    data object SessionRejected : AuthFailure
    data object Network : AuthFailure
    data object Timeout : AuthFailure
    data object Server : AuthFailure
    data object Unknown : AuthFailure

    /** Retrying the same action can help (the credentials were not refused). */
    val isTransient: Boolean get() = this is Network || this is Timeout || this is Server

    /** Customer-facing copy. Never contains codes, tokens, OTPs or numbers. */
    val message: String
        get() = when (this) {
            InvalidOtp -> "That code isn't right. Check it and try again."
            ExpiredOtp -> "That code has expired. Request a new one."
            is RateLimited -> "Too many attempts. Please wait a moment and try again."
            InvalidRequest -> "Please check the details and try again."
            SessionRejected -> "We couldn't sign you in. Please request a new code."
            Network -> "No internet connection. Check it and try again."
            Timeout -> "That took too long. Please try again."
            Server -> "Something went wrong at our end. Please try again."
            Unknown -> "Something went wrong. Please try again."
        }
}

/**
 * Maps a thrown exception to an [AuthFailure] using the backend's stable codes
 * (`OTP_INVALID`, `OTP_EXPIRED`, `OTP_RATE_LIMITED`, `OTP_INVALID_REQUEST`,
 * `INVALID_REQUEST`, `UNAUTHENTICATED`). Message text is never inspected.
 */
fun Throwable.toAuthFailure(): AuthFailure {
    val api = (this as? ApiException)?.error ?: return AuthFailure.Unknown
    return when (api) {
        ApiError.Network -> AuthFailure.Network
        ApiError.Timeout -> AuthFailure.Timeout
        is ApiError.Decoding -> AuthFailure.Unknown
        is ApiError.Http -> when {
            api.code == "OTP_RATE_LIMITED" || api.status == 429 -> AuthFailure.RateLimited(api.retryAfterSeconds)
            api.code == "OTP_EXPIRED" -> AuthFailure.ExpiredOtp
            api.code == "OTP_INVALID" -> AuthFailure.InvalidOtp
            api.code == "OTP_INVALID_REQUEST" || api.code == "INVALID_REQUEST" || api.status == 400 -> AuthFailure.InvalidRequest
            api.status == 401 -> AuthFailure.SessionRejected
            api.status in 500..599 -> AuthFailure.Server
            else -> AuthFailure.Unknown
        }
    }
}
