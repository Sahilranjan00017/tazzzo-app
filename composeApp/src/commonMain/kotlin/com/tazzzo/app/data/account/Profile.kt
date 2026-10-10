package com.tazzzo.app.data.account

import com.tazzzo.app.data.remote.ApiClient
import com.tazzzo.app.data.remote.ApiError
import com.tazzzo.app.data.remote.ApiException
import com.tazzzo.app.data.remote.ApiRequest
import com.tazzzo.app.data.remote.IfMatch
import com.tazzzo.app.data.remote.execute
import io.ktor.http.HttpMethod
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive

/*
 * `GET/PATCH /v1/customer/profile` (backend PR-12A). The profile carries `displayName` and `email` (both optional) and a
 * `version` for `If-Match: "profile-<version>"`; it deliberately carries NO phone number (auth identity is never re-exposed),
 * so the app shows no phone. The customer id is never shown. Only the display name is editable here.
 */

@Serializable internal data class ProfileDto(val customerId: String? = null, val displayName: String? = null, val email: String? = null, val version: Long = 0, val requestId: String? = null)

data class CustomerProfile(val displayName: String?, val email: String?, val version: Long) {
    override fun toString(): String = "CustomerProfile(***)"
}

internal fun ProfileDto.toDomain() = CustomerProfile(
    displayName = displayName?.trim()?.takeIf { it.isNotEmpty() },
    email = email?.trim()?.takeIf { it.isNotEmpty() },
    version = version.coerceAtLeast(0)
)

/** The backend's `DisplayNames` rule: trimmed, at most 80 code points, no control characters; empty clears the name. */
object DisplayNameRules {
    const val MAX_CODE_POINTS = 80

    sealed interface Check {
        /** [value] null = clear the name. */
        data class Ok(val value: String?) : Check
        data object TooLong : Check
        data object InvalidCharacters : Check
    }

    fun check(raw: String): Check {
        val t = raw.trim()
        if (t.isEmpty()) return Check.Ok(null)
        if (codePoints(t) > MAX_CODE_POINTS) return Check.TooLong
        if (t.any { it.isISOControl() }) return Check.InvalidCharacters
        return Check.Ok(t)
    }

    private fun codePoints(s: String): Int { var n = 0; var i = 0; while (i < s.length) { i += if (s[i].isHighSurrogate() && i + 1 < s.length && s[i + 1].isLowSurrogate()) 2 else 1; n++ }; return n }
}

interface ProfileSource {
    suspend fun get(): CustomerProfile
    suspend fun setDisplayName(name: String?, version: Long): CustomerProfile
}

class RemoteProfileDataSource(private val api: ApiClient) : ProfileSource {
    override suspend fun get(): CustomerProfile =
        api.execute<ProfileDto>(ApiRequest(method = HttpMethod.Get, path = BASE, authenticated = true)).body.toDomain()

    /** PATCH `{"displayName": name|null}` with `If-Match: "profile-<version>"`. Nothing else is ever sent. */
    override suspend fun setDisplayName(name: String?, version: Long): CustomerProfile {
        require(DisplayNameRules.check(name ?: "") == DisplayNameRules.Check.Ok(name)) { "invalid display name" }
        return api.execute<ProfileDto>(
            ApiRequest(
                method = HttpMethod.Patch, path = BASE, authenticated = true, ifMatch = IfMatch.of(IfMatch.PROFILE, version),
                body = JsonObject(mapOf("displayName" to (name?.let { JsonPrimitive(it) } ?: kotlinx.serialization.json.JsonNull)))
            )
        ).body.toDomain()
    }

    companion object { const val BASE = "/v1/customer/profile" }
}

sealed interface ProfileState {
    data object SignedOut : ProfileState
    data object Loading : ProfileState
    data class Loaded(val profile: CustomerProfile) : ProfileState
    /** Not readable right now; the Profile tab falls back to "Signed in" and offers nothing editable. */
    data object Failed : ProfileState
}

sealed interface NameSave {
    data object Idle : NameSave
    data object Saving : NameSave
    data object Saved : NameSave
    /** The profile changed elsewhere (412): it was re-read; the customer may try again. */
    data object Stale : NameSave
    data object Invalid : NameSave
    data object Failed : NameSave
}

/** The signed-in customer's profile, in memory only; forgotten on sign-out. */
class ProfileStore(
    private val scope: CoroutineScope,
    private val source: ProfileSource,
    private val isAuthenticated: () -> Boolean
) {
    private val _state = MutableStateFlow<ProfileState>(ProfileState.SignedOut)
    val state: StateFlow<ProfileState> = _state
    private val _save = MutableStateFlow<NameSave>(NameSave.Idle)
    val save: StateFlow<NameSave> = _save
    private var generation = 0

    /** Loads once per session (a no-op while loaded); call [refresh] to re-read. */
    fun ensureLoaded() = scope.launch {
        if (!isAuthenticated()) { _state.value = ProfileState.SignedOut; return@launch }
        if (_state.value is ProfileState.Loaded || _state.value == ProfileState.Loading) return@launch
        load()
    }

    fun refresh() = scope.launch { if (isAuthenticated()) load() else _state.value = ProfileState.SignedOut }

    fun saveDisplayName(raw: String) = scope.launch {
        if (_save.value == NameSave.Saving) return@launch                  // a double tap while a save is in flight sends nothing
        val current = (_state.value as? ProfileState.Loaded)?.profile ?: return@launch
        val ok = DisplayNameRules.check(raw) as? DisplayNameRules.Check.Ok ?: run { _save.value = NameSave.Invalid; return@launch }
        if (ok.value == current.displayName) { _save.value = NameSave.Saved; return@launch }
        val gen = generation
        _save.value = NameSave.Saving
        try {
            val updated = source.setDisplayName(ok.value, current.version)
            if (gen != generation) return@launch
            _state.value = ProfileState.Loaded(updated); _save.value = NameSave.Saved
        } catch (e: CancellationException) {
            throw e
        } catch (e: Throwable) {
            if (gen != generation) return@launch
            val status = ((e as? ApiException)?.error as? ApiError.Http)?.status
            when (status) {
                // Re-read WITHOUT leaving Loaded, so the editor stays open with the customer's draft and shows the stale message.
                412, 428 -> { load(silent = true); _save.value = NameSave.Stale }
                400 -> _save.value = NameSave.Invalid
                else -> _save.value = NameSave.Failed
            }
        }
    }

    fun acknowledgeSave() { _save.value = NameSave.Idle }

    /** Session ended or changed: forget the profile (an in-flight read or save is discarded). */
    fun signOut() = scope.launch { generation++; _state.value = ProfileState.SignedOut; _save.value = NameSave.Idle }

    /** [silent]: keep the current Loaded profile on screen while re-reading (and keep it if the re-read fails). */
    private suspend fun load(silent: Boolean = false) {
        val gen = generation
        if (!silent) _state.value = ProfileState.Loading
        val next = try { ProfileState.Loaded(source.get()) } catch (e: CancellationException) { throw e } catch (_: Throwable) { if (silent) null else ProfileState.Failed }
        if (gen == generation && next != null) _state.value = next
    }
}

/** The name line of the Profile card: the display name when set, otherwise null (the card says "Signed in"). */
fun ProfileState.displayName(): String? = (this as? ProfileState.Loaded)?.profile?.displayName
