package com.tazzzo.app.data.checkout

import com.tazzzo.app.data.address.AddressServiceability
import com.tazzzo.app.data.address.BookState
import com.tazzzo.app.data.address.CustomerAddress
import com.tazzzo.app.data.cart.CartAccess
import com.tazzzo.app.data.cart.CartState
import com.tazzzo.app.data.catalog.Pincode
import com.tazzzo.app.data.remote.IdempotencyKey
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlin.time.Duration
import kotlin.time.Duration.Companion.ZERO
import kotlin.time.TimeMark
import kotlin.time.TimeSource

// ---- what "the same delivery address" means for a quote ------------------------------------------------------------

/** Everything about an address that goes into a delivery. `isDefault` is deliberately NOT here: it never affects a quote. */
data class DeliveryContent(
    val recipientName: String, val recipientPhone: String, val addressLine1: String, val addressLine2: String?,
    val landmark: String?, val city: String, val state: String, val postalCode: Pincode
) {
    override fun toString(): String = "DeliveryContent(***)"
}

/** The selected address as the quote saw it: id AND version AND content. A matching id alone is not enough. */
data class AddressStamp(val addressId: String, val version: Long, val delivery: DeliveryContent) {
    override fun toString(): String = "AddressStamp(***)"
}

fun CustomerAddress.stamp() = AddressStamp(
    addressId, version,
    DeliveryContent(recipientName, recipientPhone, addressLine1, addressLine2, landmark, city, state, postalCode)
)

sealed interface AddressSelection {
    data object None : AddressSelection
    /** An id is selected but the address book is not loaded right now: say nothing, change nothing. */
    data class Unknown(val addressId: String) : AddressSelection
    /** The selected id is no longer in the loaded address book. */
    data class Missing(val addressId: String) : AddressSelection
    data class Selected(val stamp: AddressStamp, val serviceability: AddressServiceability) : AddressSelection
}

fun addressSelection(selectedId: String?, book: BookState): AddressSelection {
    if (selectedId == null) return AddressSelection.None
    if (book !is BookState.Loaded) return AddressSelection.Unknown(selectedId)
    val a = book.addresses.firstOrNull { it.addressId == selectedId } ?: return AddressSelection.Missing(selectedId)
    return AddressSelection.Selected(a.stamp(), a.serviceability)
}

interface CheckoutAddressSource { val selection: StateFlow<AddressSelection> }

class DeliveryAddressSource(
    scope: CoroutineScope, selectedId: Flow<String?>, book: Flow<BookState>
) : CheckoutAddressSource {
    override val selection: StateFlow<AddressSelection> =
        combine(selectedId, book) { id, b -> addressSelection(id, b) }
            .stateIn(scope, SharingStarted.Eagerly, AddressSelection.None)
}

/** What the order flow needs from the quote store: the current quote, a synchronous reset after a placed order, and invalidation. */
interface CheckoutQuoteAccess {
    val state: StateFlow<CheckoutState>
    /** Forget the quote NOW. Must be called on the store's own scope (the order store shares it). */
    fun resetNow()
    /**
     * The backend refused an order from quote [quoteId] for [reason] (no order exists). If that quote is the current Ready
     * one it becomes [CheckoutState.Stale] NOW and its key is dropped, so it can never be placed or re-sent again. Nothing is
     * re-quoted automatically. Must be called on the store's own scope.
     */
    fun invalidateNow(quoteId: String, reason: StaleReason)
}

/** The inputs a quote was built from. Backend idempotency fingerprint = (cartVersion, addressId); the address version/content is client-side only. */
data class CheckoutSource(val cartVersion: Long, val address: AddressStamp) {
    override fun toString(): String = "CheckoutSource(***)"
}

// ---- state ----------------------------------------------------------------------------------------------------------

enum class StaleReason {
    CartChanged, AddressChanged, AddressRemoved,
    /** An order from this quote was refused with PAYABLE_CHANGED (defensive: the running backend does not send that code). */
    PayableChanged
}

sealed interface CheckoutState {
    data object SignedOut : CheckoutState
    data object Idle : CheckoutState
    data object Creating : CheckoutState
    /** A server quote that is still current for the cart and address it was built from. NOT a stock reservation. */
    data class Ready(val quote: CheckoutQuote, val source: CheckoutSource) : CheckoutState { override fun toString() = "Ready(***)" }
    data object Expired : CheckoutState
    /** The inputs moved on. The old quote is never treated as current; the customer must explicitly review again. */
    data class Stale(val reason: StaleReason) : CheckoutState
    /** [canRetrySameKey]: an explicit "Try again" may resend the SAME request with the SAME key. */
    data class Failed(val failure: CheckoutFailure, val canRetrySameKey: Boolean) : CheckoutState
}

/**
 * The checkout quote — in memory only, never persisted, authoritative from the BACKEND.
 *
 *  - A NEW intentional attempt (`start`) re-reads the server cart first (a cart GET can itself advance the version),
 *    requires a selected saved address, refuses a cart the server already reports as blocked, then POSTs with the
 *    version just returned and a freshly generated Idempotency-Key.
 *  - The key belongs to exactly one fingerprint (cartVersion + addressId). It is kept only for an EXPLICIT same-key
 *    retry of an ambiguous failure while the inputs are unchanged; it is dropped on any cart/address change, expiry,
 *    conflict or a new attempt. Nothing is retried automatically.
 *  - A Ready quote goes Stale the moment the cart version, the selected address id, or that address's version/content
 *    changes (a default-flag-only change does not). Nothing is re-quoted automatically.
 *  - Expiry is the SERVER's createdAt→expiresAt duration measured on a monotonic clock from the moment the request was
 *    sent; the server's 410 stays authoritative.
 *  - A quote is a snapshot, not a reservation, and quote creation never clears or mutates the server cart.
 *
 * Mutable state is touched only on [scope]'s serial dispatcher.
 */
class CheckoutQuoteStore(
    private val scope: CoroutineScope,
    private val source: QuoteSource,
    private val cart: CartAccess,
    private val addresses: CheckoutAddressSource,
    private val isAuthenticated: () -> Boolean,
    private val onAddressSuspect: () -> Unit = {},
    private val newKey: () -> String = { IdempotencyKey.generate() },
    private val clock: TimeSource = TimeSource.Monotonic
) : CheckoutQuoteAccess {
    private val _state = MutableStateFlow<CheckoutState>(CheckoutState.Idle)
    override val state: StateFlow<CheckoutState> = _state

    private class Attempt(val key: String, val source: CheckoutSource, var ambiguous: Boolean = false) {
        override fun toString() = "Attempt(***)"
    }

    private var attempt: Attempt? = null
    private var generation = 0
    private var job: Job? = null
    private var expiryJob: Job? = null
    private var deadline: TimeMark? = null

    init {
        scope.launch { combine(cart.state, addresses.selection) { _, _ -> }.collect { inputsChanged() } }
    }

    // ---- public API (each enqueues a command) --------------------------------------------------------------------------

    /** The customer tapped "Checkout": keep a still-valid quote or in-flight attempt, otherwise start a fresh one. */
    fun enter() = command {
        when (_state.value) {
            is CheckoutState.Ready, CheckoutState.Creating -> Unit
            else -> beginAttempt()
        }
    }

    /** "Review checkout" / "Refresh checkout" / start over: always a NEW attempt with a NEW key. */
    fun start() = command { if (_state.value != CheckoutState.Creating) beginAttempt() }

    /** "Try again": the same request with the same key only if it is still valid; otherwise a new attempt. */
    fun retry() = command {
        val a = attempt
        val f = _state.value as? CheckoutState.Failed
        if (a != null && a.ambiguous && f != null && f.canRetrySameKey && matchesCurrent(a.source)) {
            _state.value = CheckoutState.Creating
            val gen = generation
            job = scope.launch { post(gen) }
        } else if (_state.value != CheckoutState.Creating) beginAttempt()
    }

    /** An order was placed from this quote: it is spent. Back to Idle, key dropped, nothing re-requested. */
    override fun resetNow() = wipe(CheckoutState.Idle)

    override fun invalidateNow(quoteId: String, reason: StaleReason) {
        val cur = _state.value
        if (cur is CheckoutState.Ready && cur.quote.quoteId == quoteId) wipe(CheckoutState.Stale(reason))   // a newer quote is left alone
    }

    /** Session ended: forget everything. The server cart is untouched. */
    fun signOut() = command { wipe(CheckoutState.SignedOut) }

    fun onSignedIn() = command { if (_state.value == CheckoutState.SignedOut) _state.value = CheckoutState.Idle }

    /** Time left on a Ready quote, on the monotonic clock. Null when there is none. */
    fun remaining(): Duration? = deadline?.let { (-it.elapsedNow()).coerceAtLeast(ZERO) }

    // ---- internals ------------------------------------------------------------------------------------------------------

    private fun command(block: suspend () -> Unit) { scope.launch { block() } }

    private fun wipe(next: CheckoutState) {
        generation++
        job?.cancel(); job = null; expiryJob?.cancel(); expiryJob = null; deadline = null; attempt = null
        _state.value = next
    }

    private fun fail(f: CheckoutFailure, sameKey: Boolean = false) {
        if (!sameKey) attempt = null
        _state.value = CheckoutState.Failed(f, sameKey)
    }

    private fun beginAttempt() {
        if (!isAuthenticated()) { wipe(CheckoutState.SignedOut); return }
        generation++
        job?.cancel(); expiryJob?.cancel(); expiryJob = null; deadline = null; attempt = null
        val gen = generation
        if (addresses.selection.value !is AddressSelection.Selected) { fail(CheckoutFailure.AddressRequired); return }
        _state.value = CheckoutState.Creating
        job = scope.launch { prepareAndPost(gen) }
    }

    private suspend fun prepareAndPost(gen: Int) {
        val cs = cart.refreshAndAwait()
        if (gen != generation) return
        val c = (cs as? CartState.Loaded)?.cart
        when {
            cs is CartState.SignedOut -> { wipe(CheckoutState.SignedOut); return }
            c == null -> { fail(CheckoutFailure.CartUnavailable); return }
            c.isEmpty -> { fail(CheckoutFailure.CartEmpty); return }
            cart.pending.value.isNotEmpty() -> { fail(CheckoutFailure.CartBusy); return }
            c.items.any { it.isBlocked } -> { fail(CheckoutFailure.CartHasIssues); return }
        }
        val sel = addresses.selection.value as? AddressSelection.Selected
        if (sel == null) { fail(CheckoutFailure.AddressRequired); return }
        attempt = Attempt(newKey(), CheckoutSource(c!!.version, sel.stamp))
        post(gen)
    }

    private suspend fun post(gen: Int) {
        val a = attempt ?: return
        val sentAt = clock.markNow()
        val quote = try {
            source.createQuote(a.source.cartVersion, a.source.address.addressId, a.key)
        } catch (e: CancellationException) {
            throw e
        } catch (t: Throwable) {
            if (gen == generation) onFailure(t.toCheckoutFailure(), a)
            return
        }
        if (gen != generation) return
        if (quote.cartVersion != a.source.cartVersion || quote.addressId != a.source.address.addressId) {
            fail(CheckoutFailure.ContractViolation); return                        // a quote for something we did not ask for is never Ready
        }
        val remaining = quote.lifetime - sentAt.elapsedNow()
        if (remaining <= ZERO) { attempt = null; _state.value = CheckoutState.Expired; return }
        deadline = sentAt + quote.lifetime
        _state.value = CheckoutState.Ready(quote, a.source)
        expiryJob?.cancel()
        expiryJob = scope.launch {
            delay(remaining)
            if (gen == generation && _state.value is CheckoutState.Ready) { attempt = null; deadline = null; _state.value = CheckoutState.Expired }
        }
        inputsChanged()                                                            // the cart/address may have moved while the request was out
    }

    private suspend fun onFailure(f: CheckoutFailure, a: Attempt) {
        val gen = generation
        when {
            f is CheckoutFailure.Unauthenticated -> wipe(CheckoutState.SignedOut)
            f is CheckoutFailure.QuoteExpired -> { attempt = null; _state.value = CheckoutState.Expired }
            f is CheckoutFailure.CartChanged || f is CheckoutFailure.CartEmpty || f is CheckoutFailure.ItemsUnavailable -> {
                attempt = null
                fail(f)                                                            // show the outcome now,
                cart.refreshAndAwait()                                             // and bring the real cart (and its issues) up to date
            }
            f is CheckoutFailure.NotFound -> { attempt = null; fail(f); onAddressSuspect(); cart.refreshAndAwait() }
            f.isAmbiguous -> { a.ambiguous = true; fail(f, sameKey = true) }
            else -> fail(f)                                                        // Unserviceable, KeyConflict, ClientBug
        }
        if (gen != generation) return
    }

    private fun matchesCurrent(s: CheckoutSource): Boolean = staleReason(s) == null && currentKnown()

    private fun currentKnown(): Boolean =
        cart.state.value is CartState.Loaded && addresses.selection.value is AddressSelection.Selected

    /** Why [s] is no longer current, or null. Unknown/transient states (cart or address book reloading) never invalidate. */
    private fun staleReason(s: CheckoutSource): StaleReason? {
        val c = (cart.state.value as? CartState.Loaded)?.cart
        if (c != null && c.version != s.cartVersion) return StaleReason.CartChanged
        return when (val sel = addresses.selection.value) {
            AddressSelection.None, is AddressSelection.Missing -> StaleReason.AddressRemoved
            is AddressSelection.Unknown -> if (sel.addressId != s.address.addressId) StaleReason.AddressChanged else null
            is AddressSelection.Selected -> when {
                sel.stamp.addressId != s.address.addressId -> StaleReason.AddressChanged
                sel.stamp != s.address -> StaleReason.AddressChanged               // same id, new version or delivery content
                else -> null
            }
        }
    }

    private fun inputsChanged() {
        val cur = _state.value
        val a = attempt
        if (cur is CheckoutState.Ready) {
            staleReason(cur.source)?.let { reason ->
                attempt = null; expiryJob?.cancel(); expiryJob = null; deadline = null
                _state.value = CheckoutState.Stale(reason)
            }
        } else if (cur is CheckoutState.Failed && cur.canRetrySameKey && a != null && staleReason(a.source) != null) {
            attempt = null                                                         // the old key can never be reused for different inputs
            _state.value = CheckoutState.Failed(cur.failure, canRetrySameKey = false)
        }
    }
}

/** Ties checkout to the auth session: sign-out (logout or definitive rejection) forgets any quote. */
class CheckoutSessionBinding(
    private val scope: CoroutineScope,
    private val active: Flow<Boolean>,
    private val store: CheckoutQuoteStore
) {
    fun start() {
        scope.launch {
            var was = false
            active.collect { now ->
                if (now && !was) store.onSignedIn() else if (!now && was) store.signOut()
                was = now
            }
        }
    }
}
