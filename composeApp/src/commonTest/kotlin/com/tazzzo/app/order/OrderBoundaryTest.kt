package com.tazzzo.app.order

import com.tazzzo.app.HomeTab
import com.tazzzo.app.data.catalog.CatalogCapabilities
import com.tazzzo.app.data.catalog.CatalogMode
import com.tazzzo.app.data.catalog.CatalogSource
import com.tazzzo.app.data.order.OrderLaunchGate
import com.tazzzo.app.data.repository.ServiceLocator
import com.tazzzo.app.ui.home.visibleHomeTabs
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** REMOTE must never show or feed mock orders, coins, Club progress or "Order again". */
class OrderBoundaryTest {
    private inline fun <T> inMode(mode: CatalogMode?, block: () -> T): T {
        val before = CatalogSource.debugOverride
        CatalogSource.debugOverride = mode
        try { return block() } finally { CatalogSource.debugOverride = before }
    }

    @Test fun theMockOrdersAreUnreachableInRemoteMode() = runTest {
        inMode(null) {
            assertFailsWith<UnsupportedOperationException> { ServiceLocator.orders.getOrders() }
            assertFailsWith<UnsupportedOperationException> { ServiceLocator.orders.placeOrder(emptyList(), com.tazzzo.app.config.BillCalculator.bill(emptyList()), "x") }
        }
    }

    @Test fun theMockCoinsAreUnreachableInRemoteModeSoNoFakeBalanceOrCreditExists() = runTest {
        inMode(null) {
            assertFailsWith<UnsupportedOperationException> { ServiceLocator.coins.getBalance() }
            assertFailsWith<UnsupportedOperationException> { ServiceLocator.coins.getLedger() }
            assertFailsWith<UnsupportedOperationException> { ServiceLocator.coins.credit(10, "order") }
        }
    }

    @Test fun theMockOrdersAndCoinsStillWorkInExplicitMockMode() = runTest {
        inMode(CatalogMode.MOCK) {
            assertTrue(ServiceLocator.orders.getOrders().isNotEmpty())
            assertEquals(40, ServiceLocator.coins.getBalance())
        }
    }

    @Test fun orderAgainStaysHiddenInRemoteEvenWithRealHistory() {
        val remote = CatalogCapabilities.REMOTE
        assertTrue(remote.orderHistoryIntegration)                                                       // real history exists...
        assertFalse(remote.reorder)                                                                      // ...but no reorder contract
        assertTrue(HomeTab.ORDER_AGAIN !in visibleHomeTabs(remote))
        assertTrue(HomeTab.ORDERS in visibleHomeTabs(remote))
        assertTrue(HomeTab.ORDER_AGAIN in visibleHomeTabs(CatalogCapabilities.MOCK))
    }

    @Test fun releaseRemotePlacesRealOrdersThroughTheCapabilityAlone() {
        assertTrue(CatalogCapabilities.REMOTE.orderIntegration)
        assertTrue(OrderLaunchGate.enabled(CatalogCapabilities.REMOTE))
        assertFalse(OrderLaunchGate.enabled(CatalogCapabilities.REMOTE.copy(orderIntegration = false)))   // fail-closed kill switch
        assertTrue(OrderLaunchGate.enabled(CatalogCapabilities.MOCK))
    }
}
