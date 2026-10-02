package com.tazzzo.app.data.order

import com.tazzzo.app.analytics.Analytics
import com.tazzzo.app.analytics.AnalyticsEvents
import com.tazzzo.app.config.AppEnvironment
import com.tazzzo.app.config.debugRealOrderingRequested
import com.tazzzo.app.data.cart.CartAccess
import com.tazzzo.app.data.catalog.CatalogCapabilities
import com.tazzzo.app.data.checkout.CheckoutQuoteAccess
import com.tazzzo.app.data.checkout.CheckoutState
import com.tazzzo.app.data.checkout.StaleReason
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

/**
 * Decides whether the app may submit a real COD order. This is a LAUNCH rule, not a property of [OrderStore]: production
 * REMOTE keeps `orderIntegration = false` until deployment and end-to-end sign-off, while a developer build can explicitly
 * enable the full real path. Flipping the capability later needs no store changes.
 *
 * The developer override needs BOTH a debug binary AND an explicit build-time opt-in (`-Ptazzzo.debugRealOrdering=true`,
 * see [debugRealOrderingRequested]); it is never persisted and has no UI. A release build ignores it: there only
 * `orderIntegration` decides.
 */
object OrderLaunchGate {
    /** Debug-only explicit enable (a hard no-op in a release build, same rule as `CatalogSource`). Starts from the build opt-in. */
    var debugEnabled: Boolean = AppEnvironment.isDebug && debugRealOrderingRequested()
        set(value) { if (!AppEnvironment.isDebug) return; field = value }

    fun enabled(caps: CatalogCapabilities): Boolean = allows(caps.orderIntegration, AppEnvironment.isDebug, debugEnabled)

    /** The rule itself: the production capability, or (debug binary AND explicit opt-in). A release binary ignores the override. */
    internal fun allows(orderIntegration: Boolean, debugBuild: Boolean, debugOptIn: Boolean): Boolean =
        orderIntegration || (debugBuild && debugOptIn)
}

sealed interface OrderState {
    data object SignedOut : OrderState
    data object Idle : OrderState
    /** One POST is on the wire (or being reconciled). Further placements are ignored. */
    data object Placing : OrderState
    /** The order MAY exist (timeout / lost response / 5xx). The pending quote id is persisted; "Check order" re-POSTs the SAME quote. */
    data class Ambiguous(val failure: OrderFailure) : OrderState
    data class Placed(val order: CustomerOrder) : OrderState { override fun toString() = "Placed(***)" }
    /** A conclusive outcome: the backend answered and NO order exists for the quote (e.g. PAYABLE_CHANGED). */
    data class Failed(val failure: OrderFailure) : OrderState
}

/**
 * The REAL order — the backend is the only truth.
 *
 *  - Input is ONLY the Ready, current quote from the quote store. Nothing is read from the local cart, `CheckoutSession`,
 *    `BillCalculator`, `lastOrder` or any mock repository.
 *  - Single flight: one POST at a time; further taps while Placing/Ambiguous/Placed do nothing.
 *  - The POST body is `{quoteId, "COD"}`. Re-POSTing the same quote is idempotent on the backend (replay lookup runs before
 *    expiry and every other check), so it IS the reconciliation: 200 = the order exists; a definitive 4xx = no order exists.
 *    A timeout/lost response/5xx is Ambiguous and is resolved ONLY by an explicit "Check order" (same quote, never a new
 *    quote or a new intent).
 *  - Process-death recovery: while Placing/Ambiguous, the opaque quote id (nothing else) is persisted. On the next
 *    authenticated cold start ONE automatic reconciliation is attempted; if that is ambiguous again the record stays and
 *    the customer gets "Check order". Logout (or any sign-out) deletes the record and never POSTs.
 *  - On success: the quote is reset FIRST, the pending record is deleted, then the SERVER cart is re-read. The cart is never
 *    cleared or edited locally.
 *
 * All mutable state is touched only on [scope]'s serial dispatcher (the same one the quote store uses).
 */
class OrderStore(
    private val scope: CoroutineScope,
    private val source: OrderSource,
    private val quotes: CheckoutQuoteAccess,
    private val cart: CartAccess,
    private val pending: PendingOrderStore,
    private val isAuthenticated: () -> Boolean,
    private val launchEnabled: () -> Boolean,
    private val track: (String) -> Unit = { Analytics.track(it) }
) {
    private val _state = MutableStateFlow<OrderState>(OrderState.Idle)
    val state: StateFlow<OrderState> = _state

    /** The order placed in THIS session, for the confirmation / "recent order" surfaces. Memory only; not history. */
    private val _recent = MutableStateFlow<CustomerOrder?>(null)
    val recent: StateFlow<CustomerOrder?> = _recent

    private var attemptQuoteId: String? = null
    private var generation = 0
    private var job: Job? = null
    private var launchReconcileDone = false

    // ---- public API (each enqueues a command) ----------------------------------------------------------------------

    /** "Place order". Single flight; needs a Ready quote and a launch-enabled build. */
    fun place() = command {
        when (_state.value) {
            OrderState.Placing, is OrderState.Ambiguous, is OrderState.Placed -> return@command
            else -> Unit
        }
        if (!isAuthenticated()) { _state.value = OrderState.Failed(OrderFailure.Unauthenticated); return@command }
        if (!launchEnabled()) { _state.value = OrderState.Failed(OrderFailure.NotLaunched); return@command }
        val quote = (quotes.state.value as? CheckoutState.Ready)?.quote
        // A quote without binding money (legacy) is never ordered: the customer has not seen an amount the backend will honour.
        if (quote == null || quote.money == null || !RemoteOrderDataSource.isValidQuoteId(quote.quoteId)) { _state.value = OrderState.Failed(OrderFailure.QuoteNotReady); return@command }
        begin(quote.quoteId, saveFirst = true)
        track(AnalyticsEvents.ORDER_PLACE_STARTED)
    }

    /** "Check order": re-POST the SAME quote. Never a new quote. */
    fun checkOrder() = command {
        if (_state.value !is OrderState.Ambiguous) return@command
        val quoteId = attemptQuoteId ?: pending.load() ?: run { _state.value = OrderState.Idle; return@command }
        begin(quoteId, saveFirst = false)
    }

    /**
     * ONE automatic reconciliation per app launch, for an attempt left unresolved by a previous process. Called only after a
     * session RESTORE; an interactive sign-in never reconciles (the record could belong to another customer). Not a new
     * purchase: it only asks whether the earlier order exists.
     */
    fun resumeAfterRestore() = command {
        if (launchReconcileDone) return@command
        launchReconcileDone = true
        // Not authenticated here means "no usable session YET", which is not a logout: the record is KEPT (it is deleted only by
        // an explicit logout, a definitive auth rejection, an interactive sign-in, or a resolved outcome).
        if (!isAuthenticated()) return@command
        val quoteId = pending.load() ?: return@command
        if (_state.value != OrderState.Idle) return@command
        begin(quoteId, saveFirst = false)
    }

    /** The confirmation or a conclusive failure was seen: back to Idle. */
    fun acknowledge() = command {
        if (_state.value is OrderState.Placed || _state.value is OrderState.Failed) _state.value = OrderState.Idle
    }

    /**
     * Session ended (logout or definitive rejection). Forget everything and DELETE the recovery record. Never POSTs. Tradeoff:
     * logging out abandons local recovery of an unresolved attempt, because the record carries no identity and must never be
     * reconciled under another customer.
     */
    fun signOut() = command {
        generation++
        job?.cancel(); job = null
        attemptQuoteId = null
        pending.clear()
        _recent.value = null
        _state.value = OrderState.SignedOut
    }

    /**
     * The stored credential was DEFINITIVELY refused during session restoration (the session is cleared). The recovery record
     * can never be reconciled, so it is deleted. No POST. Transient failures (offline/timeout/5xx) never reach here: the session
     * is preserved and the record survives.
     */
    fun onSessionRejected() = command {
        generation++
        job?.cancel(); job = null
        attemptQuoteId = null
        pending.clear()
        _recent.value = null
        _state.value = OrderState.SignedOut
    }

    /**
     * A NEW interactive sign-in (not a session restoration). The record carries no customer identity, so it cannot be proven to
     * belong to whoever just signed in: it is deleted and never reconciled under this session.
     */
    fun onInteractiveSignIn() = command {
        pending.clear()
        attemptQuoteId = null
        if (_state.value == OrderState.SignedOut) _state.value = OrderState.Idle
    }

    /**
     * One stored order by id, for the confirmation / current-session detail: this session's order if it is the one asked for,
     * otherwise `GET /orders/{id}`. This is NOT history (there is no list endpoint) and the id is never persisted for one.
     */
    suspend fun fetch(orderId: String): CustomerOrder? {
        _recent.value?.takeIf { it.orderId == orderId }?.let { return it }
        return try { source.getOrder(orderId) } catch (e: CancellationException) { throw e } catch (_: Throwable) { null }
    }

    // ---- internals -----------------------------------------------------------------------------------------------------

    private fun command(block: suspend () -> Unit) { scope.launch { block() } }

    private fun begin(quoteId: String, saveFirst: Boolean) {
        attemptQuoteId = quoteId
        if (saveFirst) pending.save(quoteId)                  // BEFORE the request leaves: a crash after sending is recoverable
        _state.value = OrderState.Placing
        val gen = generation
        job = scope.launch { run(quoteId, gen) }
    }

    private suspend fun run(quoteId: String, gen: Int) {
        var purchasedReconciled = false
        while (true) {
            val result = try {
                Result.success(source.placeCodOrder(quoteId))
            } catch (e: CancellationException) {
                throw e
            } catch (t: Throwable) {
                Result.failure(t)
            }
            if (gen != generation) return
            val order = result.getOrNull()
            if (order != null) { succeed(order); return }
            val f = result.exceptionOrNull()!!.toOrderFailure()
            when {
                // The earlier purchase may be THIS quote: reconcile the same quote once before calling it a failure.
                f is OrderFailure.CartAlreadyPurchased && !purchasedReconciled -> { purchasedReconciled = true; continue }
                // A 401 reaches us only after the one allowed refresh failed. If the session was cleared (definitive rejection) the
                // record is dead; if it was PRESERVED (the refresh failed transiently) the order may still exist: keep the record.
                f is OrderFailure.Unauthenticated ->
                    if (isAuthenticated()) { _state.value = OrderState.Ambiguous(OrderFailure.Unavailable); return }
                    else { forgetAttempt(); _state.value = OrderState.SignedOut; return }
                f.isAmbiguous -> { _state.value = OrderState.Ambiguous(f); return }               // pending record stays
                else -> {
                    forgetAttempt()                                                             // definitive: no order exists
                    // The reviewed money is no longer current: this quote can never be placed or re-sent again. The server cart is
                    // untouched; a NEW quote (new key) is created only when the customer reviews checkout.
                    if (f is OrderFailure.PayableChanged) quotes.invalidateNow(quoteId, StaleReason.PayableChanged)
                    _state.value = OrderState.Failed(f); track(AnalyticsEvents.ORDER_PLACE_FAILED); return
                }
            }
        }
    }

    private fun forgetAttempt() { attemptQuoteId = null; pending.clear() }

    private suspend fun succeed(order: CustomerOrder) {
        quotes.resetNow()                         // 1. the quote is spent: reset BEFORE the cart moves, so no misleading "stale" state
        forgetAttempt()                           // 2. the outcome is known: delete the recovery record
        _recent.value = order
        _state.value = OrderState.Placed(order)
        track(AnalyticsEvents.ORDER_PLACE_SUCCEEDED)
        cart.refreshAndAwait()                    // 3. take the SERVER cart as truth (emptied, or preserved if it changed after the quote)
    }
}

/**
 * Ties the order flow to the auth session.
 *  - A signed-in -> signed-out transition (explicit logout, or a definitive rejection that clears the session) forgets the attempt
 *    and deletes the recovery record. No POST.
 *  - A signed-out -> signed-in transition AFTER startup is an interactive sign-in: the record is deleted (no identity to match).
 *  - Nothing is inferred from the state the session happens to be in when the binding starts: [start] is told whether the
 *    restoration already produced a session, so an initial or still-restoring "not signed in yet" is never mistaken for a logout.
 */
class OrderSessionBinding(
    private val scope: CoroutineScope,
    private val active: kotlinx.coroutines.flow.Flow<Boolean>,
    private val store: OrderStore
) {
    fun start(initiallyActive: Boolean) {
        scope.launch {
            var was = initiallyActive
            active.collect { now ->
                if (now && !was) store.onInteractiveSignIn() else if (!now && was) store.signOut()
                was = now
            }
        }
    }
}
