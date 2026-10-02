package com.tazzzo.app.data.auth

import com.tazzzo.app.data.remote.AccessTokenProvider
import com.tazzzo.app.data.remote.ApiError
import com.tazzzo.app.data.remote.ApiException
import com.tazzzo.app.data.remote.AuthRecovery
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlin.time.Clock
import kotlin.time.ExperimentalTime

/**
 * How a cold-start [AuthSessionManager.restore] ended. Callers that keep state across a restart (the pending order
 * recovery record) must be able to tell these apart: "nothing was saved" and "the backend refused the credential" are not
 * the same as "a session is here".
 */
enum class RestoreOutcome {
    /** A session is held: restored as saved, or kept because a refresh failed transiently (offline, timeout, 5xx). */
    Restored,
    /** The secure store held no (readable) session. Not a logout and not a rejection: nothing is known either way. */
    NoSession,
    /** The stored credential was refreshed and DEFINITIVELY refused (401/400): the session was cleared. */
    Rejected
}

/** The result of one refresh round, shared by every caller that waited on it. */
enum class RefreshOutcome {
    /** A newer token is available (this call rotated it, or someone else already had). */
    Refreshed,

    /** The backend refused the stored refresh credential: the session is over and cleared. */
    Rejected,

    /** Network/timeout/5xx: nothing was learned about the credential. The session is KEPT. */
    Transient
}

/**
 * Owns the customer session: in-memory tokens, secure persistence, expiry
 * tracking and refresh.
 *
 *  - **Opaque access tokens.** Expiry comes from the server's
 *    `accessTokenExpiresIn`, never from parsing the token.
 *  - **Single flight.** However many requests see a 401 (or an expired token)
 *    at once, exactly one `POST /v1/auth/refresh` is in flight; the rest await
 *    its result. A caller whose rejected token is no longer current simply
 *    retries with the already-rotated one.
 *  - **Rotation is persisted first.** The backend invalidates the old refresh
 *    token the instant it rotates, so the new one is written to the secure
 *    store BEFORE it is used or exposed.
 *  - **Definitive vs transient.** Only a 401/400 from refresh ends the session
 *    (credential refused). Offline, timeout and 5xx keep the tokens and surface
 *    as a failed recovery, so a flaky network never logs a customer out.
 *  - **No recursion.** Refresh/session/OTP/logout go through a client without
 *    this manager attached.
 *  - Nothing here logs or exposes a token, OTP or phone number.
 */
@OptIn(ExperimentalTime::class)
class AuthSessionManager(
    private val remote: RemoteAuthDataSource,
    private val store: SecureTokenStore,
    private val scope: CoroutineScope,
    private val nowMs: () -> Long = { Clock.System.now().toEpochMilliseconds() },
    private val refreshMarginMs: Long = DEFAULT_REFRESH_MARGIN_MS
) : AccessTokenProvider, AuthRecovery {

    private val lock = Mutex()
    private val tokens = MutableStateFlow<StoredTokens?>(null)
    private var inFlight: Deferred<RefreshOutcome>? = null

    /** True while the secure session is the authority that the customer is signed in. */
    val isAuthenticated: Boolean get() = tokens.value != null

    /** Emits the current value, then every change of signed-in/out (not token rotations). */
    val active: Flow<Boolean> = tokens.map { it != null }.distinctUntilChanged()

    private fun isStale(t: StoredTokens) = nowMs() >= t.accessExpiresAtMs - refreshMarginMs

    /** Cold start: load the secure session; refresh silently if it is (nearly) expired. */
    suspend fun restore(): RestoreOutcome {
        val saved = runCatching { store.load() }.getOrNull()
        tokens.value = saved
        if (saved == null) return RestoreOutcome.NoSession
        if (isStale(saved)) refreshShared(saved.accessToken)
        // A transient refresh failure keeps the tokens (Restored); only a definitive refusal clears them (Rejected).
        return if (tokens.value != null) RestoreOutcome.Restored else RestoreOutcome.Rejected
    }

    /** Exchanges a verified one-time grant for a session and persists it. */
    internal suspend fun establish(grantId: String) {
        val s = remote.createSession(grantId)
        val next = StoredTokens(s.accessToken, s.refreshToken, nowMs() + s.accessTokenExpiresIn * 1000, s.customerId)
        persist(next)
        tokens.value = next
    }

    override suspend fun accessToken(): String? {
        val t = tokens.value ?: return null
        if (isStale(t)) refreshShared(t.accessToken) // proactive; a transient failure falls through
        return tokens.value?.accessToken
    }

    override suspend fun recover(rejectedToken: String): Boolean =
        refreshShared(rejectedToken) == RefreshOutcome.Refreshed

    /**
     * Best-effort server revoke, then ALWAYS clear local state. A failed
     * revoke must never leave the app locally signed in.
     */
    suspend fun logout() {
        try {
            var t = tokens.value
            if (t != null && isStale(t)) {
                refreshShared(t.accessToken)
                t = tokens.value
            }
            if (t != null) remote.logout(t.accessToken)
        } catch (e: CancellationException) {
            throw e
        } catch (_: Exception) {
            // ignored on purpose: local sign-out below is unconditional
        } finally {
            endSession()
        }
    }

    private suspend fun refreshShared(rejectedToken: String?): RefreshOutcome {
        val deferred = lock.withLock {
            val cur = tokens.value ?: return RefreshOutcome.Rejected
            if (rejectedToken != null && cur.accessToken != rejectedToken) return RefreshOutcome.Refreshed
            inFlight ?: scope.async { doRefresh(cur) }.also { inFlight = it }
        }
        return try {
            deferred.await()
        } finally {
            lock.withLock { if (inFlight === deferred && deferred.isCompleted) inFlight = null }
        }
    }

    private suspend fun doRefresh(cur: StoredTokens): RefreshOutcome = try {
        val r = remote.refresh(cur.refreshToken)
        val next = cur.copy(
            accessToken = r.accessToken,
            refreshToken = r.refreshToken,
            accessExpiresAtMs = nowMs() + r.accessTokenExpiresIn * 1000
        )
        persist(next) // before use: the old refresh token is already dead server-side
        tokens.value = next
        RefreshOutcome.Refreshed
    } catch (e: CancellationException) {
        throw e
    } catch (e: ApiException) {
        val http = e.error as? ApiError.Http
        if (http != null && (http.status == 401 || http.status == 400)) {
            endSession()
            RefreshOutcome.Rejected
        } else {
            RefreshOutcome.Transient
        }
    } catch (_: Exception) {
        RefreshOutcome.Transient
    }

    private fun persist(t: StoredTokens) {
        // A failed write must not strand a session the server already rotated.
        runCatching { store.save(t) }
    }

    private fun endSession() {
        runCatching { store.clear() }
        tokens.value = null
    }

    companion object {
        const val DEFAULT_REFRESH_MARGIN_MS = 30_000L
    }
}
