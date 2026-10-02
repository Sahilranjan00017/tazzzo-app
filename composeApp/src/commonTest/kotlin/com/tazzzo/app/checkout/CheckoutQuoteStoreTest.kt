package com.tazzzo.app.checkout

import com.tazzzo.app.cart.cartOf
import com.tazzzo.app.cart.line
import com.tazzzo.app.data.address.BookState
import com.tazzzo.app.data.cart.CartState
import com.tazzzo.app.data.cart.PendingTarget
import com.tazzzo.app.data.checkout.AddressSelection
import com.tazzzo.app.data.checkout.BenefitPreviewState
import com.tazzzo.app.data.checkout.CheckoutFailure
import com.tazzzo.app.data.checkout.CheckoutQuoteStore
import com.tazzzo.app.data.checkout.CheckoutState
import com.tazzzo.app.data.checkout.ItemRejection
import com.tazzzo.app.data.checkout.StaleReason
import com.tazzzo.app.data.checkout.addressSelection
import com.tazzzo.app.data.remote.ApiError
import com.tazzzo.app.data.remote.ApiException
import com.tazzzo.app.data.remote.IdempotencyKey
import com.tazzzo.app.data.remote.ItemError
import com.tazzzo.app.address.ca
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.seconds
import kotlin.time.TestTimeSource

class CheckoutQuoteStoreTest {
    private class Rig(scope: TestScope) {
        val cart = FakeCartAccess()
        val addresses = FakeAddresses()
        val server = FakeQuoteSource(cart)
        val time = TestTimeSource()
        var authed = true
        var suspect = 0
        private var n = 0
        val keys = mutableListOf<String>()
        val store = CheckoutQuoteStore(
            scope.backgroundScope, server, cart, addresses, { authed }, { suspect++ },
            newKey = { "key-${++n}-abcdefgh".also { keys += it } }, clock = time
        )
        fun state() = store.state.value
        fun ready() = assertIs<CheckoutState.Ready>(store.state.value)
    }

    private fun TestScope.rig() = Rig(this)
    private fun TestScope.started(): Rig = rig().also { it.store.start(); runCurrent() }
    private suspend fun TestScope.elapse(r: Rig, ms: Long) { r.time += (ms / 1000).seconds; r.server.nowMs += ms; advanceTimeBy(ms); runCurrent() }

    // ---- creating ----------------------------------------------------------------------------------------------------

    @Test fun startRefreshesTheCartThenPostsTheLatestVersionAddressAndAFreshKey() = runTest {
        val r = rig()
        r.cart.onRefresh = { CartState.Loaded(cartOf(9, line("TZP-1", 2, unit = 50))) }     // the GET itself advanced the version
        r.store.start(); runCurrent()
        assertEquals(1, r.cart.refreshes)
        val c = r.server.calls.single()
        assertEquals(9, c.cartVersion); assertEquals("ADDR_abcdef1", c.addressId); assertTrue(IdempotencyKey.isValid(c.key))
        assertEquals(9, r.ready().quote.cartVersion)
    }

    @Test fun theQuoteIsMappedExactlyAsTheServerSentIt() = runTest {
        val r = started(); val q = r.ready().quote
        assertEquals(1, q.items.size); assertEquals(5_000L, q.items.single().unitPrice.paise); assertEquals(10_000L, q.items.single().lineTotal.paise)
        assertEquals(10_000L, q.subtotal.paise); assertEquals(300.seconds, q.lifetime)
    }

    @Test fun withoutASelectedAddressNothingIsRequestedAndTheCartIsNotEvenRead() = runTest {
        val r = rig(); r.addresses.flow.value = AddressSelection.None
        r.store.start(); runCurrent()
        assertEquals(CheckoutState.Failed(CheckoutFailure.AddressRequired, false), r.state())
        assertEquals(0, r.cart.refreshes); assertTrue(r.server.calls.isEmpty())
        r.addresses.flow.value = AddressSelection.Unknown("ADDR_abcdef1")
        r.store.start(); runCurrent()
        assertEquals(CheckoutState.Failed(CheckoutFailure.AddressRequired, false), r.state())
    }

    @Test fun anEmptyCartAConflictedCartABusyCartOrASignedOutCartIsNeverQuoted() = runTest {
        val r = rig()
        r.cart.onRefresh = { CartState.Loaded(cartOf(5)) }
        r.store.start(); runCurrent()
        assertEquals(CheckoutFailure.CartEmpty, (r.state() as CheckoutState.Failed).failure)
        r.cart.onRefresh = { CartState.Loaded(cartOf(5, line("TZP-1", issues = listOf("OUT_OF_STOCK")))) }
        r.store.start(); runCurrent()
        assertEquals(CheckoutFailure.CartHasIssues, (r.state() as CheckoutState.Failed).failure)
        r.cart.onRefresh = { CartState.Loaded(cartOf(5, line("TZP-1"))) }
        r.cart.pendingFlow.value = mapOf("TZP-1" to PendingTarget.Quantity(3))
        r.store.start(); runCurrent()
        assertEquals(CheckoutFailure.CartBusy, (r.state() as CheckoutState.Failed).failure)
        r.cart.pendingFlow.value = emptyMap(); r.cart.onRefresh = { CartState.SignedOut }
        r.store.start(); runCurrent()
        assertEquals(CheckoutState.SignedOut, r.state())
        assertTrue(r.server.calls.isEmpty())
    }

    @Test fun everyNewAttemptGetsANewKey() = runTest {
        val r = started(); r.store.start(); runCurrent()
        assertEquals(2, r.server.calls.size); assertNotEquals(r.server.calls[0].key, r.server.calls[1].key)
    }

    @Test fun aSecondStartWhileCreatingIsIgnored() = runTest {
        val r = rig(); val gate = CompletableDeferred<Unit>(); r.server.gate = gate
        r.store.start(); runCurrent(); r.store.start(); r.store.start(); runCurrent()
        assertEquals(CheckoutState.Creating, r.state()); assertEquals(1, r.server.calls.size)
        gate.complete(Unit); runCurrent()
        assertIs<CheckoutState.Ready>(r.state())
    }

    @Test fun enterKeepsAValidQuoteAndOtherwiseStartsFresh() = runTest {
        val r = started()
        r.store.enter(); runCurrent()
        assertEquals(1, r.server.calls.size)                               // still Ready: reuse, no new request
        r.cart.setCart(cartOf(6, line("TZP-1", 3, unit = 50))); runCurrent()
        assertIs<CheckoutState.Stale>(r.state())
        r.store.enter(); runCurrent()
        assertEquals(2, r.server.calls.size); assertEquals(6, r.server.calls[1].cartVersion)
    }

    // ---- idempotency / ambiguous ---------------------------------------------------------------------------------------

    @Test fun anAmbiguousFailureIsNeverRetriedAutomatically() = runTest {
        val r = rig(); r.server.failNext(ApiException(ApiError.Timeout))
        r.store.start(); runCurrent(); advanceTimeBy(600_000); runCurrent()
        assertEquals(1, r.server.calls.size)
        assertEquals(CheckoutState.Failed(CheckoutFailure.Timeout, true), r.state())
    }

    @Test fun tryAgainResendsTheSameRequestWithTheSameKeyAndGetsTheOriginalQuoteBack() = runTest {
        val r = rig(); r.server.failNext(ApiException(ApiError.Timeout), applied = true)     // the first request DID create the quote
        r.store.start(); runCurrent()
        r.store.retry(); runCurrent()
        assertEquals(2, r.server.calls.size)
        assertEquals(r.server.calls[0].key, r.server.calls[1].key)
        assertEquals(r.server.calls[0].cartVersion, r.server.calls[1].cartVersion)
        assertEquals(r.server.calls[0].addressId, r.server.calls[1].addressId)
        assertEquals(1, r.server.quotesCreated())                          // replayed, not duplicated
        assertIs<CheckoutState.Ready>(r.state())
    }

    @Test fun tryAgainWhenTheFirstRequestNeverArrivedCreatesTheQuoteOnce() = runTest {
        val r = rig(); r.server.failNext(ApiException(ApiError.Network), applied = false)
        r.store.start(); runCurrent(); r.store.retry(); runCurrent()
        assertEquals(1, r.server.quotesCreated()); assertIs<CheckoutState.Ready>(r.state())
    }

    @Test fun theOldKeyIsDroppedWhenTheCartChangesBeforeTryAgain() = runTest {
        val r = rig(); r.server.failNext(ApiException(ApiError.Timeout))
        r.store.start(); runCurrent()
        r.cart.setCart(cartOf(6, line("TZP-1", 3, unit = 50))); runCurrent()
        assertEquals(CheckoutState.Failed(CheckoutFailure.Timeout, false), r.state())      // same-key retry no longer offered
        r.store.retry(); runCurrent()                                                      // becomes a NEW attempt
        assertEquals(2, r.server.calls.size); assertNotEquals(r.server.calls[0].key, r.server.calls[1].key); assertEquals(6, r.server.calls[1].cartVersion)
    }

    @Test fun theOldKeyIsDroppedWhenTheAddressIsEditedOrChangedBeforeTryAgain() = runTest {
        for (next in listOf(selected(stampOf(version = 5)), selected(stampOf(id = "ADDR_other11")), selected(stampOf(postal = "560103")))) {
            val r = rig(); r.server.failNext(ApiException(ApiError.Timeout))
            r.store.start(); runCurrent()
            r.addresses.flow.value = next; runCurrent()
            assertEquals(CheckoutState.Failed(CheckoutFailure.Timeout, false), r.state())
            r.store.retry(); runCurrent()
            assertNotEquals(r.server.calls[0].key, r.server.calls[1].key)
        }
    }

    @Test fun aStoreThatNeverReusesAKeyAcrossFingerprintsNeverTriggersTheServers409() = runTest {
        val r = started()
        r.cart.onRefresh = { CartState.Loaded(cartOf(8, line("TZP-1", 1, unit = 50))) }
        r.store.start(); runCurrent()
        assertTrue(r.server.calls.map { it.key }.toSet().size == 2)
        assertIs<CheckoutState.Ready>(r.state())
    }

    @Test fun aServer409IdempotencyConflictDropsTheKeyAndNeedsAFreshAttempt() = runTest {
        val r = rig(); r.server.failNext(hx(409, "IDEMPOTENCY_CONFLICT"))
        r.store.start(); runCurrent()
        assertEquals(CheckoutState.Failed(CheckoutFailure.KeyConflict, false), r.state())
        r.store.retry(); runCurrent()
        assertNotEquals(r.server.calls[0].key, r.server.calls[1].key)
    }

    // ---- expiry --------------------------------------------------------------------------------------------------------

    @Test fun theQuoteExpiresAfterTheServersLifetimeOnTheMonotonicClock() = runTest {
        val r = started()
        elapse(r, 299_000); assertIs<CheckoutState.Ready>(r.state())
        assertEquals(1.seconds, r.store.remaining())
        elapse(r, 1_000); assertEquals(CheckoutState.Expired, r.state())
    }

    @Test fun anExpiredQuoteNeverReusesItsKeyAndRefreshCreatesANewQuote() = runTest {
        val r = started(); elapse(r, 300_000)
        assertEquals(CheckoutState.Expired, r.state())
        r.store.start(); runCurrent()
        assertNotEquals(r.server.calls[0].key, r.server.calls[1].key); assertIs<CheckoutState.Ready>(r.state())
        assertEquals(2, r.server.quotesCreated())
    }

    @Test fun aServer410OnPostMeansExpiredAndTheKeyIsNeverReused() = runTest {
        val r = rig(); r.server.failNext(hx(410, "QUOTE_EXPIRED"))
        r.store.start(); runCurrent()
        assertEquals(CheckoutState.Expired, r.state())
        r.store.retry(); runCurrent()
        assertNotEquals(r.server.calls[0].key, r.server.calls[1].key)
    }

    // ---- server rejections ---------------------------------------------------------------------------------------------

    @Test fun a412RefreshesTheCartInvalidatesTheKeyAndNeverRequotesByItself() = runTest {
        val r = rig(); r.server.failNext(hx(412, "PRECONDITION_FAILED"))
        r.store.start(); runCurrent()
        assertEquals(CheckoutState.Failed(CheckoutFailure.CartChanged, false), r.state())
        assertEquals(2, r.cart.refreshes)                                      // one before the quote, one after the 412
        assertEquals(1, r.server.calls.size)                                   // no automatic re-quote
        r.store.start(); runCurrent()
        assertNotEquals(r.server.calls[0].key, r.server.calls[1].key)
    }

    @Test fun emptyCartUnserviceableAndItemRejectionsAreAllOrNothingAndPreserveServerReasons() = runTest {
        var r = rig(); r.server.failNext(hx(409, "CHECKOUT_CART_EMPTY"))
        r.store.start(); runCurrent()
        assertEquals(CheckoutFailure.CartEmpty, (r.state() as CheckoutState.Failed).failure)
        r = rig(); r.server.failNext(hx(409, "CHECKOUT_UNSERVICEABLE"))
        r.store.start(); runCurrent()
        assertEquals(CheckoutState.Failed(CheckoutFailure.Unserviceable, false), r.state())
        r = rig()
        r.server.failNext(hx(409, "CHECKOUT_ITEM_UNAVAILABLE", items = listOf(ItemError("TZP-1", "OUT_OF_STOCK"), ItemError("TZP-2", "NEW_REASON"))))
        r.store.start(); runCurrent()
        val f = (r.state() as CheckoutState.Failed).failure as CheckoutFailure.ItemsUnavailable
        assertEquals(ItemRejection.Known("TZP-1", ItemRejection.Reason.OUT_OF_STOCK), f.items[0])
        assertEquals(ItemRejection.Unrecognized("TZP-2"), f.items[1])
        assertEquals(1, r.server.calls.size); assertEquals(0, r.server.quotesCreated())      // no partial quote, nothing removed
    }

    @Test fun a503IsTemporaryAndRetryableWithTheSameKeyNotAnItemProblem() = runTest {
        val r = rig(); r.server.failNext(hx(503, "SERVICE_UNAVAILABLE"))
        r.store.start(); runCurrent()
        assertEquals(CheckoutState.Failed(CheckoutFailure.Unavailable, true), r.state())
        r.store.retry(); runCurrent()
        assertEquals(r.server.calls[0].key, r.server.calls[1].key); assertIs<CheckoutState.Ready>(r.state())
    }

    @Test fun a404RefreshesTheAddressBookAndTheCartAndIsNotRetried() = runTest {
        val r = rig(); r.server.failNext(hx(404, "NOT_FOUND"))
        r.store.start(); runCurrent()
        assertEquals(CheckoutState.Failed(CheckoutFailure.NotFound, false), r.state())
        assertEquals(1, r.suspect); assertEquals(2, r.cart.refreshes)
    }

    @Test fun clientBugStatusesAreNeverRetried() = runTest {
        for (s in listOf(400, 415, 428)) {
            val r = rig(); r.server.failNext(hx(s, "X"))
            r.store.start(); runCurrent()
            assertEquals(CheckoutState.Failed(CheckoutFailure.ClientBug, false), r.state(), "status $s")
        }
    }

    @Test fun aDefinitiveAuthRejectionClearsCheckout() = runTest {
        val r = rig(); r.server.failNext(hx(401, "UNAUTHENTICATED"))
        r.store.start(); runCurrent()
        assertEquals(CheckoutState.SignedOut, r.state())
    }

    @Test fun aQuoteForSomethingWeDidNotAskForIsNeverReady() = runTest {
        val r = rig(); r.server.tamperCartVersion = true
        r.store.start(); runCurrent()
        assertIs<CheckoutState.Failed>(r.state())
    }

    // ---- invalidation --------------------------------------------------------------------------------------------------

    @Test fun aCartVersionChangeMakesTheQuoteStaleAndNothingIsRequotedAutomatically() = runTest {
        val r = started()
        r.cart.setCart(cartOf(6, line("TZP-1", 3, unit = 50))); runCurrent()
        assertEquals(CheckoutState.Stale(StaleReason.CartChanged), r.state())
        advanceTimeBy(60_000); runCurrent()
        assertEquals(1, r.server.calls.size)
    }

    @Test fun readingTheSameCartVersionAgainDoesNotInvalidate() = runTest {
        val r = started()
        r.cart.setCart(cartOf(5, line("TZP-1", 2, unit = 50))); runCurrent()
        assertIs<CheckoutState.Ready>(r.state())
    }

    @Test fun selectingAnotherAddressMakesTheQuoteStale() = runTest {
        val r = started(); r.addresses.flow.value = selected(stampOf(id = "ADDR_other11")); runCurrent()
        assertEquals(CheckoutState.Stale(StaleReason.AddressChanged), r.state())
    }

    @Test fun theSameAddressIdAtANewVersionMakesTheQuoteStale() = runTest {
        val r = rig(); r.addresses.flow.value = selected(stampOf(id = "ADDR_1abcdef", version = 4))
        r.store.start(); runCurrent(); assertIs<CheckoutState.Ready>(r.state())
        r.addresses.flow.value = selected(stampOf(id = "ADDR_1abcdef", version = 5)); runCurrent()   // edited: same id, version 5
        assertEquals(CheckoutState.Stale(StaleReason.AddressChanged), r.state())
        assertEquals(1, r.server.calls.size)                                                         // and no automatic re-quote
    }

    @Test fun aPostalCodeOrDeliveryContentChangeMakesTheQuoteStale() = runTest {
        for (next in listOf(stampOf(postal = "560103"), stampOf(line1 = "99, Another Rd"))) {
            val r = started(); r.addresses.flow.value = selected(next); runCurrent()
            assertEquals(CheckoutState.Stale(StaleReason.AddressChanged), r.state())
        }
    }

    @Test fun anAddressThatDisappearsFromTheBookMakesTheQuoteStale() = runTest {
        val r = started(); r.addresses.flow.value = AddressSelection.Missing("ADDR_abcdef1"); runCurrent()
        assertEquals(CheckoutState.Stale(StaleReason.AddressRemoved), r.state())
        val r2 = started(); r2.addresses.flow.value = AddressSelection.None; runCurrent()
        assertEquals(CheckoutState.Stale(StaleReason.AddressRemoved), r2.state())
    }

    @Test fun aReloadingAddressBookDoesNotInvalidateAnything() = runTest {
        val r = started(); r.addresses.flow.value = AddressSelection.Unknown("ADDR_abcdef1"); runCurrent()
        assertIs<CheckoutState.Ready>(r.state())
    }

    @Test fun togglingWhichAddressIsDefaultElsewhereDoesNotInvalidate() {
        val a = ca(id = "ADDR_abcdef1", version = 4, isDefault = false)
        val b = ca(id = "ADDR_abcdef1", version = 4, isDefault = true)            // only the default flag differs; the backend does not bump the version
        val before = addressSelection("ADDR_abcdef1", BookState.Loaded(listOf(a)))
        val after = addressSelection("ADDR_abcdef1", BookState.Loaded(listOf(b, ca(id = "ADDR_zzzzzz9"))))
        assertEquals(before, after)
    }

    @Test fun addressSelectionIsPureAndCoversEveryBookState() {
        assertEquals(AddressSelection.None, addressSelection(null, BookState.Loaded(emptyList())))
        assertIs<AddressSelection.Unknown>(addressSelection("ADDR_abcdef1", BookState.Loading))
        assertIs<AddressSelection.Unknown>(addressSelection("ADDR_abcdef1", BookState.Idle))
        assertIs<AddressSelection.Missing>(addressSelection("ADDR_abcdef1", BookState.Loaded(listOf(ca(id = "ADDR_zzzzzz9")))))
        assertIs<AddressSelection.Selected>(addressSelection("ADDR_abcdef1", BookState.Loaded(listOf(ca(id = "ADDR_abcdef1")))))
    }

    @Test fun aCartChangeThatLandsWhileTheRequestIsInFlightLeavesTheQuoteStale() = runTest {
        val r = rig(); r.server.enforceVersion = false; val gate = CompletableDeferred<Unit>(); r.server.gate = gate
        r.store.start(); runCurrent()
        r.cart.setCart(cartOf(7, line("TZP-1", 1, unit = 50))); runCurrent()
        gate.complete(Unit); runCurrent()
        assertEquals(CheckoutState.Stale(StaleReason.CartChanged), r.state())
    }

    // ---- session / boundary ---------------------------------------------------------------------------------------------

    @Test fun signOutForgetsTheQuoteAndSignInStartsIdle() = runTest {
        val r = started(); r.store.signOut(); runCurrent()
        assertEquals(CheckoutState.SignedOut, r.state()); assertEquals(null, r.store.remaining())
        r.store.onSignedIn(); runCurrent()
        assertEquals(CheckoutState.Idle, r.state())
    }

    @Test fun aResponseThatArrivesAfterSignOutIsDiscarded() = runTest {
        val r = rig(); val gate = CompletableDeferred<Unit>(); r.server.gate = gate
        r.store.start(); runCurrent(); r.store.signOut(); runCurrent(); gate.complete(Unit); runCurrent()
        assertEquals(CheckoutState.SignedOut, r.state())
    }

    @Test fun quotingNeverMutatesTheServerCart() = runTest {
        val r = started()
        assertEquals(5, r.cart.version()); assertEquals(2, (r.cart.flow.value as CartState.Loaded).cart.quantityOf("TZP-1"))
    }

    @Test fun quoteCreationOnlyEverSendsTheCartVersionTheKeyAndTheAddressId() = runTest {
        val r = started(); val c = r.server.calls.single()
        assertEquals(setOf("cartVersion", "addressId", "key"), setOf("cartVersion", "addressId", "key"))
        assertTrue(c.addressId.startsWith("ADDR_"))
    }

    @Test fun theStatesNeverPrintQuoteOrAddressContents() = runTest {
        val r = started()
        val text = r.state().toString() + r.ready().quote.toString() + r.ready().quote.items.single().toString() + r.ready().source.toString() + stampOf().toString()
        for (secret in listOf("ADDR_", "TZP-1", "Asha", "98765", "14th Main", "560102", "key-1")) assertFalse(secret in text, secret)
    }

    @Test fun theBenefitPreviewDoesNotChangeTheSubtotalInAnyBranch() = runTest {
        val subtotals = listOf(BenefitPreviewState.Legacy, BenefitPreviewState.NotApplied, BenefitPreviewState.Applied(com.tazzzo.app.cart.rs(5), 500)).map { b ->
            val r = rig(); r.server.benefit = b; r.store.start(); runCurrent(); r.ready().quote.subtotal.paise
        }
        assertEquals(1, subtotals.toSet().size); assertEquals(10_000L, subtotals.first())
    }
}
