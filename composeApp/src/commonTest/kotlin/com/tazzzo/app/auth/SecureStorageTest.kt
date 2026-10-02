package com.tazzzo.app.auth

import com.russhwolf.settings.MapSettings
import com.tazzzo.app.data.auth.AuthInstallGuard
import com.tazzzo.app.data.auth.BlobSecureTokenStore
import com.tazzzo.app.data.local.PersistentStore
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class SecureStorageTest {
    @Test fun saveLoadClearRoundTrip() {
        val blob = FakeBlobStore(); val s = BlobSecureTokenStore(blob)
        assertNull(s.load())
        s.save(tokens("a", "SES_x.r", 123L))
        assertEquals(tokens("a", "SES_x.r", 123L), s.load())
        s.clear(); assertNull(s.load()); assertNull(blob.value)
    }

    @Test fun undecryptableBlobReadsAsLoggedOutAndIsWiped() {
        val blob = FakeBlobStore("anything", failReads = true); val s = BlobSecureTokenStore(blob)
        assertNull(s.load()); assertEquals(1, blob.deletes)
    }

    @Test fun corruptedPlaintextReadsAsLoggedOutAndIsWiped() {
        val blob = FakeBlobStore("{not json"); val s = BlobSecureTokenStore(blob)
        assertNull(s.load()); assertNull(blob.value)
        blob.value = """{"accessToken":"a"}"""; assertNull(s.load()) // missing required fields
    }

    @Test fun theBlobHoldsTokensButNeverGoesNearPlainSettings() {
        val settings = MapSettings(); val store = PersistentStore(settings)
        BlobSecureTokenStore(FakeBlobStore()).save(tokens("SECRET-ACCESS", "SES_x.SECRET-REFRESH"))
        store.saveSession(PersistentStore.SavedSession("", "", false, 0))
        assertTrue(settings.keys.none { k -> settings.getStringOrNull(k)?.contains("SECRET") == true })
    }

    // --- iOS reinstall: Keychain survives uninstall, the settings marker does not -------------------------

    @Test fun freshInstallClearsInheritedCredentialsThenSetsTheMarker() {
        val secure = InMemorySecureTokenStore(tokens("stale", "SES_x.stale"))
        val store = PersistentStore(MapSettings())
        assertFalse(store.authInstallMarker)
        AuthInstallGuard.clearStaleCredentialsOnFreshInstall(store, secure)
        assertNull(secure.current); assertTrue(store.authInstallMarker)
    }

    @Test fun anExistingInstallKeepsItsCredentials() {
        val secure = InMemorySecureTokenStore(tokens("live", "SES_x.live"))
        val store = PersistentStore(MapSettings()).apply { authInstallMarker = true }
        AuthInstallGuard.clearStaleCredentialsOnFreshInstall(store, secure)
        assertEquals("live", secure.current!!.accessToken); assertEquals(0, secure.clears)
    }

    @Test fun theGuardIsIdempotent() {
        val secure = InMemorySecureTokenStore(tokens())
        val store = PersistentStore(MapSettings())
        AuthInstallGuard.clearStaleCredentialsOnFreshInstall(store, secure)
        secure.save(tokens("new", "SES_x.new"))
        AuthInstallGuard.clearStaleCredentialsOnFreshInstall(store, secure)
        assertEquals("new", secure.current!!.accessToken)
    }
}
