package com.tazzzo.app.order

import com.russhwolf.settings.MapSettings
import com.tazzzo.app.checkout.FakeCartAccess
import com.tazzzo.app.checkout.hx
import com.tazzzo.app.cart.cartOf
import com.tazzzo.app.cart.line
import com.tazzzo.app.data.cart.CartState
import com.tazzzo.app.data.checkout.CheckoutState
import com.tazzzo.app.data.checkout.StaleReason
import com.tazzzo.app.data.local.PersistentStore
import com.tazzzo.app.data.order.OrderFailure
import com.tazzzo.app.data.order.OrderState
import com.tazzzo.app.data.order.OrderStore
import com.tazzzo.app.data.order.PersistentPendingOrderStore
import com.tazzzo.app.data.remote.ApiError
import com.tazzzo.app.data.remote.ApiException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue

class OrderStoreTest {
    private class Rig(
        scope: TestScope,
        val server: FakeOrderSource = FakeOrderSource(),
        val pending: com.tazzzo.app.data.order.PendingOrderStore = FakePending(),
        val quotes: FakeQuoteAccess = FakeQuoteAccess(),
        val cart: FakeCartAccess = FakeCartAccess(),
        var authed: Boolean = true,
        var launch: Boolean = true
    ) {
        val events = mutableListOf<String>()
        val analytics = mutableListOf<String>()
        val store = OrderStore(scope.backgroundScope, server, quotes, cart, pending, { authed }, { launch }, { analytics += it })
        init {
            quotes.onReset = { events += "reset" }
            cart.onRefreshHook = { events += "refresh" }
        }
        fun state() = store.state.value
    }

    private fun TestScope.rig() = Rig(this)
    private fun TestScope.placed(): Rig = rig().also { it.store.place(); runCurrent() }

    // ---- placing -----------------------------------------------------------------------------------------------------

    @Test fun placeSendsExactlyOnePostForTheReadyQuoteAndEndsPlaced() = runTest {
        val r = placed()
        assertEquals(listOf("CHKQ_abc123"), r.server.calls)
        val s = assertIs<OrderState.Placed>(r.state())
        assertEquals(s.order, r.server.orders["CHKQ_abc123"]); assertEquals(s.order, r.store.recent.value)
    }

    @Test fun rapidTapsSendOnePost() = runTest {
        val r = rig(); val gate = CompletableDeferred<Unit>(); r.server.gate = gate
        repeat(6) { r.store.place() }; runCurrent()
        assertEquals(OrderState.Placing, r.state()); assertEquals(1, r.server.calls.size)
        gate.complete(Unit); runCurrent()
        assertEquals(1, r.server.calls.size); assertEquals(1, r.server.ordersCreated())
    }

    @Test fun placeWhilePlacingAmbiguousOrPlacedDoesNothing() = runTest {
        val r = rig(); r.server.failNext(ApiException(ApiError.Timeout))
        r.store.place(); runCurrent()
        assertIs<OrderState.Ambiguous>(r.state())
        r.store.place(); runCurrent()
        assertEquals(1, r.server.calls.size)                              // an unresolved attempt blocks a second one
        val r2 = placed(); r2.store.place(); runCurrent()
        assertEquals(1, r2.server.calls.size)                             // and so does a placed order, until acknowledged
    }

    @Test fun aQuoteThatIsNotReadyIsNeverOrderedFrom() = runTest {
        for (q in listOf(CheckoutState.Idle, CheckoutState.Creating, CheckoutState.Expired, CheckoutState.Stale(StaleReason.CartChanged),
            CheckoutState.Stale(StaleReason.AddressChanged), CheckoutState.SignedOut, CheckoutState.Failed(com.tazzzo.app.data.checkout.CheckoutFailure.Timeout, true))) {
            val r = rig(); r.quotes.flow.value = q
            r.store.place(); runCurrent()
            assertEquals(OrderState.Failed(OrderFailure.QuoteNotReady), r.state(), q.toString())
            assertTrue(r.server.calls.isEmpty()); assertTrue((r.pending as FakePending).saves.isEmpty())
        }
    }

    @Test fun anUnauthenticatedCustomerNeverPosts() = runTest {
        val r = rig(); r.authed = false
        r.store.place(); runCurrent()
        assertEquals(OrderState.Failed(OrderFailure.Unauthenticated), r.state()); assertTrue(r.server.calls.isEmpty())
    }

    @Test fun whenProductionPlacementIsNotLaunchEnabledNothingIsSentOrPersisted() = runTest {
        val r = rig(); r.launch = false
        r.store.place(); runCurrent()
        assertEquals(OrderState.Failed(OrderFailure.NotLaunched), r.state())
        assertTrue(r.server.calls.isEmpty()); assertTrue((r.pending as FakePending).saves.isEmpty())
        r.launch = true; r.store.acknowledge(); runCurrent(); r.store.place(); runCurrent()   // flipping the gate later needs no store change
        assertIs<OrderState.Placed>(r.state())
    }

    @Test fun theRecoveryRecordIsWrittenBeforeTheRequestLeaves() = runTest {
        val r = rig(); val gate = CompletableDeferred<Unit>(); r.server.gate = gate
        r.store.place(); runCurrent()
        assertEquals("CHKQ_abc123", (r.pending as FakePending).value)
        gate.complete(Unit); runCurrent()
        assertNull(r.pending.value)
    }

    @Test fun aSpentQuoteIsResetBeforeTheServerCartIsReadAndNothingIsClearedLocally() = runTest {
        val r = rig()
        r.cart.onRefresh = { CartState.Loaded(cartOf(6)) }               // the backend emptied the cart and advanced the version
        r.store.place(); runCurrent()
        assertEquals(listOf("reset", "refresh"), r.events)
        assertEquals(1, r.quotes.resets); assertEquals(1, r.cart.refreshes)
        assertTrue((r.cart.flow.value as CartState.Loaded).cart.isEmpty)
        assertNull((r.pending as FakePending).value)
    }

    @Test fun aCartTheBackendPreservedBecauseItChangedAfterTheQuoteIsAcceptedAsIs() = runTest {
        val r = rig()
        val changed = cartOf(9, line("TZP-7", 3, unit = 20))
        r.cart.onRefresh = { CartState.Loaded(changed) }
        r.store.place(); runCurrent()
        assertEquals(changed, (r.cart.flow.value as CartState.Loaded).cart)    // server truth, untouched by the order flow
    }

    // ---- ambiguous / reconciliation ---------------------------------------------------------------------------------

    @Test fun everyAmbiguousFailureKeepsTheRecordAndIsNeverRetriedAutomatically() = runTest {
        for (e in listOf(ApiException(ApiError.Timeout), ApiException(ApiError.Network), hx(500, "INTERNAL"), hx(503, "SERVICE_UNAVAILABLE"), ApiException(ApiError.Decoding()))) {
            val r = rig(); r.server.failNext(e)
            r.store.place(); runCurrent(); advanceTimeBy(600_000); runCurrent()
            assertIs<OrderState.Ambiguous>(r.state(), e.toString())
            assertEquals(1, r.server.calls.size); assertEquals("CHKQ_abc123", (r.pending as FakePending).value)
            assertEquals(0, r.quotes.resets)                               // no new quote, nothing reset
        }
    }

    @Test fun checkOrderRePostsTheSameQuoteAndGetsTheOriginalOrderBack() = runTest {
        val r = rig(); r.server.failNext(ApiException(ApiError.Timeout), applied = true)     // the order WAS created; the response was lost
        r.store.place(); runCurrent()
        r.store.checkOrder(); runCurrent()
        assertEquals(listOf("CHKQ_abc123", "CHKQ_abc123"), r.server.calls)
        assertEquals(1, r.server.ordersCreated()); assertIs<OrderState.Placed>(r.state())
        assertNull((r.pending as FakePending).value)
    }

    @Test fun checkOrderWhenNothingWasCreatedCreatesTheOrderOnce() = runTest {
        val r = rig(); r.server.failNext(ApiException(ApiError.Network), applied = false)
        r.store.place(); runCurrent(); r.store.checkOrder(); runCurrent()
        assertEquals(1, r.server.ordersCreated()); assertIs<OrderState.Placed>(r.state())
    }

    @Test fun checkOrderOutsideAnAmbiguousStateDoesNothing() = runTest {
        val r = rig(); r.store.checkOrder(); runCurrent()
        assertTrue(r.server.calls.isEmpty())
    }

    @Test fun aReplayAfterTheQuoteExpiredStillReturnsTheOrder() = runTest {
        val r = rig(); r.server.failNext(ApiException(ApiError.Timeout), applied = true)
        r.store.place(); runCurrent()
        r.server.rejectNew = hx(410, "QUOTE_EXPIRED")                      // the quote has since expired: a NEW placement would be refused
        r.store.checkOrder(); runCurrent()
        assertIs<OrderState.Placed>(r.state())
    }

    @Test fun anAmbiguousRetryThatIsDefinitivelyRejectedProvesNoOrderExists() = runTest {
        val r = rig(); r.server.failNext(ApiException(ApiError.Timeout), applied = false)
        r.store.place(); runCurrent()
        r.server.rejectNew = hx(410, "QUOTE_EXPIRED")
        r.store.checkOrder(); runCurrent()
        assertEquals(OrderState.Failed(OrderFailure.QuoteExpired), r.state()); assertNull((r.pending as FakePending).value)
        assertEquals(0, r.server.ordersCreated())
    }

    // ---- definitive errors ------------------------------------------------------------------------------------------

    @Test fun everyDefinitiveBackendCodeIsAConclusiveFailureThatClearsTheRecord() = runTest {
        val table = listOf(
            hx(410, "QUOTE_EXPIRED") to OrderFailure.QuoteExpired, hx(404, "NOT_FOUND") to OrderFailure.NotFound,
            hx(409, "ADDRESS_CHANGED") to OrderFailure.AddressChanged, hx(409, "NOT_SERVICEABLE") to OrderFailure.NotServiceable,
            hx(409, "PRICE_CHANGED") to OrderFailure.PriceChanged, hx(409, "PRODUCT_UNAVAILABLE") to OrderFailure.ProductUnavailable,
            hx(409, "STOCK_UNAVAILABLE") to OrderFailure.StockUnavailable, hx(409, "RESERVATION_EXPIRED") to OrderFailure.ReservationExpired,
            hx(400, "PAYMENT_METHOD_UNSUPPORTED") to OrderFailure.ClientBug, hx(400, "INVALID_REQUEST") to OrderFailure.ClientBug,
            hx(415, "UNSUPPORTED_MEDIA_TYPE") to OrderFailure.ClientBug
        )
        for ((e, f) in table) {
            val r = rig(); r.server.rejectNew = e
            r.store.place(); runCurrent()
            assertEquals(OrderState.Failed(f), r.state(), f.toString())
            assertNull((r.pending as FakePending).value); assertEquals(1, r.server.calls.size); assertEquals(0, r.quotes.resets)
        }
    }

    @Test fun cartVersionAlreadyPurchasedReconcilesTheSameQuoteOnceBeforeCallingItAFailure() = runTest {
        val r = rig(); r.server.failNext(hx(409, "CART_VERSION_ALREADY_PURCHASED"), applied = true)   // this quote DID buy the cart
        r.store.place(); runCurrent()
        assertEquals(listOf("CHKQ_abc123", "CHKQ_abc123"), r.server.calls)
        assertIs<OrderState.Placed>(r.state()); assertEquals(1, r.server.ordersCreated())
    }

    @Test fun cartVersionAlreadyPurchasedThatReconcilesToNothingIsAFailureAfterExactlyOneExtraPost() = runTest {
        val r = rig(); r.server.rejectNew = hx(409, "CART_VERSION_ALREADY_PURCHASED")
        r.store.place(); runCurrent()
        assertEquals(2, r.server.calls.size)
        assertEquals(OrderState.Failed(OrderFailure.CartAlreadyPurchased), r.state()); assertNull((r.pending as FakePending).value)
    }

    @Test fun anUnrecognized409CodeIsTreatedAsAmbiguousNotAsAConclusiveFailure() = runTest {
        val r = rig(); r.server.rejectNew = hx(409, "SOMETHING_NEW")
        r.store.place(); runCurrent()
        assertIs<OrderState.Ambiguous>(r.state()); assertEquals("CHKQ_abc123", (r.pending as FakePending).value)
    }

    @Test fun a401EndsTheSessionAndDeletesTheRecord() = runTest {
        val r = rig(); r.server.rejectNew = hx(401, "UNAUTHENTICATED")
        r.store.place(); runCurrent()
        assertEquals(OrderState.SignedOut, r.state()); assertNull((r.pending as FakePending).value)
    }

    // ---- process death ------------------------------------------------------------------------------------------------

    private class Launch(val server: FakeOrderSource, val pending: com.tazzzo.app.data.order.PendingOrderStore, scope: TestScope, authed: Boolean = true) {
        val quotes = FakeQuoteAccess(CheckoutState.Idle)
        val cart = FakeCartAccess()
        val store = OrderStore(scope.backgroundScope, server, quotes, cart, pending, { authed }, { true }, {})
    }

    @Test fun processDeathAfterTheOrderWasCommittedIsRecoveredByExactlyOneAutomaticReconciliation() = runTest {
        val settings = MapSettings(); val persisted = PersistentPendingOrderStore(PersistentStore(settings))
        val server = FakeOrderSource()
        // launch 1: the POST commits the order, the response never arrives, the process dies.
        val gate = CompletableDeferred<Unit>(); server.gate = gate
        server.failNext(ApiException(ApiError.Timeout), applied = true)
        val first = Launch(server, persisted, this); first.quotes.flow.value = readyState()
        first.store.place(); runCurrent()
        assertEquals("CHKQ_abc123", persisted.load())                      // the handle survives; the process does not
        gate.complete(Unit); runCurrent()                                  // (the dead process's own result is irrelevant)
        // launch 2: a fresh process, same storage, authenticated session restored.
        val second = Launch(server, PersistentPendingOrderStore(PersistentStore(settings)), this)
        val before = server.calls.size
        second.store.resumeAfterRestore(); runCurrent()
        assertEquals(before + 1, server.calls.size)                        // exactly ONE reconciliation POST
        assertEquals("CHKQ_abc123", server.calls.last())
        assertIs<OrderState.Placed>(second.store.state.value); assertEquals(1, server.ordersCreated())
        assertNull(PersistentStore(settings).loadPendingOrderQuote())      // deleted
        assertEquals(1, second.cart.refreshes)                             // server cart truth
        assertEquals(1, second.quotes.resets)
    }

    @Test fun ifTheLaunchReconciliationIsAlsoAmbiguousThereIsNoLoopTheRecordStaysAndCheckOrderIsOffered() = runTest {
        val settings = MapSettings(); val persisted = PersistentPendingOrderStore(PersistentStore(settings)); persisted.save("CHKQ_abc123")
        val server = FakeOrderSource(); server.failNext(hx(503, "SERVICE_UNAVAILABLE"))
        val l = Launch(server, persisted, this)
        l.store.resumeAfterRestore(); runCurrent(); advanceTimeBy(600_000); runCurrent()
        assertEquals(1, server.calls.size); assertIs<OrderState.Ambiguous>(l.store.state.value)
        assertEquals("CHKQ_abc123", persisted.load())
        l.store.resumeAfterRestore(); runCurrent()                         // a second call in the same launch is ignored
        assertEquals(1, server.calls.size)
        l.store.checkOrder(); runCurrent()                                 // the explicit action works
        assertIs<OrderState.Placed>(l.store.state.value)
    }

    @Test fun aNewLaunchGetsItsOwnSingleAutomaticAttempt() = runTest {
        val settings = MapSettings(); val server = FakeOrderSource()
        PersistentPendingOrderStore(PersistentStore(settings)).save("CHKQ_abc123"); server.failNext(ApiException(ApiError.Network))
        val a = Launch(server, PersistentPendingOrderStore(PersistentStore(settings)), this); a.store.resumeAfterRestore(); runCurrent()
        val b = Launch(server, PersistentPendingOrderStore(PersistentStore(settings)), this); b.store.resumeAfterRestore(); runCurrent()
        assertEquals(2, server.calls.size)
    }

    @Test fun resumeWithoutARecordOrWithoutASessionNeverPostsAndAnUnauthenticatedLaunchDeletesTheRecord() = runTest {
        val server = FakeOrderSource()
        val none = Launch(server, FakePending(), this); none.store.resumeAfterRestore(); runCurrent()
        assertTrue(server.calls.isEmpty())
        val p = FakePending().apply { value = "CHKQ_abc123" }
        val signedOut = Launch(server, p, this, authed = false); signedOut.store.resumeAfterRestore(); runCurrent()
        assertTrue(server.calls.isEmpty()); assertNull(p.value)
    }

    @Test fun theAutomaticReconciliationRunsAtMostOncePerLaunchEvenIfARecordReappearsInTheIdleState() = runTest {
        val p = FakePending().apply { value = "CHKQ_abc123" }; val server = FakeOrderSource(); server.rejectNew = hx(410, "QUOTE_EXPIRED")
        val l = Launch(server, p, this); l.store.resumeAfterRestore(); runCurrent()
        l.store.acknowledge(); runCurrent()
        assertEquals(OrderState.Idle, l.store.state.value)
        p.value = "CHKQ_other99"                                           // an Idle store with a record: the launch budget is already spent
        l.store.resumeAfterRestore(); runCurrent()
        assertEquals(1, server.calls.size)
    }

    @Test fun checkOrderAlwaysUsesTheOriginalAttemptNeverWhateverQuoteIsReadyNow() = runTest {
        val r = rig(); r.server.failNext(ApiException(ApiError.Timeout), applied = true)
        r.store.place(); runCurrent()
        r.quotes.flow.value = readyState("CHKQ_other99")                   // the quote store has since moved on to a different quote
        r.store.checkOrder(); runCurrent()
        assertEquals(listOf("CHKQ_abc123", "CHKQ_abc123"), r.server.calls)  // still the SAME quote: never a new intent
        assertEquals(1, r.server.ordersCreated())
    }

    @Test fun aDefinitiveAnswerOnLaunchAlsoDeletesTheRecord() = runTest {
        val p = FakePending().apply { value = "CHKQ_abc123" }; val server = FakeOrderSource(); server.rejectNew = hx(410, "QUOTE_EXPIRED")
        val l = Launch(server, p, this); l.store.resumeAfterRestore(); runCurrent()
        assertEquals(OrderState.Failed(OrderFailure.QuoteExpired), l.store.state.value); assertNull(p.value)
    }

    // ---- logout ------------------------------------------------------------------------------------------------------

    @Test fun logoutWithAnUnresolvedAttemptNeverPostsAndDeletesTheRecordSoAnotherCustomerCannotReconcileIt() = runTest {
        val r = rig(); r.server.failNext(ApiException(ApiError.Timeout))
        r.store.place(); runCurrent()
        val calls = r.server.calls.size
        r.store.signOut(); runCurrent()
        assertEquals(OrderState.SignedOut, r.state()); assertEquals(calls, r.server.calls.size)
        assertNull((r.pending as FakePending).value); assertNull(r.store.recent.value)
        r.store.onSignedIn(); runCurrent()
        assertEquals(OrderState.Idle, r.state())
        r.store.resumeAfterRestore(); runCurrent()                         // nothing left to reconcile for the next customer
        assertEquals(calls, r.server.calls.size)
    }

    @Test fun aResponseThatArrivesAfterLogoutIsDiscarded() = runTest {
        val r = rig(); val gate = CompletableDeferred<Unit>(); r.server.gate = gate
        r.store.place(); runCurrent(); r.store.signOut(); runCurrent(); gate.complete(Unit); runCurrent()
        assertEquals(OrderState.SignedOut, r.state()); assertNull(r.store.recent.value); assertEquals(0, r.quotes.resets)
    }

    @Test fun acknowledgeEndsAPlacedOrConclusiveStateButNotAnUnresolvedOne() = runTest {
        val r = placed(); r.store.acknowledge(); runCurrent()
        assertEquals(OrderState.Idle, r.state()); assertNotNullRecent(r)
        val a = rig(); a.server.failNext(ApiException(ApiError.Timeout)); a.store.place(); runCurrent(); a.store.acknowledge(); runCurrent()
        assertIs<OrderState.Ambiguous>(a.state())
    }

    private fun assertNotNullRecent(r: Rig) = assertTrue(r.store.recent.value != null)

    // ---- detail / analytics / privacy --------------------------------------------------------------------------------

    @Test fun thisSessionsOrderIsReadWithoutANetworkCallAndAnotherIdIsFetchedByGet() = runTest {
        val r = placed(); val id = (r.state() as OrderState.Placed).order.orderId
        assertEquals(id, r.store.fetch(id)!!.orderId); assertTrue(r.server.gets.isEmpty())
        assertNull(r.store.fetch("ORD_unknown1")); assertEquals(listOf("ORD_unknown1"), r.server.gets)
    }

    @Test fun analyticsAreOutcomeOnlyEventNamesWithNoPayloadAtAll() = runTest {
        val ok = placed()
        assertEquals(listOf("order_place_started", "order_place_succeeded"), ok.analytics)
        val bad = rig(); bad.server.rejectNew = hx(409, "PRICE_CHANGED"); bad.store.place(); runCurrent()
        assertEquals(listOf("order_place_started", "order_place_failed"), bad.analytics)
        for (e in ok.analytics + bad.analytics) for (secret in listOf("ORD_", "CHKQ_", "TZP-", "99", "4950")) assertFalse(secret in e)
    }

    @Test fun onlyTheOpaqueQuoteIdIsEverPersistedAndOnlyWhileUnresolved() = runTest {
        val settings = MapSettings()
        val pending = PersistentPendingOrderStore(PersistentStore(settings))
        val r = Rig(this, pending = pending); val gate = CompletableDeferred<Unit>(); r.server.gate = gate
        r.store.place(); runCurrent()
        assertEquals(setOf("tazzzo.pending-order.v1"), settings.keys)
        val raw = settings.getString("tazzzo.pending-order.v1", "")
        assertEquals("""{"v":1,"quoteId":"CHKQ_abc123"}""", raw.replace(" ", ""))
        for (secret in listOf("ORD_", "TZP-", "ADDR_", "Asha", "98765", "14th", "560102", "4950", "9900", "COD", "Bearer")) assertFalse(secret in raw, secret)
        gate.complete(Unit); runCurrent()
        assertTrue(settings.keys.isEmpty())                                // resolved: nothing is retained
    }

    @Test fun anUnreadableOrUnknownVersionRecordIsIgnoredAndRemoved() {
        val settings = MapSettings(); val store = PersistentStore(settings)
        for (bad in listOf("garbage", """{"v":2,"quoteId":"CHKQ_abc123"}""", """{"v":1,"quoteId":"../x"}""")) {
            settings.putString("tazzzo.pending-order.v1", bad)
            assertNull(store.loadPendingOrderQuote(), bad); assertTrue(settings.keys.isEmpty())
        }
    }

    @Test fun statesNeverPrintOrderContents() = runTest {
        val r = placed()
        val text = r.state().toString() + r.store.recent.value.toString() + orderOf().items.single().toString() + orderOf().deliveryAddress.toString()
        for (secret in listOf("ORD_", "TZP-", "Asha", "98765", "14th Main", "560102", "Atta")) assertFalse(secret in text, secret)
    }
}
