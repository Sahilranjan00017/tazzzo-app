package com.tazzzo.app.payable

import com.russhwolf.settings.MapSettings
import com.tazzzo.app.analytics.AnalyticsEvents
import com.tazzzo.app.checkout.FakeAddresses
import com.tazzzo.app.checkout.FakeCartAccess
import com.tazzzo.app.checkout.FakeQuoteSource
import com.tazzzo.app.checkout.hx
import com.tazzzo.app.data.checkout.CheckoutAction
import com.tazzzo.app.data.checkout.CheckoutFailure
import com.tazzzo.app.data.checkout.CheckoutQuoteStore
import com.tazzzo.app.data.checkout.CheckoutState
import com.tazzzo.app.data.checkout.StaleReason
import com.tazzzo.app.data.checkout.view
import com.tazzzo.app.data.local.PersistentStore
import com.tazzzo.app.data.order.OrderFailure
import com.tazzzo.app.data.order.OrderState
import com.tazzzo.app.data.order.OrderStore
import com.tazzzo.app.data.order.PendingOrderStore
import com.tazzzo.app.data.order.PersistentPendingOrderStore
import com.tazzzo.app.data.order.PlaceOrderAvailability
import com.tazzzo.app.data.order.placeOrderAvailability
import com.tazzzo.app.data.order.view
import com.tazzzo.app.data.remote.ApiError
import com.tazzzo.app.data.remote.ApiException
import com.tazzzo.app.order.FakeOrderSource
import com.tazzzo.app.order.FakePending
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNotEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.TestTimeSource

/**
 * The REAL quote store and the REAL order store together (fake backends with the real rules): PAYABLE_CHANGED is a
 * definitive end of the attempt that only a NEW, customer-reviewed quote can follow, while a genuinely ambiguous answer keeps
 * the PR-08 same-quote reconciliation. The two never cross.
 */
class PayableChangedFlowTest {
    private class Flow(scope: TestScope, val pending: PendingOrderStore = FakePending()) {
        val cart = FakeCartAccess()
        val addresses = FakeAddresses()
        val quoteServer = FakeQuoteSource(cart)
        val orderServer = FakeOrderSource()
        val keys = mutableListOf<String>()
        val analytics = mutableListOf<String>()
        private var n = 0
        var launch = true
        val checkout = CheckoutQuoteStore(
            scope.backgroundScope, quoteServer, cart, addresses, { true }, {},
            newKey = { "key-${++n}-abcdefgh".also { keys += it } }, clock = TestTimeSource()
        )
        val orders = OrderStore(scope.backgroundScope, orderServer, checkout, cart, pending, { true }, { launch }, { analytics += it })
        fun quoteId() = assertIs<CheckoutState.Ready>(checkout.state.value).quote.quoteId
        fun pendingValue() = pending.load()
        fun availability() = placeOrderAvailability(launch, checkout.state.value, orders.state.value)
    }

    private fun TestScope.ready(pending: PendingOrderStore = FakePending()): Flow =
        Flow(this, pending).also { it.checkout.start(); runCurrent(); it.quoteId() }

    private fun payableChanged() = hx(409, "PAYABLE_CHANGED")

    // ---- PAYABLE_CHANGED: definitive -------------------------------------------------------------------------------------

    @Test fun payableChangedIsATypedDefinitiveFailureNeverAmbiguous() = runTest {
        val f = ready(); f.orderServer.rejectNew = payableChanged()
        f.orders.place(); runCurrent()
        assertEquals(OrderState.Failed(OrderFailure.PayableChanged), f.orders.state.value)
        assertFalse(f.orders.state.value is OrderState.Ambiguous)
        assertNotEquals(PlaceOrderAvailability.NeedsCheck, f.availability())
    }

    @Test fun payableChangedDeletesThePendingRecord() = runTest {
        val f = ready(); val q = f.quoteId(); f.orderServer.rejectNew = payableChanged()
        f.orders.place(); runCurrent()
        assertEquals(listOf(q), (f.pending as FakePending).saves)                 // it was saved before the request left...
        assertNull(f.pendingValue())                                              // ...and is gone once the answer is definitive
    }

    @Test fun payableChangedInvalidatesTheOldQuoteSoItCanNeverBePlacedAgain() = runTest {
        val f = ready(); f.orderServer.rejectNew = payableChanged()
        f.orders.place(); runCurrent()
        assertEquals(CheckoutState.Stale(StaleReason.PayableChanged), f.checkout.state.value)
        f.orders.acknowledge(); runCurrent()
        assertEquals(PlaceOrderAvailability.NoReadyQuote, f.availability())
        f.orders.place(); runCurrent()
        assertEquals(OrderState.Failed(OrderFailure.QuoteNotReady), f.orders.state.value)
        assertEquals(1, f.orderServer.calls.size)                                // the same quote is never POSTed again
    }

    @Test fun payableChangedNeverOffersCheckOrderAndCheckOrderNeverResendsTheQuote() = runTest {
        val f = ready(); f.orderServer.rejectNew = payableChanged()
        f.orders.place(); runCurrent()
        val v = OrderFailure.PayableChanged.view()
        assertFalse(CheckoutAction.CheckOrder in v.actions); assertEquals(listOf(CheckoutAction.ReviewCheckout), v.actions)
        assertEquals("Your order amount changed. Review checkout again.", v.title)
        f.orders.checkOrder(); runCurrent()
        assertEquals(1, f.orderServer.calls.size)
        assertEquals(OrderState.Failed(OrderFailure.PayableChanged), f.orders.state.value)
    }

    @Test fun nothingIsRequotedOrRetriedAutomaticallyAndTheServerCartIsUntouched() = runTest {
        val f = ready(); f.orderServer.rejectNew = payableChanged()
        val refreshesBefore = f.cart.refreshes; val cartBefore = f.cart.flow.value
        f.orders.place(); runCurrent()
        advanceTimeBy(600_000); runCurrent()
        assertEquals(1, f.quoteServer.calls.size); assertEquals(1, f.orderServer.calls.size)
        assertEquals(refreshesBefore, f.cart.refreshes); assertEquals(cartBefore, f.cart.flow.value)
    }

    @Test fun reviewCheckoutCreatesANewQuoteWithANewKeyAndStopsUntilTheCustomerPlacesAgain() = runTest {
        val f = ready(); val q1 = f.quoteId(); f.orderServer.rejectNew = payableChanged()
        f.orders.place(); runCurrent()
        f.orderServer.rejectNew = null                                            // the backend now honours a NEW quote

        val refreshesBefore = f.cart.refreshes
        f.orders.acknowledge(); f.checkout.start(); runCurrent()                 // the "Review checkout" action
        assertEquals(refreshesBefore + 1, f.cart.refreshes)                      // fresh authoritative cart first
        assertEquals(2, f.quoteServer.calls.size)
        assertEquals(f.keys[1], f.quoteServer.calls[1].key); assertNotEquals(f.keys[0], f.keys[1])
        val q2 = f.quoteId(); assertNotEquals(q1, q2)

        advanceTimeBy(60_000); runCurrent()
        assertEquals(listOf(q1), f.orderServer.calls)                            // STOP: no automatic resubmission
        assertEquals(OrderState.Idle, f.orders.state.value)
        assertEquals(PlaceOrderAvailability.Available, f.availability())

        f.orders.place(); runCurrent()                                           // the customer's explicit confirmation
        assertEquals(listOf(q1, q2), f.orderServer.calls)
        assertIs<OrderState.Placed>(f.orders.state.value)
        assertEquals(listOf(AnalyticsEvents.ORDER_PLACE_STARTED, AnalyticsEvents.ORDER_PLACE_FAILED,
            AnalyticsEvents.ORDER_PLACE_STARTED, AnalyticsEvents.ORDER_PLACE_SUCCEEDED), f.analytics)
    }

    @Test fun payableChangedFromACheckOrderReconciliationIsDefinitiveToo() = runTest {
        val f = ready(); f.orderServer.failNext(ApiException(ApiError.Timeout))
        f.orders.place(); runCurrent()
        assertIs<OrderState.Ambiguous>(f.orders.state.value)
        f.orderServer.rejectNew = payableChanged()                                // replay-first found no order, then refused
        f.orders.checkOrder(); runCurrent()
        assertEquals(OrderState.Failed(OrderFailure.PayableChanged), f.orders.state.value)
        assertNull(f.pendingValue())
        assertEquals(CheckoutState.Stale(StaleReason.PayableChanged), f.checkout.state.value)
    }

    @Test fun payableChangedAfterTheAlreadyPurchasedReconciliationIsDefinitive() = runTest {
        val f = ready(); f.orderServer.failNext(hx(409, "CART_VERSION_ALREADY_PURCHASED")); f.orderServer.rejectNew = payableChanged()
        f.orders.place(); runCurrent()
        assertEquals(2, f.orderServer.calls.size)
        assertEquals(OrderState.Failed(OrderFailure.PayableChanged), f.orders.state.value); assertNull(f.pendingValue())
    }

    @Test fun payableChangedOnTheColdStartReconciliationDeletesTheRecordAndLeavesANewerQuoteAlone() = runTest {
        val pending = FakePending().also { it.value = "CHKQ_old999" }
        val f = ready(pending); val current = f.quoteId()
        f.orderServer.rejectNew = payableChanged()
        f.orders.resumeAfterRestore(); runCurrent()
        assertEquals(listOf("CHKQ_old999"), f.orderServer.calls)
        assertEquals(OrderState.Failed(OrderFailure.PayableChanged), f.orders.state.value); assertNull(f.pendingValue())
        assertEquals(current, f.quoteId())                                        // a different, newer quote is not invalidated
    }

    // ---- AMBIGUOUS: unchanged ---------------------------------------------------------------------------------------------

    @Test fun ambiguousAnswersKeepTheSameQuoteReconciliationAndNeverInvalidateTheQuote() = runTest {
        for (e in listOf(ApiException(ApiError.Timeout), ApiException(ApiError.Network), hx(500, "INTERNAL"), hx(503, "SERVICE_UNAVAILABLE"))) {
            val f = ready(); val q = f.quoteId(); f.orderServer.failNext(e, applied = true)       // the order WAS created; the answer was lost
            f.orders.place(); runCurrent()
            assertIs<OrderState.Ambiguous>(f.orders.state.value, e.toString())
            assertEquals(q, f.pendingValue(), e.toString())                                    // the record is kept
            assertEquals(q, f.quoteId(), e.toString())                                         // the quote is NOT invalidated
            assertEquals(listOf(CheckoutAction.CheckOrder), (f.orders.state.value as OrderState.Ambiguous).failure.view().actions)
            f.orders.checkOrder(); runCurrent()
            assertEquals(listOf(q, q), f.orderServer.calls, e.toString())                     // the SAME quote, never a new one
            assertEquals(1, f.quoteServer.calls.size, e.toString())                           // no new quote, no new key
            assertIs<OrderState.Placed>(f.orders.state.value, e.toString())
        }
    }

    // ---- quotes without binding money, malformed quotes -------------------------------------------------------------------

    @Test fun aQuoteWithoutBindingMoneyIsNeverOrderedAndOnlyRefreshedIntoANewQuote() = runTest {
        val f = Flow(this); f.quoteServer.moneyFor = { null }
        f.checkout.start(); runCurrent()
        assertNull(assertIs<CheckoutState.Ready>(f.checkout.state.value).quote.money)
        assertEquals(PlaceOrderAvailability.NeedsRefresh, f.availability())
        f.orders.place(); runCurrent()
        assertEquals(OrderState.Failed(OrderFailure.QuoteNotReady), f.orders.state.value)
        assertTrue(f.orderServer.calls.isEmpty()); assertTrue((f.pending as FakePending).saves.isEmpty())
        f.quoteServer.moneyFor = { com.tazzzo.app.checkout.bindingOf(it) }
        f.orders.acknowledge(); f.checkout.start(); runCurrent()                 // "Refresh checkout"
        assertNotEquals(f.keys[0], f.keys[1])
        assertEquals(PlaceOrderAvailability.Available, f.availability())
    }

    @Test fun aMalformedQuoteIsAContractFailureWithNoSameKeyRetryAndNoAutomaticLoop() = runTest {
        val f = Flow(this)
        f.quoteServer.failNext(ApiException(ApiError.Decoding()), applied = true)   // the server stored a quote we cannot accept
        f.checkout.start(); runCurrent()
        val failed = assertIs<CheckoutState.Failed>(f.checkout.state.value)
        assertEquals(CheckoutFailure.ContractViolation, failed.failure); assertFalse(failed.canRetrySameKey)
        assertEquals(listOf(CheckoutAction.RefreshCheckout), failed.failure.view(failed.canRetrySameKey).actions)
        assertEquals(PlaceOrderAvailability.NoReadyQuote, f.availability())
        advanceTimeBy(600_000); runCurrent()
        assertEquals(1, f.quoteServer.calls.size)                                 // nothing loops on its own

        f.checkout.retry(); runCurrent()                                         // even a "retry" is a NEW attempt here
        assertEquals(2, f.quoteServer.calls.size)
        assertNotEquals(f.quoteServer.calls[0].key, f.quoteServer.calls[1].key)
        assertIs<CheckoutState.Ready>(f.checkout.state.value)
    }

    @Test fun aQuoteForACartWeDidNotAskForIsAContractFailure() = runTest {
        val f = Flow(this); f.quoteServer.tamperCartVersion = true
        f.checkout.start(); runCurrent()
        assertEquals(CheckoutState.Failed(CheckoutFailure.ContractViolation, false), f.checkout.state.value)
    }

    @Test fun staleAndExpiredQuotesAreRefused() = runTest {
        val f = ready()
        f.checkout.invalidateNow(f.quoteId(), StaleReason.PayableChanged)
        f.orders.place(); runCurrent()
        assertEquals(OrderState.Failed(OrderFailure.QuoteNotReady), f.orders.state.value)
        assertEquals(PlaceOrderAvailability.NoReadyQuote, placeOrderAvailability(true, CheckoutState.Expired, OrderState.Idle))
        assertTrue(f.orderServer.calls.isEmpty())
    }

    // ---- privacy -----------------------------------------------------------------------------------------------------------

    @Test fun noMoneyIsEverPersistedOnlyTheOpaqueQuoteId() = runTest {
        val settings = MapSettings()
        val f = ready(PersistentPendingOrderStore(PersistentStore(settings)))
        f.orderServer.failNext(ApiException(ApiError.Timeout))
        f.orders.place(); runCurrent()                                            // Ambiguous: the record exists now
        val stored = settings.keys.joinToString { k -> k + "=" + settings.getStringOrNull(k) }
        assertTrue(f.quoteId() in stored)
        for (d in listOf("9900", "99.00", "₹", "payable", "subtotal", "discount", "money")) assertFalse(d in stored.lowercase(), d)
        f.orderServer.rejectNew = payableChanged()
        f.orders.checkOrder(); runCurrent()
        assertTrue(settings.keys.isEmpty() || settings.keys.all { settings.getStringOrNull(it).isNullOrEmpty() })
    }

    @Test fun analyticsCarriesOutcomeEventNamesOnly() = runTest {
        val f = ready(); f.orderServer.rejectNew = payableChanged()
        f.orders.place(); runCurrent()
        assertEquals(listOf(AnalyticsEvents.ORDER_PLACE_STARTED, AnalyticsEvents.ORDER_PLACE_FAILED), f.analytics)
        for (e in f.analytics) for (d in listOf("9900", "₹", "CHKQ", "payable")) assertFalse(d in e, d)
    }
}
