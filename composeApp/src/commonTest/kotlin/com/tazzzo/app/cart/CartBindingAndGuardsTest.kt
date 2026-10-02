package com.tazzzo.app.cart

import com.russhwolf.settings.MapSettings
import com.tazzzo.app.TazzzoAppState
import com.tazzzo.app.data.cart.CartSessionBinding
import com.tazzzo.app.data.cart.CartState
import com.tazzzo.app.data.cart.CartStore
import com.tazzzo.app.data.catalog.CatalogMode
import com.tazzzo.app.data.catalog.CatalogSource
import com.tazzzo.app.data.catalog.Pincode
import com.tazzzo.app.data.local.PersistentStore
import com.tazzzo.app.data.repository.CheckoutRepository
import com.tazzzo.app.data.repository.ServiceLocator
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue

class CartBindingAndGuardsTest {
    private class Rig(scope: TestScope) {
        val src = FakeCartSource().apply { lines["TZP-1"] = line("TZP-1", 2); version = 1 }
        val active = MutableStateFlow(false)
        val selected = MutableStateFlow<String?>(null)
        val pin = MutableStateFlow(Pincode.LAUNCH)
        val store = CartStore(scope.backgroundScope, src, { active.value }, { selected.value })
        val gets get() = src.calls.count { it == "GET" }
        init { CartSessionBinding(scope.backgroundScope, active, selected, pin, store).start(); scope.runCurrent() }
    }

    @Test fun signingInLoadsTheCartAndSigningOutForgetsItWithoutClearingTheServer() = runTest {
        val r = Rig(this)
        assertEquals(0, r.gets)
        r.active.value = true; runCurrent()
        assertEquals(2, (r.store.state.value as CartState.Loaded).cart.quantityOf("TZP-1"))
        r.active.value = false; runCurrent()
        assertEquals(CartState.SignedOut, r.store.state.value)
        assertTrue(r.src.calls.none { it == "DELETE ALL" }); assertEquals(2, r.src.lines.getValue("TZP-1").quantity)
    }

    @Test fun aColdStartThatIsSignedOutDoesNotTouchTheBackend() = runTest {
        val r = Rig(this); runCurrent()
        assertTrue(r.src.calls.isEmpty())
    }

    @Test fun selectingAnAddressReReadsTheCartWithThatAddress() = runTest {
        val r = Rig(this); r.active.value = true; runCurrent()
        val before = r.gets
        r.selected.value = "ADDR_abcdef1"; runCurrent()
        assertEquals(before + 1, r.gets); assertEquals("ADDR_abcdef1", r.src.addressIds.last())
    }

    @Test fun clearingTheSelectedAddressReReadsWithoutOne() = runTest {
        val r = Rig(this); r.active.value = true; r.selected.value = "ADDR_abcdef1"; runCurrent()
        r.selected.value = null; runCurrent()
        assertNull(r.src.addressIds.last())
    }

    @Test fun aPinChangeOfTheSelectedAddressReReads() = runTest {
        val r = Rig(this); r.active.value = true; r.selected.value = "ADDR_abcdef1"; runCurrent()
        val before = r.gets
        r.pin.value = Pincode.parse("560102")!!; runCurrent()
        assertEquals(before + 1, r.gets)
    }

    @Test fun aManualPinWithNoSelectedAddressIsNotACartLocation() = runTest {
        val r = Rig(this); r.active.value = true; runCurrent()
        val before = r.gets
        r.pin.value = Pincode.parse("560102")!!; runCurrent()
        assertEquals(before, r.gets)
    }

    // ---- REMOTE guards --------------------------------------------------------------------------------------------

    private inline fun <T> inMode(mode: CatalogMode?, block: () -> T): T {
        val before = CatalogSource.debugOverride
        CatalogSource.debugOverride = mode
        try { return block() } finally { CatalogSource.debugOverride = before }
    }

    @Test fun theMockCheckoutIsUnreachableInRemoteMode() = runTest {
        inMode(null) {
            val c: CheckoutRepository = ServiceLocator.checkout
            assertFailsWith<UnsupportedOperationException> { c.getSlots("a") }
            assertFailsWith<UnsupportedOperationException> { c.getPaymentMethods() }
            assertFailsWith<UnsupportedOperationException> { c.validateCart(emptyList()) }
        }
    }

    @Test fun theMockCheckoutStillWorksInExplicitMockMode() = runTest {
        inMode(CatalogMode.MOCK) { assertTrue(ServiceLocator.checkout.getPaymentMethods().isNotEmpty()) }
    }

    @Test fun remoteHasCheckoutReviewButNoOrderPlacementAndMockHasBoth() {
        val r = com.tazzzo.app.data.catalog.CatalogCapabilities.REMOTE
        val m = com.tazzzo.app.data.catalog.CatalogCapabilities.MOCK
        assertEquals(true, r.checkoutIntegration); assertEquals(false, r.orderIntegration)
        assertEquals(true, m.checkoutIntegration); assertEquals(true, m.orderIntegration)
    }

    @Test fun aLegacyLocalCartIsPurgedNotRestoredInRemoteMode() = runTest {
        inMode(null) {
            val store = PersistentStore(MapSettings())
            store.saveCart(listOf(PersistentStore.SavedCartLine("p8", 2, 2_900L)))
            val app = TazzzoAppState(store = store)
            app.restoreFromDisk()
            assertTrue(app.cartLines().isEmpty()); assertTrue(store.loadCart().isEmpty())
        }
    }

    @Test fun theOrderAgainTabNeedsOrdersNotJustACheckoutReview() {
        val tabs = com.tazzzo.app.ui.home.visibleHomeTabs(com.tazzzo.app.data.catalog.CatalogCapabilities.REMOTE)
        assertTrue(com.tazzzo.app.HomeTab.ORDER_AGAIN !in tabs)
    }
}
