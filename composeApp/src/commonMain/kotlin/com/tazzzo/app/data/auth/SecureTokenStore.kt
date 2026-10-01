package com.tazzzo.app.data.auth

import com.tazzzo.app.data.local.PersistentStore
import kotlinx.serialization.json.Json

/**
 * Where session credentials live. NEVER multiplatform-settings: Android uses
 * Android Keystore (AES-256-GCM, non-exportable key) and iOS uses the Keychain.
 */
interface SecureTokenStore {
    /** The stored credentials, or null if none — or if they cannot be read/decrypted. */
    fun load(): StoredTokens?
    fun save(tokens: StoredTokens)
    fun clear()
}

/** The platform's raw encrypted slot: one opaque string. Implemented per platform. */
interface SecureBlobStore {
    /** Null when nothing is stored. May throw if the platform cannot decrypt. */
    fun read(): String?
    fun write(value: String)
    fun delete()
}

/** Platform factory: Keystore-backed on Android, Keychain-backed on iOS. */
expect fun createSecureBlobStore(): SecureBlobStore

/**
 * Encodes [StoredTokens] into a [SecureBlobStore]. A blob that cannot be read,
 * decrypted or decoded is treated as "no session" and wiped — a lost Keystore
 * key or corrupted entry must read as logged out, never as a crash or as
 * garbage credentials.
 */
class BlobSecureTokenStore(private val blob: SecureBlobStore) : SecureTokenStore {
    private val json = Json { ignoreUnknownKeys = true }

    override fun load(): StoredTokens? = try {
        blob.read()?.let { json.decodeFromString<StoredTokens>(it) }
    } catch (e: Exception) {
        runCatching { blob.delete() }
        null
    }

    override fun save(tokens: StoredTokens) = blob.write(json.encodeToString(StoredTokens.serializer(), tokens))

    override fun clear() = blob.delete()
}

/**
 * The iOS Keychain survives uninstall; regular app settings do not. A fresh
 * install (marker absent) therefore must not inherit a previous install's
 * credentials: clear the secure store, then set the non-secret marker.
 * Harmless on Android, where the marker and the store are wiped together.
 */
object AuthInstallGuard {
    fun clearStaleCredentialsOnFreshInstall(store: PersistentStore, secure: SecureTokenStore) =
        clearStaleCredentialsOnFreshInstall(store.authInstallMarker, { store.authInstallMarker = true }, secure)

    fun clearStaleCredentialsOnFreshInstall(markerPresent: Boolean, setMarker: () -> Unit, secure: SecureTokenStore) {
        if (!markerPresent) {
            runCatching { secure.clear() }
            setMarker()
        }
    }
}
