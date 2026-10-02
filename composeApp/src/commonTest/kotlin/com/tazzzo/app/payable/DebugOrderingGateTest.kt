package com.tazzzo.app.payable

import com.tazzzo.app.config.debugRealOrderingRequested
import com.tazzzo.app.data.catalog.CatalogCapabilities
import com.tazzzo.app.data.order.OrderFailure
import com.tazzzo.app.data.order.OrderLaunchGate
import com.tazzzo.app.data.order.OrderState
import com.tazzzo.app.data.order.OrderStore
import com.tazzzo.app.checkout.FakeCartAccess
import com.tazzzo.app.order.FakeOrderSource
import com.tazzzo.app.order.FakePending
import com.tazzzo.app.order.FakeQuoteAccess
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertTrue

/**
 * The developer override needs a debug binary AND the explicit build-time opt-in; a release binary ignores it, and the
 * production capability stays the final authority.
 */
class DebugOrderingGateTest {
    private fun TestScope.storeWith(gate: Boolean): Pair<OrderStore, FakeOrderSource> {
        val server = FakeOrderSource()
        return OrderStore(backgroundScope, server, FakeQuoteAccess(), FakeCartAccess(), FakePending(), { true }, { gate }, {}) to server
    }

    @Test fun debugWithoutTheOptInKeepsOrderingUnavailable() = runTest {
        assertFalse(OrderLaunchGate.allows(orderIntegration = false, debugBuild = true, debugOptIn = false))
        val (store, server) = storeWith(OrderLaunchGate.allows(false, true, false))
        store.place(); runCurrent()
        assertEquals(OrderState.Failed(OrderFailure.NotLaunched), store.state.value); assertTrue(server.calls.isEmpty())
    }

    @Test fun debugWithTheExplicitOptInEnablesTheRealOrderStorePath() = runTest {
        assertTrue(OrderLaunchGate.allows(orderIntegration = false, debugBuild = true, debugOptIn = true))
        val (store, server) = storeWith(OrderLaunchGate.allows(false, true, true))
        store.place(); runCurrent()
        assertIs<OrderState.Placed>(store.state.value); assertEquals(1, server.calls.size)
    }

    @Test fun aReleaseBinaryIgnoresTheOptInEntirely() = runTest {
        assertFalse(OrderLaunchGate.allows(orderIntegration = false, debugBuild = false, debugOptIn = true))
        val (store, server) = storeWith(OrderLaunchGate.allows(false, false, true))
        store.place(); runCurrent()
        assertEquals(OrderState.Failed(OrderFailure.NotLaunched), store.state.value); assertTrue(server.calls.isEmpty())
    }

    @Test fun inReleaseOnlyTheProductionCapabilityDecides() {
        assertTrue(OrderLaunchGate.allows(orderIntegration = true, debugBuild = false, debugOptIn = false))
        assertFalse(OrderLaunchGate.allows(orderIntegration = false, debugBuild = false, debugOptIn = false))
    }

    @Test fun productionRemoteOrderingStaysOff() {
        assertFalse(CatalogCapabilities.REMOTE.orderIntegration)
        assertFalse(CatalogCapabilities.REMOTE.orderHistoryIntegration)
    }

    @Test fun theDefaultBuildHasNoOptIn() {
        assertFalse(debugRealOrderingRequested())                               // OFF unless -Ptazzzo.debugRealOrdering=true
    }
}
