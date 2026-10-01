package com.tazzzo.app.data.auth

/** Customer authentication, as the UI sees it. */
interface AuthRepository {
    /** True while a secure session exists. */
    val isAuthenticated: Boolean

    /** Asks the backend to send a 6-digit code. */
    suspend fun requestOtp(phone: PhoneNumber): OtpChallenge

    /**
     * Verifies the 6-digit [otp] AND creates the session (grant → tokens).
     * Verification alone is not being signed in. Throws on any failure.
     */
    suspend fun verifyOtp(challengeId: String, otp: String)

    /** Revokes server-side if possible; ALWAYS clears local credentials. */
    suspend fun logout()
}

/**
 * The real implementation. The only state kept is a verified-but-not-yet-
 * exchanged grant: if the session call fails transiently AFTER a successful
 * verify, a retry for the same challenge reuses that one-time grant instead of
 * re-verifying a code the backend has already consumed.
 */
class RemoteAuthRepository(
    private val remote: RemoteAuthDataSource,
    private val session: AuthSessionManager
) : AuthRepository {

    private var pendingChallengeId: String? = null
    private var pendingGrantId: String? = null

    override val isAuthenticated: Boolean get() = session.isAuthenticated

    override suspend fun requestOtp(phone: PhoneNumber): OtpChallenge = remote.requestOtp(phone.e164)

    override suspend fun verifyOtp(challengeId: String, otp: String) {
        require(OTP_FORMAT.matches(otp)) { "OTP must be 6 digits" }
        val grant = if (pendingChallengeId == challengeId && pendingGrantId != null) {
            pendingGrantId!!
        } else {
            remote.verifyOtp(challengeId, otp).also {
                pendingChallengeId = challengeId
                pendingGrantId = it
            }
        }
        try {
            session.establish(grant)
            clearPending()
        } catch (e: com.tazzzo.app.data.remote.ApiException) {
            // 401/400: the grant is spent or refused — it cannot be retried.
            val status = (e.error as? com.tazzzo.app.data.remote.ApiError.Http)?.status
            if (status == 401 || status == 400) clearPending()
            throw e
        }
    }

    override suspend fun logout() {
        clearPending()
        session.logout()
    }

    private fun clearPending() {
        pendingChallengeId = null
        pendingGrantId = null
    }

    private companion object {
        val OTP_FORMAT = Regex("^[0-9]{6}$")
    }
}
