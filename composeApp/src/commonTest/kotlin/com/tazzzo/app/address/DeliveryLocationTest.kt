package com.tazzzo.app.address

import com.tazzzo.app.data.address.AddressBook
import com.tazzzo.app.data.address.BookState
import com.tazzzo.app.data.address.DeliveryLocation
import com.tazzzo.app.data.address.SessionLocationBinding
import com.tazzzo.app.data.address.deliverySuggestion
import com.tazzzo.app.data.address.suggestionText
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class DeliveryLocationTest {
    private class Env(
        val pins: FakePins, val selection: MemorySelection, val source: FakeAddressSource, val book: AddressBook, val location: DeliveryLocation
    )

    private fun TestScope.env(selected: String? = null, vararg server: com.tazzzo.app.data.address.CustomerAddress): Env {
        val pins = FakePins("560047"); val sel = MemorySelection(selected)
        val source = FakeAddressSource().apply { this.server.addAll(server) }
        val book = AddressBook(backgroundScope, source) { true }
        return Env(pins, sel, source, book, DeliveryLocation(backgroundScope, pins, sel, book))
    }

    private val home = ca("ADDR_home001", AddressLabelHome, "560102", isDefault = true)
    private val work = ca("ADDR_work002", com.tazzzo.app.data.address.AddressLabel.WORK, "560103")

    // ---- default suggestion -------------------------------------------------------------------------------

    @Test fun loginAndLoadNeverChangeThePinOnTheirOwn() = runTest {
        val e = env(null, home, work)
        e.book.load(); runCurrent()
        assertEquals("560047", e.pins.pin.value.value)           // the existing launch PIN stays active
        assertTrue(e.pins.writes.isEmpty())
        assertNull(e.location.selectedAddressId.value); assertNull(e.selection.selectedAddressId)
    }

    @Test fun theDefaultIsOfferedNotApplied() = runTest {
        val e = env(null, home, work)
        e.book.load(); runCurrent()
        val offer = e.location.suggestion.value!!
        assertEquals("ADDR_home001", offer.addressId)
        assertEquals("Deliver to Home · 560102?", offer.suggestionText())
        assertEquals("560047", e.pins.pin.value.value)
    }

    @Test fun acceptingTheSuggestionSelectsTheAddressAndSetsItsPin() = runTest {
        val e = env(null, home, work)
        e.book.load(); runCurrent()
        e.location.selectAddress(e.location.suggestion.value!!); runCurrent()
        assertEquals("560102", e.pins.pin.value.value)
        assertEquals("ADDR_home001", e.location.selectedAddressId.value); assertEquals("ADDR_home001", e.selection.selectedAddressId)
        assertNull(e.location.suggestion.value, "once chosen there is nothing left to suggest")
    }

    @Test fun keepingTheCurrentLocationDismissesTheOfferWithoutChangingAnything() = runTest {
        val e = env(null, home, work)
        e.book.load(); runCurrent()
        e.location.keepCurrentLocation(); runCurrent()
        assertNull(e.location.suggestion.value); assertEquals("560047", e.pins.pin.value.value); assertTrue(e.pins.writes.isEmpty())
    }

    @Test fun noDefaultMeansNoSuggestion() = runTest {
        val e = env(null, ca("ADDR_aaaaaa1"))
        e.book.load(); runCurrent()
        assertNull(e.location.suggestion.value)
    }

    @Test fun suggestionIsAPureFunctionOfBookSelectionAndDismissal() {
        val loaded = BookState.Loaded(listOf(home, work))
        assertEquals(home, deliverySuggestion(loaded, null, false))
        assertNull(deliverySuggestion(loaded, "ADDR_work002", false)); assertNull(deliverySuggestion(loaded, null, true))
        assertNull(deliverySuggestion(BookState.Loading, null, false)); assertNull(deliverySuggestion(BookState.SignedOut, null, false))
    }

    // ---- manual pin ------------------------------------------------------------------------------------------

    @Test fun aManualPinClearsTheSelectedAddressAndBecomesTheOnlySource() = runTest {
        val e = env(null, home, work)
        e.book.load(); runCurrent()
        e.location.selectAddress(home)
        assertTrue(e.location.setManualPin("110001"))
        assertNull(e.location.selectedAddressId.value); assertNull(e.selection.selectedAddressId)
        assertEquals("110001", e.pins.pin.value.value)
    }

    @Test fun anInvalidManualPinChangesNothing() = runTest {
        val e = env(null, home)
        e.book.load(); runCurrent(); e.location.selectAddress(home)
        assertFalse(e.location.setManualPin("060047")); assertFalse(e.location.setManualPin("abc"))
        assertEquals("ADDR_home001", e.location.selectedAddressId.value); assertEquals("560102", e.pins.pin.value.value)
    }

    // ---- edits and deletes of the selected address --------------------------------------------------------------------

    @Test fun editingTheSelectedAddressesPinMovesTheLocationAfterTheRefetch() = runTest {
        val e = env("ADDR_home001", home.copy(), work)
        e.book.load(); runCurrent()
        e.pins.setPin("560102")                                         // the persisted selection and its PIN agree
        e.source.server[0] = e.source.server[0].copy(postalCode = pin("570001"), version = 2)   // edited (server-side)
        e.book.refresh(); runCurrent()
        assertEquals("570001", e.pins.pin.value.value)
        assertEquals("ADDR_home001", e.location.selectedAddressId.value)
    }

    @Test fun deletingTheSelectedAddressClearsTheIdButKeepsThePin() = runTest {
        val e = env(null, home, work)
        e.book.load(); runCurrent(); e.location.selectAddress(work)
        e.book.delete(work); runCurrent()
        assertNull(e.location.selectedAddressId.value); assertNull(e.selection.selectedAddressId)
        assertEquals("560103", e.pins.pin.value.value)                  // retained until the customer picks another location
    }

    @Test fun aBackendPromotedDefaultIsNeverSilentlySelected() = runTest {
        val e = env(null, home, work)
        e.book.load(); runCurrent(); e.location.selectAddress(home)
        e.book.delete(home); runCurrent()                               // the server promotes `work` to default
        assertNull(e.location.selectedAddressId.value)
        assertEquals("560102", e.pins.pin.value.value)                  // still the old PIN: a default is not a selection
        assertEquals("ADDR_work002", e.location.suggestion.value?.addressId)   // it is only offered
    }

    @Test fun aPersistedSelectionThatNoLongerExistsIsClearedSafely() = runTest {
        val e = env("ADDR_gone0001", home)
        e.book.load(); runCurrent()
        assertNull(e.location.selectedAddressId.value); assertNull(e.selection.selectedAddressId)
        assertEquals("560047", e.pins.pin.value.value)
    }

    @Test fun aPersistedSelectionThatStillExistsSurvivesAColdStart() = runTest {
        val e = env("ADDR_home001", home)
        e.book.load(); runCurrent()
        assertEquals("ADDR_home001", e.location.selectedAddressId.value)
    }

    // ---- logout ------------------------------------------------------------------------------------------------------------

    @Test fun logoutClearsEverythingAndResetsThePinTo560047() = runTest {
        val e = env(null, home, work)
        e.book.load(); runCurrent(); e.location.selectAddress(work)
        e.book.signOut(); e.location.onSignedOut(); runCurrent()
        assertNull(e.location.selectedAddressId.value); assertNull(e.selection.selectedAddressId)
        assertEquals("560047", e.pins.pin.value.value)
        assertEquals(BookState.SignedOut, e.book.state.value)
    }

    @Test fun theBindingTreatsRealTransitionsAsSignInAndSignOutButNotTheColdStartDefault() = runTest {
        val e = env("ADDR_work002", home, work)
        val active = MutableStateFlow(false)
        // cold start: restored session is authenticated, flow starts false then flips true
        SessionLocationBinding(backgroundScope, active, e.book, e.location).start(initiallyAuthenticated = true)
        runCurrent()
        assertEquals("ADDR_work002", e.location.selectedAddressId.value, "the initial false is not a logout")
        active.value = true; runCurrent()
        assertIs(e.book.state.value)                                    // signing in loads the book
        e.pins.setPin("560103")
        active.value = false; runCurrent()                              // a real sign-out (or definitive rejection)
        assertEquals(BookState.SignedOut, e.book.state.value)
        assertNull(e.location.selectedAddressId.value); assertEquals("560047", e.pins.pin.value.value)
    }

    @Test fun aGuestAtColdStartCannotKeepAStaleSelectionButKeepsItsManualPin() = runTest {
        val e = env("ADDR_stale001")
        e.pins.setPin("110001")
        SessionLocationBinding(backgroundScope, MutableStateFlow(false), e.book, e.location).start(initiallyAuthenticated = false)
        runCurrent()
        assertNull(e.location.selectedAddressId.value); assertNull(e.selection.selectedAddressId)
        assertEquals("110001", e.pins.pin.value.value)
    }

    private fun assertIs(s: BookState) = assertTrue(s is BookState.Loaded || s is BookState.Loading, "$s")

    companion object { private val AddressLabelHome = com.tazzzo.app.data.address.AddressLabel.HOME }
}
