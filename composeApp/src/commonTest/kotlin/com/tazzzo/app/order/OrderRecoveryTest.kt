package com.tazzzo.app.order

import com.tazzzo.app.auth.InMemorySecureTokenStore
import com.tazzzo.app.auth.JSON_HEADERS
import com.tazzzo.app.auth.apiClient
import com.tazzzo.app.auth.errorJson
import com.tazzzo.app.auth.refreshJson
import com.tazzzo.app.auth.tokens
import com.tazzzo.app.checkout.FakeCartAccess
import com.tazzzo.app.data.auth.AuthSessionManager
import com.tazzzo.app.data.auth.RemoteAuthDataSource
import com.tazzzo.app.data.auth.RestoreOutcome
import com.tazzzo.app.data.checkout.CheckoutState
import com.tazzzo.app.data.order.OrderSessionBinding
import com.tazzzo.app.data.order.OrderState
import com.tazzzo.app.data.order.OrderStore
import com.tazzzo.app.data.order.restoreSessionAndRecoverOrders
import io.ktor.client.engine.mock.respond
import io.ktor.http.HttpStatusCode
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Pending-order recovery across cold starts, driven by the REAL [AuthSessionManager] (restore / transient refresh failure /
 * definitive rejection / logout), not by a stub: "no session yet" must never be mistaken for a logout.
 */
class OrderRecoveryTest {
    private val now = 20_000_000L                                   // tokens() expire at 10_000_000 -> stale -> restore refreshes

    private enum class Refresh { Ok, Rejected401, Offline, Server503 }

    private class Rig(scope: TestScope, saved: com.tazzzo.app.data.auth.StoredTokens?, refresh: Refresh, now: Long) {
        val pending = FakePending().apply { value = "CHKQ_abc123" }
        val server = FakeOrderSource()
        val quotes = FakeQuoteAccess(CheckoutState.Idle)
        val cart = FakeCartAccess()
        val session = AuthSessionManager(
            RemoteAuthDataSource(apiClient { _ ->
                when (refresh) {
                    Refresh.Ok -> respond(refreshJson("acc2", "SES_abc.ref2"), HttpStatusCode.OK, JSON_HEADERS)
                    Refresh.Rejected401 -> respond(errorJson("INVALID_REFRESH_TOKEN"), HttpStatusCode.Unauthorized, JSON_HEADERS)
                    Refresh.Server503 -> respond(errorJson("SERVICE_UNAVAILABLE"), HttpStatusCode.ServiceUnavailable, JSON_HEADERS)
                    Refresh.Offline -> throw RuntimeException("offline")
                }
            }),
            InMemorySecureTokenStore(saved), scope.backgroundScope, nowMs = { now }
        )
        val orders = OrderStore(scope.backgroundScope, server, quotes, cart, pending, { session.isAuthenticated }, { true }, {})
        var initiallyAuthenticated: Boolean? = null

        suspend fun coldStart(scope: TestScope) {
            restoreSessionAndRecoverOrders(session, orders) { initially ->
                initiallyAuthenticated = initially
                OrderSessionBinding(scope.backgroundScope, session.active, orders).start(initially)
            }
            scope.runCurrent()
        }
    }

    private fun TestScope.rig(saved: com.tazzzo.app.data.auth.StoredTokens? = tokens(expiresAtMs = now + 600_000), refresh: Refresh = Refresh.Ok) =
        Rig(this, saved, refresh, now)

    // ---- 1. restoring / no session yet ------------------------------------------------------------------------------

    @Test fun anAbsentSessionAtColdStartKeepsTheRecordAndPostsNothing() = runTest {
        val r = rig(saved = null)
        r.coldStart(this)
        assertEquals(RestoreOutcome.NoSession, r.session.restore())
        assertEquals("CHKQ_abc123", r.pending.value); assertTrue(r.server.calls.isEmpty())
        assertEquals(false, r.initiallyAuthenticated)
        assertEquals(OrderState.Idle, r.orders.state.value)
    }

    @Test fun theBindingStartedBeforeAnySessionExistsDoesNotMistakeThePlaceholderForALogout() = runTest {
        val r = rig(saved = null)
        r.coldStart(this); runCurrent()                                         // active emits an initial `false`
        assertEquals("CHKQ_abc123", r.pending.value); assertEquals(OrderState.Idle, r.orders.state.value)
    }

    @Test fun aBindingStartedWithARestoredSessionDoesNotTreatTheInitialTrueAsASignIn() = runTest {
        val r = rig()
        r.coldStart(this); runCurrent()
        assertEquals(true, r.initiallyAuthenticated)
        assertTrue(r.pending.clears == 0 || r.pending.value == null)           // cleared only by the resolved reconciliation below
        assertIs<OrderState.Placed>(r.orders.state.value)
    }

    // ---- 2. restoration succeeds ----------------------------------------------------------------------------------

    @Test fun whenTheSameSessionIsRestoredExactlyOneReconciliationPostsTheSameQuoteAndSuccessClearsTheRecord() = runTest {
        val r = rig()
        r.server.failNext(com.tazzzo.app.data.remote.ApiException(com.tazzzo.app.data.remote.ApiError.Timeout), applied = true)
        // the earlier process's POST committed the order (a lost response) -> pre-create it on the backend
        runCatching { r.server.placeCodOrder("CHKQ_abc123") }
        val before = r.server.calls.size
        r.coldStart(this)
        assertEquals(before + 1, r.server.calls.size); assertEquals("CHKQ_abc123", r.server.calls.last())
        assertIs<OrderState.Placed>(r.orders.state.value); assertNull(r.pending.value)
        assertEquals(1, r.server.ordersCreated()); assertEquals(1, r.quotes.resets); assertEquals(1, r.cart.refreshes)
    }

    @Test fun aStaleSessionThatRefreshesSuccessfullyIsRestoredAndReconciledOnce() = runTest {
        val r = rig(saved = tokens(expiresAtMs = 10_000_000L), refresh = Refresh.Ok)
        r.server.failNext(com.tazzzo.app.data.remote.ApiException(com.tazzzo.app.data.remote.ApiError.Timeout), applied = true)
        runCatching { r.server.placeCodOrder("CHKQ_abc123") }
        val before = r.server.calls.size
        r.coldStart(this)
        assertEquals(before + 1, r.server.calls.size); assertIs<OrderState.Placed>(r.orders.state.value); assertNull(r.pending.value)
    }

    // ---- 3. transient refresh failure ------------------------------------------------------------------------------

    @Test fun aTransientRefreshFailureKeepsTheSessionAndTheRecord() = runTest {
        for (mode in listOf(Refresh.Offline, Refresh.Server503)) {
            val r = rig(saved = tokens(expiresAtMs = 10_000_000L), refresh = mode)
            r.server.rejectNew = com.tazzzo.app.checkout.hx(503, "SERVICE_UNAVAILABLE")      // the reconciliation itself is also unavailable
            r.coldStart(this)
            assertEquals(true, r.initiallyAuthenticated, mode.name)                          // PR-03A preserved the session
            assertTrue(r.session.isAuthenticated, mode.name)
            assertEquals("CHKQ_abc123", r.pending.value, mode.name)                          // NOT deleted
            assertIs<OrderState.Ambiguous>(r.orders.state.value, mode.name)                  // and "Check order" is offered, no loop
            assertEquals(1, r.server.calls.size, mode.name)
        }
    }

    // ---- 4. definitive rejection --------------------------------------------------------------------------------------

    @Test fun aDefinitiveRefreshRejectionClearsTheSessionAndDeletesTheRecordWithoutPosting() = runTest {
        val r = rig(saved = tokens(expiresAtMs = 10_000_000L), refresh = Refresh.Rejected401)
        r.coldStart(this)
        assertEquals(false, r.initiallyAuthenticated); assertTrue(!r.session.isAuthenticated)
        assertNull(r.pending.value); assertTrue(r.server.calls.isEmpty())
        assertEquals(OrderState.SignedOut, r.orders.state.value)
    }

    // ---- 5. explicit logout ------------------------------------------------------------------------------------------------

    @Test fun anExplicitLogoutAfterStartupDeletesTheRecordAndNeverPosts() = runTest {
        val r = rig()
        r.server.rejectNew = com.tazzzo.app.checkout.hx(503, "SERVICE_UNAVAILABLE")
        r.coldStart(this); assertIs<OrderState.Ambiguous>(r.orders.state.value)
        val calls = r.server.calls.size
        r.session.logout(); runCurrent()
        assertEquals(OrderState.SignedOut, r.orders.state.value); assertNull(r.pending.value); assertEquals(calls, r.server.calls.size)
    }

    // ---- 6. account switch / interactive login --------------------------------------------------------------------------

    @Test fun anInteractiveSignInNeverReconcilesAnOldRecordAndDeletesItBecauseOwnershipCannotBeProven() = runTest {
        val r = rig(saved = null)                                               // cold start with no session; a record is lying around
        r.coldStart(this)
        assertEquals("CHKQ_abc123", r.pending.value)                            // kept while only "not signed in yet"
        // a different customer now signs in interactively
        val loginTokens = tokens("accB", "SES_abc.refB", expiresAtMs = now + 600_000)
        val store = InMemorySecureTokenStore(loginTokens)
        val b = Rig(this, null, Refresh.Ok, now)                                 // fresh rig purely to drive the binding with a session flow
        b.pending.value = "CHKQ_abc123"
        val flow = kotlinx.coroutines.flow.MutableStateFlow(false)
        val orders = OrderStore(backgroundScope, b.server, b.quotes, b.cart, b.pending, { flow.value }, { true }, {})
        OrderSessionBinding(backgroundScope, flow, orders).start(initiallyActive = false); runCurrent()
        assertEquals("CHKQ_abc123", b.pending.value)
        flow.value = true; runCurrent()                                          // interactive sign-in
        assertNull(b.pending.value); assertTrue(b.server.calls.isEmpty())
        orders.resumeAfterRestore(); runCurrent()
        assertTrue(b.server.calls.isEmpty()); assertTrue(store.current != null)
    }

    @Test fun customerAsLogoutThenCustomerBLoginLeavesNothingForBToReconcile() = runTest {
        val r = rig()
        r.server.rejectNew = com.tazzzo.app.checkout.hx(503, "SERVICE_UNAVAILABLE")
        r.coldStart(this)
        r.session.logout(); runCurrent()
        assertNull(r.pending.value)
        val calls = r.server.calls.size
        r.orders.onInteractiveSignIn(); r.orders.resumeAfterRestore(); runCurrent()
        assertEquals(calls, r.server.calls.size); assertEquals(OrderState.Idle, r.orders.state.value)
    }
}
