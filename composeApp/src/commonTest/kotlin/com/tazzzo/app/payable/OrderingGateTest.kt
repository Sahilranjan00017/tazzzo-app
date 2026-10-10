package com.tazzzo.app.payable

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
 * The release path IS the real order path: the gate is the `orderIntegration` capability alone (true for REMOTE and MOCK).
 * There is no debug-only opt-in any more. A capability set with it false still fails closed before any request.
 *
 * Mutation notes: REMOTE `orderIntegration = false` fails [releaseRemoteOrdersForReal]; a gate that ignores the capability
 * fails [aCapabilityKillSwitchStillFailsClosedWithoutARequest].
 */
class OrderingGateTest {
    private fun TestScope.storeWith(gate: Boolean): Pair<OrderStore, FakeOrderSource> {
        val server = FakeOrderSource()
        return OrderStore(backgroundScope, server, FakeQuoteAccess(), FakeCartAccess(), FakePending(), { true }, { gate }, {}) to server
    }

    @Test fun releaseRemoteOrdersForReal() = runTest {
        assertTrue(CatalogCapabilities.REMOTE.orderIntegration)
        assertTrue(OrderLaunchGate.enabled(CatalogCapabilities.REMOTE))
        val (store, server) = storeWith(OrderLaunchGate.enabled(CatalogCapabilities.REMOTE))
        store.place(); runCurrent()
        assertIs<OrderState.Placed>(store.state.value); assertEquals(listOf("CHKQ_abc123"), server.calls)
    }

    @Test fun aCapabilityKillSwitchStillFailsClosedWithoutARequest() = runTest {
        val off = CatalogCapabilities.REMOTE.copy(orderIntegration = false)
        assertFalse(OrderLaunchGate.enabled(off))
        val (store, server) = storeWith(OrderLaunchGate.enabled(off))
        store.place(); runCurrent()
        assertEquals(OrderState.Failed(OrderFailure.NotLaunched), store.state.value); assertTrue(server.calls.isEmpty())
    }

    @Test fun mockKeepsOrdering() { assertTrue(OrderLaunchGate.enabled(CatalogCapabilities.MOCK)) }
}
