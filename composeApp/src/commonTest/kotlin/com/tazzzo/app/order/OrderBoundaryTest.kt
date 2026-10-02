package com.tazzzo.app.order

import com.tazzzo.app.HomeTab
import com.tazzzo.app.config.AppEnvironment
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

    @Test fun orderAgainStaysHiddenInRemoteWhateverTheOrderCapabilityIs() {
        val remote = CatalogCapabilities.REMOTE
        assertFalse(remote.orderHistoryIntegration)
        assertTrue(HomeTab.ORDER_AGAIN !in visibleHomeTabs(remote))
        assertTrue(HomeTab.ORDER_AGAIN !in visibleHomeTabs(remote.copy(orderIntegration = true)))      // enabling placing does not expose history
        assertTrue(HomeTab.ORDER_AGAIN in visibleHomeTabs(CatalogCapabilities.MOCK))
    }

    @Test fun productionRemoteKeepsOrderPlacementGatedUntilAnAuthoritativePayableExists() {
        val remote = CatalogCapabilities.REMOTE
        assertFalse(remote.orderIntegration)
        OrderLaunchGate.debugEnabled = false
        assertFalse(OrderLaunchGate.enabled(remote))
    }

    @Test fun anExplicitDevelopmentSwitchLetsTheRealFlowBeExercisedAndNothingElseDoes() {
        try {
            OrderLaunchGate.debugEnabled = true
            assertEquals(AppEnvironment.isDebug, OrderLaunchGate.enabled(CatalogCapabilities.REMOTE))   // honoured only in a debug build
        } finally { OrderLaunchGate.debugEnabled = false }
        assertFalse(OrderLaunchGate.enabled(CatalogCapabilities.REMOTE))
    }

    @Test fun flippingTheCapabilityLaterEnablesPlacingWithoutTouchingTheStore() {
        assertTrue(OrderLaunchGate.enabled(CatalogCapabilities.REMOTE.copy(orderIntegration = true)))
        assertTrue(OrderLaunchGate.enabled(CatalogCapabilities.MOCK))
    }
}
