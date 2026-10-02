package com.tazzzo.app.cart

import com.tazzzo.app.address.http
import com.tazzzo.app.data.cart.CartAction
import com.tazzzo.app.data.cart.CartNotice
import com.tazzzo.app.data.cart.toView
import com.tazzzo.app.data.cart.CartState
import com.tazzzo.app.data.cart.CartStore
import com.tazzzo.app.data.cart.PendingTarget
import com.tazzzo.app.data.remote.ApiError
import com.tazzzo.app.data.remote.ApiException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue

class CartStoreTest {
    private class Rig(scope: CoroutineScope, val src: FakeCartSource = FakeCartSource()) {
        var authed = true
        var address: String? = null
        var suspect = 0
        val store = CartStore(scope, src, { authed }, { address }, { suspect++ })
        fun loaded() = (store.state.value as CartState.Loaded).cart
    }

    private fun TestScope.rig(src: FakeCartSource = FakeCartSource()) = Rig(backgroundScope, src)
    private fun TestScope.opened(src: FakeCartSource = FakeCartSource()): Rig = rig(src).also { it.store.load(); runCurrent() }

    // ---- loading ---------------------------------------------------------------------------------------------------

    @Test fun loadHoldsTheServersCartExactly() = runTest {
        val src = FakeCartSource().apply { lines["TZP-1"] = line("TZP-1", 2); version = 4 }
        val r = opened(src)
        assertEquals(4, r.loaded().version); assertEquals(2, r.loaded().quantityOf("TZP-1"))
    }

    @Test fun signedOutNeverCallsTheBackend() = runTest {
        val r = rig(); r.authed = false
        r.store.load(); runCurrent()
        assertEquals(CartState.SignedOut, r.store.state.value)
        r.store.increment("TZP-1"); runCurrent()
        assertEquals(CartNotice.AuthRequired, r.store.notice.value)
        assertTrue(r.src.calls.isEmpty())
    }

    @Test fun aLoadFailureIsShownAndRefreshRecovers() = runTest {
        val src = FakeCartSource().apply { getError = ApiException(ApiError.Network) }
        val r = opened(src)
        assertIs<CartState.Failed>(r.store.state.value)
        src.getError = null; r.store.refresh(); runCurrent()
        assertIs<CartState.Loaded>(r.store.state.value)
    }

    @Test fun everyGetReplacesTheHeldVersion() = runTest {
        val src = FakeCartSource(); val r = opened(src)
        src.externalChange(); src.externalChange()
        r.store.refresh(); runCurrent()
        assertEquals(2, r.loaded().version)
    }

    // ---- mutations ---------------------------------------------------------------------------------------------------

    @Test fun addSendsAnAbsoluteQuantityWithTheServersVersion() = runTest {
        val src = FakeCartSource().apply { version = 7 }; val r = opened(src)
        r.store.increment("TZP-1"); runCurrent()
        assertEquals(listOf("PUT TZP-1=1"), src.calls.filter { it != "GET" })
        assertEquals(listOf(7L), src.versionsSeen)
        assertEquals(8, r.loaded().version)
    }

    @Test fun theVersionIsTakenFromTheResponseNeverIncrementedLocally() = runTest {
        val src = FakeCartSource().apply { version = 3 }; val r = opened(src)
        r.store.increment("TZP-1"); runCurrent()
        src.version += 5                           // the server moved on by itself (e.g. expiry housekeeping on a GET elsewhere)
        r.store.refresh(); runCurrent()
        r.store.increment("TZP-1"); runCurrent()
        assertEquals(listOf(3L, 9L), src.versionsSeen)
    }

    @Test fun manyTapsBeforeTheWorkerRunsAreOneRequest() = runTest {
        val src = FakeCartSource(); val r = opened(src)
        repeat(5) { r.store.increment("TZP-1") }; runCurrent()
        assertEquals(listOf("PUT TZP-1=5"), src.calls.filter { it != "GET" })
    }

    @Test fun tapsWhileARequestIsInFlightCoalesceIntoOneFollowUp() = runTest {
        val src = FakeCartSource(); val r = opened(src)
        val gate = CompletableDeferred<Unit>(); src.gate = gate
        r.store.increment("TZP-1"); runCurrent()                       // PUT 1 now in flight
        repeat(4) { r.store.increment("TZP-1") }; runCurrent()
        assertEquals(PendingTarget.Quantity(5), r.store.pending.value["TZP-1"])
        src.gate = null; gate.complete(Unit); runCurrent()
        assertEquals(listOf("PUT TZP-1=1", "PUT TZP-1=5"), src.calls.filter { it != "GET" })
        assertEquals(5, r.loaded().quantityOf("TZP-1")); assertTrue(r.store.pending.value.isEmpty())
    }

    @Test fun onlyOneMutationIsEverOnTheWire() = runTest {
        val src = FakeCartSource(); val r = opened(src)
        val gate = CompletableDeferred<Unit>(); src.gate = gate
        r.store.increment("TZP-1"); r.store.increment("TZP-2"); r.store.increment("TZP-3"); runCurrent()
        assertEquals(1, src.mutations)
        src.gate = null; gate.complete(Unit); runCurrent()
        assertEquals(3, src.mutations)
    }

    @Test fun differentSkusChainTheVersionFromEachResponse() = runTest {
        val src = FakeCartSource().apply { version = 10 }; val r = opened(src)
        r.store.increment("TZP-1"); r.store.increment("TZP-2"); runCurrent()
        assertEquals(listOf(10L, 11L), src.versionsSeen)
    }

    @Test fun aRemoveReplacesAQueuedQuantitySoNoStalePutFollowsIt() = runTest {
        val src = FakeCartSource().apply { lines["TZP-1"] = line("TZP-1", 2); version = 1 }; val r = opened(src)
        val gate = CompletableDeferred<Unit>(); src.gate = gate
        r.store.increment("TZP-2"); runCurrent()                       // occupy the wire with another SKU
        r.store.setQuantity("TZP-1", 4); r.store.remove("TZP-1"); runCurrent()
        src.gate = null; gate.complete(Unit); runCurrent()
        assertTrue(src.calls.none { it == "PUT TZP-1=4" })
        assertTrue("DELETE TZP-1" in src.calls)
        assertEquals(0, r.loaded().quantityOf("TZP-1"))
    }

    @Test fun decrementToZeroIsADeleteNeverAPutOfZero() = runTest {
        val src = FakeCartSource().apply { lines["TZP-1"] = line("TZP-1", 1); version = 1 }; val r = opened(src)
        r.store.decrement("TZP-1"); runCurrent()
        assertEquals(listOf("DELETE TZP-1"), src.calls.filter { it != "GET" })
    }

    @Test fun removingALineThatIsAlreadyGoneSendsNothing() = runTest {
        val src = FakeCartSource(); val r = opened(src)
        r.store.remove("TZP-1"); runCurrent()
        assertEquals(0, src.mutations)
    }

    @Test fun aQuantityAlreadyOnTheServerSendsNothing() = runTest {
        val src = FakeCartSource().apply { lines["TZP-1"] = line("TZP-1", 3); version = 1 }; val r = opened(src)
        r.store.setQuantity("TZP-1", 3); runCurrent()
        assertEquals(0, src.mutations)
    }

    @Test fun anInvalidSkuIsNeverSent() = runTest {
        val src = FakeCartSource(); val r = opened(src)
        r.store.increment("../x"); r.store.increment("TZP-1/../2"); r.store.increment(""); runCurrent()
        assertEquals(0, src.mutations)
    }

    @Test fun clearIsOneDeleteOfTheWholeCart() = runTest {
        val src = FakeCartSource().apply { lines["TZP-1"] = line("TZP-1", 2); version = 2 }; val r = opened(src)
        r.store.clear(); runCurrent()
        assertEquals(listOf("DELETE ALL"), src.calls.filter { it != "GET" })
        assertTrue(r.loaded().isEmpty)
    }

    @Test fun theSelectedAddressIdIsTheOnlyLocationAndIsOmittedWhenThereIsNone() = runTest {
        val src = FakeCartSource(); val r = opened(src)
        r.store.increment("TZP-1"); runCurrent()
        r.address = "ADDR_abcdef1"
        r.store.increment("TZP-2"); runCurrent()
        assertEquals(null, src.addressIds[src.calls.indexOf("PUT TZP-1=1")])
        assertEquals("ADDR_abcdef1", src.addressIds[src.calls.indexOf("PUT TZP-2=1")])
    }

    // ---- caps -------------------------------------------------------------------------------------------------------

    @Test fun incrementPastAKnownInventoryMaxIsRefusedWithoutARequest() = runTest {
        val src = FakeCartSource().apply { lines["TZP-1"] = line("TZP-1", 3, max = 3); version = 1 }; val r = opened(src)
        r.store.increment("TZP-1"); runCurrent()
        assertEquals(CartNotice.MaxReached, r.store.notice.value); assertEquals(0, src.mutations)
    }

    @Test fun anUnknownAvailabilityLineDoesNotUseItsMaxAsALimit() = runTest {
        val src = FakeCartSource().apply { lines["TZP-1"] = line("TZP-1", 3, max = 3, serviceable = null); version = 1 }; val r = opened(src)
        r.store.increment("TZP-1"); runCurrent()
        assertEquals(1, src.mutations)                                   // the server decides
    }

    @Test fun aCardsMaxHintLimitsTheFirstAdd() = runTest {
        val src = FakeCartSource(); val r = opened(src)
        r.store.increment("TZP-1", maxHint = 1); r.store.increment("TZP-1", maxHint = 1); runCurrent()
        assertEquals(listOf("PUT TZP-1=1"), src.calls.filter { it != "GET" })
    }

    @Test fun the400ThatIsNotAnInventoryLimitNeverNamesAMaximum() = runTest {
        val src = FakeCartSource(); val r = opened(src)
        src.failNext(http(400, "INVALID_REQUEST"))
        r.store.increment("TZP-1"); runCurrent()
        assertEquals(CartNotice.CouldntUpdate, r.store.notice.value)    // the hidden cart cap is never inferred
    }

    @Test fun a400OnALineAtItsInventoryMaxSaysMaximum() = runTest {
        val src = FakeCartSource().apply { lines["TZP-1"] = line("TZP-1", 4, max = 4); version = 1 }; val r = opened(src)
        src.failNext(http(400, "INVALID_REQUEST"))
        r.store.setQuantity("TZP-1", 5); runCurrent()
        assertEquals(CartNotice.MaxReached, r.store.notice.value)
    }

    // ---- failures ---------------------------------------------------------------------------------------------------

    @Test fun a412DiscardsEveryPendingIntentShowsServerTruthAndReplaysNothing() = runTest {
        val src = FakeCartSource().apply { lines["TZP-1"] = line("TZP-1", 1); version = 1 }; val r = opened(src)
        val gate = CompletableDeferred<Unit>(); src.gate = gate
        r.store.increment("TZP-2"); runCurrent()
        r.store.increment("TZP-3"); r.store.setQuantity("TZP-1", 6); runCurrent()
        src.externalChange()                                             // another device changed the cart
        src.gate = null; gate.complete(Unit); runCurrent()
        assertEquals(CartNotice.Stale, r.store.notice.value)
        assertEquals(1, src.mutations)                                   // nothing was replayed
        assertTrue(r.store.pending.value.isEmpty())
        assertEquals(src.version, r.loaded().version)
        assertEquals(1, r.loaded().quantityOf("TZP-1"))
    }

    @Test fun anAmbiguousFailureThatWasAppliedIsReconciledWithoutResending() = runTest {
        val src = FakeCartSource(); val r = opened(src)
        src.failNext(ApiException(ApiError.Timeout), applied = true)
        r.store.increment("TZP-1"); runCurrent()
        assertEquals(1, src.mutations)
        assertEquals(1, r.loaded().quantityOf("TZP-1")); assertNull(r.store.notice.value)
    }

    @Test fun anAmbiguousFailureThatWasNotAppliedIsNeverResent() = runTest {
        val src = FakeCartSource(); val r = opened(src)
        src.failNext(ApiException(ApiError.Network), applied = false)
        r.store.increment("TZP-1"); runCurrent()
        assertEquals(1, src.mutations)
        assertEquals(0, r.loaded().quantityOf("TZP-1"))
        assertEquals(CartNotice.NotApplied, r.store.notice.value)
    }

    @Test fun a500IsTreatedAsAmbiguousToo() = runTest {
        val src = FakeCartSource(); val r = opened(src)
        src.failNext(http(500), applied = true)
        r.store.increment("TZP-1"); runCurrent()
        assertEquals(1, src.mutations); assertEquals(1, r.loaded().quantityOf("TZP-1"))
    }

    @Test fun anAmbiguousRemoveIsSatisfiedOnlyIfTheLineIsGone() = runTest {
        val src = FakeCartSource().apply { lines["TZP-1"] = line("TZP-1", 2); version = 1 }; val r = opened(src)
        src.failNext(ApiException(ApiError.Timeout), applied = false)
        r.store.remove("TZP-1"); runCurrent()
        assertEquals(CartNotice.NotApplied, r.store.notice.value); assertEquals(2, r.loaded().quantityOf("TZP-1"))
    }

    @Test fun a503IsNotAppliedAndKeepsTheVersion() = runTest {
        val src = FakeCartSource().apply { version = 2 }; val r = opened(src)
        src.failNext(http(503, "SERVICE_UNAVAILABLE"))
        r.store.increment("TZP-1"); runCurrent()
        assertEquals(CartNotice.Unavailable, r.store.notice.value); assertEquals(2, r.loaded().version)
    }

    @Test fun a409IsTheCartFullMessage() = runTest {
        val src = FakeCartSource(); val r = opened(src)
        src.failNext(http(409, "CART_ITEM_LIMIT_REACHED"))
        r.store.increment("TZP-1"); runCurrent()
        assertEquals(CartNotice.CartFull, r.store.notice.value)
    }

    @Test fun a404ReReadsTheCartAndAsksTheAddressBookToCheck() = runTest {
        val src = FakeCartSource(); val r = opened(src)
        src.failNext(http(404, "NOT_FOUND"))
        r.store.increment("TZP-1"); runCurrent()
        assertEquals(1, r.suspect); assertEquals(CartNotice.ItemUnavailable, r.store.notice.value)
    }

    @Test fun a428IsAClientBugAndIsNeverRetried() = runTest {
        val src = FakeCartSource(); val r = opened(src)
        src.failNext(http(428, "PRECONDITION_REQUIRED"))
        r.store.increment("TZP-1"); runCurrent()
        assertEquals(CartNotice.ClientBug, r.store.notice.value); assertEquals(1, src.mutations)
    }

    // ---- sign-out ---------------------------------------------------------------------------------------------------

    @Test fun signOutForgetsTheLocalCartAndNeverCallsClear() = runTest {
        val src = FakeCartSource().apply { lines["TZP-1"] = line("TZP-1", 2); version = 1 }; val r = opened(src)
        r.store.signOut(); runCurrent()
        assertEquals(CartState.SignedOut, r.store.state.value)
        assertTrue(r.store.pending.value.isEmpty())
        assertTrue(src.calls.none { it == "DELETE ALL" })
        assertEquals(2, src.lines.getValue("TZP-1").quantity)            // the SERVER cart is untouched
    }

    @Test fun aResponseThatArrivesAfterSignOutIsDiscarded() = runTest {
        val src = FakeCartSource(); val r = opened(src)
        val gate = CompletableDeferred<Unit>(); src.gate = gate
        r.store.increment("TZP-1"); runCurrent()
        r.store.signOut(); runCurrent()
        src.gate = null; gate.complete(Unit); runCurrent()
        assertEquals(CartState.SignedOut, r.store.state.value)
    }

    @Test fun losingTheSessionMidQueueDropsTheRestWithoutSending() = runTest {
        val src = FakeCartSource(); val r = opened(src)
        val gate = CompletableDeferred<Unit>(); src.gate = gate
        r.store.increment("TZP-1"); runCurrent()
        r.store.increment("TZP-2"); runCurrent()
        r.authed = false
        src.gate = null; gate.complete(Unit); runCurrent()
        assertEquals(1, src.mutations)
        assertEquals(CartState.SignedOut, r.store.state.value)
    }

    @Test fun aSessionThatSignsBackInLoadsTheServerCartAgain() = runTest {
        val src = FakeCartSource().apply { lines["TZP-1"] = line("TZP-1", 2); version = 1 }; val r = opened(src)
        r.store.signOut(); runCurrent()
        r.store.load(); runCurrent()
        assertEquals(2, r.loaded().quantityOf("TZP-1"))
    }

    // ---- recovery actions through the store -----------------------------------------------------------------------

    @Test fun reduceQuantityIsAnExplicitAbsolutePutWithTheLatestVersionAndNeverAutomatic() = runTest {
        val src = FakeCartSource().apply {
            lines["TZP-1"] = line("TZP-1", 5, max = 3, issues = listOf("INSUFFICIENT_STOCK")); version = 6
        }
        val r = opened(src)
        // loading a cart with the issue sends nothing by itself
        assertEquals(0, src.mutations)
        val action = r.loaded().item("TZP-1")!!.toView().actions.first()
        assertEquals(CartAction.ReduceQuantity(3), action)
        r.store.setQuantity("TZP-1", (action as CartAction.ReduceQuantity).target); runCurrent()
        assertEquals(listOf("PUT TZP-1=3"), src.calls.filter { it != "GET" })
        assertEquals(listOf(6L), src.versionsSeen)
        assertEquals(3, r.loaded().quantityOf("TZP-1")); assertEquals(7, r.loaded().version)
    }

    @Test fun retryIsAGetWithTheSelectedAddressAndNeverReplaysAMutation() = runTest {
        val src = FakeCartSource().apply { lines["TZP-1"] = line("TZP-1", 2, issues = listOf("STOCK_UNKNOWN")); version = 2 }
        val r = opened(src)
        r.address = "ADDR_abcdef1"
        src.failNext(ApiException(ApiError.Timeout), applied = false)
        r.store.increment("TZP-1"); runCurrent()                          // an earlier, failed mutation
        val mutationsBefore = src.mutations
        r.store.refresh(); runCurrent()
        assertEquals(mutationsBefore, src.mutations)
        assertEquals("GET", src.calls.last()); assertEquals("ADDR_abcdef1", src.addressIds.last())
    }

    @Test fun anAddressChangeReplacesTheOldIssueStateWithTheServersAnswer() = runTest {
        val src = FakeCartSource().apply { lines["TZP-1"] = line("TZP-1", 1, issues = listOf("LOCATION_REQUIRED"), serviceable = null, buyable = false); version = 1 }
        val r = opened(src)
        assertTrue(r.loaded().item("TZP-1")!!.isBlocked)
        src.lines["TZP-1"] = line("TZP-1", 1)                              // the server now evaluates it for the chosen address
        r.address = "ADDR_abcdef1"; r.store.refresh(); runCurrent()
        assertTrue(!r.loaded().item("TZP-1")!!.isBlocked)
    }
}
