package com.tazzzo.app.auth

import com.russhwolf.settings.MapSettings
import com.tazzzo.app.Screen
import com.tazzzo.app.TazzzoAppState
import com.tazzzo.app.data.local.PersistentStore
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class AuthAppStateTest {
    @Test fun appStartsAsAGuestAndGuestBrowsingWorksWithoutAnySession() {
        val app = TazzzoAppState(store = null)
        assertFalse(app.isAuthenticated); assertTrue(app.user.isGuest)
        app.requestGuidedTourIfFirstTime(); app.goHome() // what "Skip for now" does
        assertEquals(listOf<Screen>(Screen.Home), app.backStack.toList())
        assertTrue(app.user.isGuest)
    }

    @Test fun signingInMakesTheSecureSessionTheAuthorityWithNeutralProfile() {
        val settings = MapSettings(); val app = TazzzoAppState(store = PersistentStore(settings))
        app.onSignedIn()
        assertTrue(app.isAuthenticated); assertFalse(app.user.isGuest)
        assertEquals("", app.user.name); assertEquals("", app.user.phone) // nothing invented, phone not stored
    }

    @Test fun sessionEndingElsewhereFlipsTheAppBackToGuest() {
        val app = TazzzoAppState(store = null); app.onSignedIn()
        app.applyAuthState(false)
        assertFalse(app.isAuthenticated); assertTrue(app.user.isGuest)
    }

    @Test fun aSavedNonGuestProfileDoesNotMakeAnyoneSignedInWithoutSecureTokens() = kotlinx.coroutines.test.runTest {
        val store = PersistentStore(MapSettings())
        store.saveSession(PersistentStore.SavedSession("Old Mock", "9876543210", isGuest = false, coinBalance = 5))
        val app = TazzzoAppState(store = store)
        app.restoreFromDisk()
        assertTrue(app.user.isGuest, "isGuest from disk must not be trusted")
        assertFalse(app.isAuthenticated)
    }

    @Test fun logoutRevokesThenReturnsToGuestEvenIfTheServerFails() = kotlinx.coroutines.test.runTest {
        val app = TazzzoAppState(store = null); app.onSignedIn()
        val failing = object : com.tazzzo.app.data.auth.AuthRepository by FakeAuthRepository() {
            override suspend fun logout() { throw RuntimeException("server down") }
        }
        runCatching { app.logout(failing) }
        assertFalse(app.isAuthenticated); assertTrue(app.user.isGuest)
        app.onSignedIn(); app.logout(FakeAuthRepository()) // the normal path too
        assertFalse(app.isAuthenticated)
    }

    // Mutation note: removing clearRecentSearches() from markLoggedOut()/applyAuthState(false) makes both tests fail.
    @Test fun logoutClearsRecentSearchesFromStateAndFromThePersistedKey() = kotlinx.coroutines.test.runTest {
        val settings = MapSettings(); val app = TazzzoAppState(store = PersistentStore(settings))
        app.onSignedIn(); app.recordSearch("milk")
        assertEquals(listOf("milk"), app.recentSearches.toList())
        assertTrue(settings.getStringOrNull("tazzzo.searches.v1") != null)
        app.logout(FakeAuthRepository())
        assertTrue(app.recentSearches.isEmpty())
        assertTrue(PersistentStore(settings).loadRecentSearches().isEmpty())
        assertEquals(null, settings.getStringOrNull("tazzzo.searches.v1"))
    }

    @Test fun aSessionEndingElsewhereAlsoClearsRecentSearches() {
        val settings = MapSettings(); val app = TazzzoAppState(store = PersistentStore(settings))
        app.onSignedIn(); app.recordSearch("milk")
        app.applyAuthState(false)
        assertTrue(app.recentSearches.isEmpty()); assertEquals(null, settings.getStringOrNull("tazzzo.searches.v1"))
    }
}
