package com.tazzzo.app.checkout

import com.tazzzo.app.CheckoutSession
import com.tazzzo.app.HomeTab
import com.tazzzo.app.TazzzoAppState
import com.tazzzo.app.data.checkout.Iso8601
import com.tazzzo.app.data.catalog.CatalogCapabilities
import com.tazzzo.app.data.catalog.CatalogMode
import com.tazzzo.app.data.catalog.CatalogSource
import com.tazzzo.app.data.model.Address
import com.tazzzo.app.data.model.CoinTransaction
import com.tazzzo.app.data.model.DeliverySlot
import com.tazzzo.app.data.model.OrderRequest
import com.tazzzo.app.data.model.PaymentMethodKind
import com.tazzzo.app.data.model.Product
import com.tazzzo.app.data.repository.CoinRepository
import com.tazzzo.app.data.repository.RemoteModeCheckoutGuard
import com.tazzzo.app.data.repository.ServiceLocator
import com.tazzzo.app.order.OrderPlacement
import com.tazzzo.app.r
import com.tazzzo.app.ui.home.visibleHomeTabs
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** A real quote must have NO path into the mock order machinery. */
class CheckoutBoundaryTest {
    private inline fun <T> inMode(mode: CatalogMode?, block: () -> T): T {
        val before = CatalogSource.debugOverride
        CatalogSource.debugOverride = mode
        try { return block() } finally { CatalogSource.debugOverride = before }
    }

    private class CountingCoins : CoinRepository {
        var credits = 0
        override suspend fun getBalance() = 0
        override suspend fun getLedger(): List<CoinTransaction> = emptyList()
        override suspend fun credit(amount: Int, title: String) { credits++ }
    }

    @Test fun placeOrderRefusesInRemoteModeAsWellAsEveryOtherMockCheckoutCall() = runTest {
        inMode(null) {
            val c = ServiceLocator.checkout
            assertFailsWith<UnsupportedOperationException> { c.getSlots("a") }
            assertFailsWith<UnsupportedOperationException> { c.getPaymentMethods() }
            assertFailsWith<UnsupportedOperationException> { c.validateCart(emptyList()) }
            assertFailsWith<UnsupportedOperationException> {
                c.placeOrder(OrderRequest("chk-123456789012", emptyList(), com.tazzzo.app.config.BillCalculator.bill(emptyList()), "a", "t", "s", PaymentMethodKind.COD))
            }
            assertFailsWith<UnsupportedOperationException> { RemoteModeCheckoutGuard.placeOrder(OrderRequest("chk-123456789012", emptyList(), com.tazzzo.app.config.BillCalculator.bill(emptyList()), "a", "t", "s", PaymentMethodKind.COD)) }
        }
    }

    @Test fun theMockOrderPlacementRefusesWithNoSideEffectsWhenGivenTheRemoteGuard() = runTest {
        val app = TazzzoAppState(store = null)
        val milk = Product(id = "p8", name = "Toned Milk Pouch", brand = "Amul", emoji = "🥛", unit = "500 ml", price = r(29), mrp = r(30), categoryId = "dairy", subcategoryId = "milk", rating = 4.7, ratingCount = 8804)
        app.addToCart(milk)
        val session = CheckoutSession()
        val coins = CountingCoins()
        val result = OrderPlacement.place(
            app, session, Address("addr-1", "Home", "22, 14th Main, HSR", "", "560102", isServiceable = true), DeliverySlot("s", "Express", available = true),
            PaymentMethodKind.COD, checkout = RemoteModeCheckoutGuard, coins = coins, navigate = false
        )
        assertNull(result)
        assertEquals(0, coins.credits); assertNull(app.lastOrder); assertEquals(1, app.cartItemCount)
        assertTrue(session.placement is CheckoutSession.Placement.Idle)
    }

    @Test fun remoteHasCheckoutReviewButNoOrderCapability() {
        val r = CatalogCapabilities.REMOTE
        assertTrue(r.checkoutIntegration); assertFalse(r.orderIntegration)
        assertTrue(HomeTab.ORDER_AGAIN !in visibleHomeTabs(r))
        assertTrue(HomeTab.ORDER_AGAIN in visibleHomeTabs(CatalogCapabilities.MOCK))
    }

    @Test fun theMockCheckoutAndOrderPlacementStillWorkInExplicitMockMode() = runTest {
        inMode(CatalogMode.MOCK) { assertTrue(ServiceLocator.checkout.getPaymentMethods().isNotEmpty()) }
    }

    @Test fun anIsoTimestampParsesToExactEpochMillis() {
        assertEquals(0L, Iso8601.parseMillis("1970-01-01T00:00:00Z"))
        assertEquals(1_000L, Iso8601.parseMillis("1970-01-01T00:00:01.000Z"))
        assertEquals(1_759_394_700_123L, Iso8601.parseMillis("2025-10-02T08:45:00.123456789Z"))
        assertEquals(951_782_400_000L, Iso8601.parseMillis("2000-02-29T00:00:00Z"))
        assertEquals(1_790_931_600_000L, Iso8601.parseMillis("2026-10-02T09:00:00.000Z"))               // leap day
        for (bad in listOf("", "2026-10-02", "2026-13-01T00:00:00Z", "2026-10-02T25:00:00Z", "2026-10-02T00:00:00+05:30", "x")) assertNull(Iso8601.parseMillis(bad), bad)
    }
}
