package com.tazzzo.app.data.auth

import com.tazzzo.app.data.remote.ApiClient
import com.tazzzo.app.data.remote.ApiRequest
import com.tazzzo.app.data.remote.execute
import io.ktor.http.HttpMethod
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.encodeToJsonElement

// Wire shapes for the customer auth API (`/v1/auth/**`, backend contract).
// Every type carrying a secret redacts toString so it cannot reach a log.

@Serializable internal data class OtpRequestBody(val phone: String) {
    override fun toString() = "OtpRequestBody(***)"
}

@Serializable internal data class OtpChallengeDto(
    val challengeId: String, val expiresInSeconds: Long, val resendAfterSeconds: Long
) {
    override fun toString() = "OtpChallengeDto(***)"
}

@Serializable internal data class OtpVerifyBody(val challengeId: String, val otp: String) {
    override fun toString() = "OtpVerifyBody(***)"
}

@Serializable internal data class OtpVerifyDto(val grantId: String) {
    override fun toString() = "OtpVerifyDto(***)"
}

@Serializable internal data class SessionBody(val grantId: String) {
    override fun toString() = "SessionBody(***)"
}

@Serializable internal data class SessionDto(
    val customerId: String, val accessToken: String, val accessTokenExpiresIn: Long, val refreshToken: String
) {
    override fun toString() = "SessionDto(***)"
}

@Serializable internal data class RefreshBody(val refreshToken: String) {
    override fun toString() = "RefreshBody(***)"
}

@Serializable internal data class RefreshDto(
    val accessToken: String, val accessTokenExpiresIn: Long, val refreshToken: String
) {
    override fun toString() = "RefreshDto(***)"
}

/**
 * The five auth endpoints. Built on an [ApiClient] that has NO token provider
 * and NO recovery hook: auth calls must never trigger 401 recovery (a refresh
 * inside a refresh is the recursion this separation exists to prevent).
 */
class RemoteAuthDataSource(private val api: ApiClient) {

    private fun post(path: String, body: JsonElement? = null, bearer: String? = null) =
        ApiRequest(HttpMethod.Post, path, body = body, authenticated = false, bearerToken = bearer)

    suspend fun requestOtp(e164: String): OtpChallenge {
        val body = api.json.encodeToJsonElement(OtpRequestBody(e164))
        val r = api.execute<OtpChallengeDto>(post("/v1/auth/otp/request", body)).body
        return OtpChallenge(r.challengeId, r.expiresInSeconds, r.resendAfterSeconds)
    }

    /** Returns the one-time `grantId`. Verifying alone is NOT being logged in. */
    suspend fun verifyOtp(challengeId: String, otp: String): String {
        val body = api.json.encodeToJsonElement(OtpVerifyBody(challengeId, otp))
        return api.execute<OtpVerifyDto>(post("/v1/auth/otp/verify", body)).body.grantId
    }

    internal suspend fun createSession(grantId: String): SessionDto =
        api.execute<SessionDto>(post("/v1/auth/session", api.json.encodeToJsonElement(SessionBody(grantId)))).body

    internal suspend fun refresh(refreshToken: String): RefreshDto =
        api.execute<RefreshDto>(post("/v1/auth/refresh", api.json.encodeToJsonElement(RefreshBody(refreshToken)))).body

    /** `204`, no body, explicit bearer. */
    suspend fun logout(accessToken: String) {
        api.executeUnit(post("/v1/auth/logout", bearer = accessToken))
    }
}
