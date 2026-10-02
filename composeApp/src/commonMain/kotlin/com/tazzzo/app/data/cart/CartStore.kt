package com.tazzzo.app.data.cart

import com.tazzzo.app.data.catalog.Pincode
import com.tazzzo.app.data.catalog.StockState
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch

sealed interface CartState {
    /** Guest, logged out, or session ended. Nothing is held. */
    data object SignedOut : CartState
    data object Idle : CartState
    data object Loading : CartState
    /** The server's cart, exactly as it last answered. */
    data class Loaded(val cart: ServerCart) : CartState
    data class Failed(val failure: CartFailure) : CartState
}

/** What the customer has asked for on a SKU that the server has not confirmed yet. */
sealed interface PendingTarget {
    data class Quantity(val quantity: Int) : PendingTarget
    data object Removing : PendingTarget
}

/** A one-shot message about the last action. */
enum class CartNotice {
    /** 412: "Your cart changed. Review it and try again." */
    Stale,
    CouldntUpdate,
    /** The server's own effective inventory maximum was reached (never the hidden cart cap). */
    MaxReached,
    ItemUnavailable,
    CartFull,
    /** An ambiguous failure was reconciled and the intended state is NOT in the cart. */
    NotApplied,
    ClientBug,
    AuthRequired,
    Unavailable
}

/**
 * The customer's cart — held in memory, owned by the BACKEND. This is not a second cart:
 *  - nothing is persisted and nothing is mutated locally; the state is always the last server answer;
 *  - every request carries the latest version the server returned (never `current + 1`);
 *  - ONE mutation is on the wire at a time. Repeated taps only move a pending target for that SKU
 *    (an absolute quantity, so coalescing is exact); a remove replaces a queued quantity so a stale PUT
 *    is never sent after it; different SKUs queue one after another along the single version chain;
 *  - 412: all pending intent is DISCARDED, the cart is re-read and replaced, and the customer retries
 *    explicitly. Nothing is replayed;
 *  - an ambiguous outcome (timeout, connection lost, 500) is never re-sent: the cart is re-read and the
 *    intent counts as done only if the cart already shows the intended absolute state;
 *  - every GET replaces the held version, because a GET can itself advance it (expiry housekeeping);
 *  - [addressId] is the only location input; a manual PIN is never a cart location.
 *
 * All internal state is touched only on [scope]'s serial dispatcher (every public mutator enqueues a
 * command onto it), so it needs no locks.
 */
class CartStore(
    private val scope: CoroutineScope,
    private val source: CartSource,
    private val isAuthenticated: () -> Boolean,
    private val addressId: () -> String?,
    /** A 404 can mean the selected address is gone: let the address book re-check. */
    private val onAddressSuspect: () -> Unit = {}
) {
    private val _state = MutableStateFlow<CartState>(CartState.Idle)
    val state: StateFlow<CartState> = _state

    private val _pending = MutableStateFlow<Map<String, PendingTarget>>(emptyMap())
    val pending: StateFlow<Map<String, PendingTarget>> = _pending

    private val _syncing = MutableStateFlow(false)
    val syncing: StateFlow<Boolean> = _syncing

    private val _notice = MutableStateFlow<CartNotice?>(null)
    val notice: StateFlow<CartNotice?> = _notice

    private sealed interface Intent {
        data class Set(val quantity: Int) : Intent
        data object Remove : Intent
        data object Clear : Intent
    }

    private val desired = LinkedHashMap<String, Intent>()
    private var inFlight: Pair<String, Intent>? = null
    private var clearRequested = false
    private var refreshRequested = false
    private var running = false
    private var worker: Job? = null
    private var generation = 0

    // ---- public API (each enqueues a command) --------------------------------------------------------------

    fun load() = command {
        if (!isAuthenticated()) { _state.value = CartState.SignedOut; return@command }
        when (_state.value) {
            CartState.Idle, CartState.SignedOut, is CartState.Failed -> { refreshRequested = true; kick() }
            else -> Unit
        }
    }

    /** Re-read the cart (retry, pull-to-refresh, location change). */
    fun refresh() = command {
        if (!isAuthenticated()) { _state.value = CartState.SignedOut; return@command }
        refreshRequested = true; kick()
    }

    /**
     * One more unit of [skuId]. [maxHint] is the catalogue card's own limit for the very first add; once the line
     * exists, the cart line's server-supplied `maxOrderQuantity` is used (only when availability is known).
     */
    fun increment(skuId: String, maxHint: Int? = null) = command {
        if (!allowed(skuId)) return@command
        val target = targetQuantity(skuId) + 1
        val cap = knownCap(skuId) ?: maxHint?.takeIf { it > 0 }
        if (cap != null && target > cap) { _notice.value = CartNotice.MaxReached; return@command }
        enqueue(skuId, Intent.Set(target))
    }

    fun decrement(skuId: String) = command {
        if (!allowed(skuId)) return@command
        val current = targetQuantity(skuId)
        if (current <= 0) return@command
        enqueue(skuId, if (current - 1 <= 0) Intent.Remove else Intent.Set(current - 1))
    }

    fun setQuantity(skuId: String, quantity: Int) = command {
        if (!allowed(skuId)) return@command
        enqueue(skuId, if (quantity <= 0) Intent.Remove else Intent.Set(quantity))
    }

    fun remove(skuId: String) = command {
        if (!allowed(skuId)) return@command
        enqueue(skuId, Intent.Remove)
    }

    /** Clears the SERVER cart. Not called by logout: signing out only forgets the local copy. */
    fun clear() = command {
        if (!isAuthenticated()) { _notice.value = CartNotice.AuthRequired; return@command }
        _notice.value = null
        desired.clear()
        clearRequested = true
        publishPending(); kick()
    }

    fun dismissNotice() { _notice.value = null }

    /** Session ended (logout or definitive rejection): forget everything locally. The server cart is untouched. */
    fun signOut() = command {
        generation++
        desired.clear(); inFlight = null; clearRequested = false; refreshRequested = false
        worker?.cancel(); worker = null; running = false
        _state.value = CartState.SignedOut
        _pending.value = emptyMap(); _syncing.value = false; _notice.value = null
    }

    /** The quantity the server last confirmed for [skuId] (what to display as committed). */
    fun confirmedQuantity(skuId: String): Int = (_state.value as? CartState.Loaded)?.cart?.quantityOf(skuId) ?: 0

    // ---- internals --------------------------------------------------------------------------------------------

    private fun command(block: () -> Unit) { scope.launch { block() } }

    private fun loaded(): ServerCart? = (_state.value as? CartState.Loaded)?.cart

    private fun allowed(skuId: String): Boolean {
        if (!isAuthenticated()) { _notice.value = CartNotice.AuthRequired; return false }
        if (!RemoteCartDataSource.isValidSku(skuId)) return false
        _notice.value = null
        return true
    }

    private fun targetQuantity(skuId: String): Int {
        val i = desired[skuId] ?: inFlight?.takeIf { it.first == skuId }?.second
        return when (i) {
            is Intent.Set -> i.quantity
            Intent.Remove -> 0
            else -> loaded()?.quantityOf(skuId) ?: 0
        }
    }

    /** The server's own limit for this line — only when its availability is actually known. */
    private fun knownCap(skuId: String): Int? {
        val line = loaded()?.item(skuId) ?: return null
        val known = line.serviceable == true && (line.stockState == StockState.IN_STOCK || line.stockState == StockState.LOW_STOCK)
        return if (known && line.maxOrderQuantity > 0) line.maxOrderQuantity else null
    }

    private fun enqueue(skuId: String, intent: Intent) {
        desired[skuId] = intent
        publishPending(); kick()
    }

    private fun publishPending() {
        val m = LinkedHashMap<String, PendingTarget>()
        inFlight?.let { (sku, i) -> toTarget(i)?.let { m[sku] = it } }
        desired.forEach { (sku, i) -> toTarget(i)?.let { m[sku] = it } }
        _pending.value = m
    }

    private fun toTarget(i: Intent): PendingTarget? = when (i) {
        is Intent.Set -> PendingTarget.Quantity(i.quantity)
        Intent.Remove -> PendingTarget.Removing
        Intent.Clear -> null
    }

    private fun kick() {
        if (running) return
        running = true
        val gen = generation
        worker = scope.launch {
            try { pump() } finally { if (gen == generation) running = false }
        }
    }

    private fun hasWork() = desired.isNotEmpty() || clearRequested

    private suspend fun pump() {
        while (true) {
            if (!isAuthenticated()) { wipe(); return }
            val gen = generation
            if (refreshRequested || (loaded() == null && hasWork())) {
                refreshRequested = false
                reload(gen)
                if (gen != generation) return
                continue
            }
            if (clearRequested) {
                clearRequested = false
                runClear(gen)
                if (gen != generation) return
                continue
            }
            val e = desired.entries.firstOrNull() ?: run { _syncing.value = false; return }
            val sku = e.key
            val intent = e.value
            desired.remove(sku)
            runIntent(sku, intent, gen)
            if (gen != generation) return
        }
    }

    private fun wipe() {
        generation++
        desired.clear(); inFlight = null; clearRequested = false; refreshRequested = false
        _state.value = CartState.SignedOut; _pending.value = emptyMap(); _syncing.value = false
    }

    private suspend fun reload(gen: Int) {
        if (_state.value !is CartState.Loaded) _state.value = CartState.Loading
        _syncing.value = true
        try {
            val cart = source.get(addressId())
            if (gen == generation) _state.value = CartState.Loaded(cart)         // replaces the held version
        } catch (e: CancellationException) {
            throw e
        } catch (e: Throwable) {
            if (gen == generation) {
                val f = e.toCartFailure()
                if (f is CartFailure.Unauthenticated) _notice.value = CartNotice.AuthRequired
                _state.value = CartState.Failed(f)
                desired.clear(); clearRequested = false; publishPending()
            }
        } finally {
            if (gen == generation) _syncing.value = false
        }
    }

    private suspend fun runIntent(sku: String, intent: Intent, gen: Int) {
        val cart = loaded() ?: return
        val serverQty = cart.quantityOf(sku)
        when (intent) {
            is Intent.Set -> if (intent.quantity == serverQty) { publishPending(); return }      // already so: send nothing
            Intent.Remove -> if (serverQty == 0) { publishPending(); return }
            Intent.Clear -> return
        }
        inFlight = sku to intent
        _syncing.value = true
        publishPending()
        try {
            val next = when (intent) {
                is Intent.Set -> source.setQuantity(sku, intent.quantity, cart.version, addressId())
                Intent.Remove -> source.removeItem(sku, cart.version, addressId())
                Intent.Clear -> return
            }
            if (gen == generation) _state.value = CartState.Loaded(next)                        // the response IS the cart
        } catch (e: CancellationException) {
            throw e
        } catch (e: Throwable) {
            if (gen == generation) onFailure(sku, intent, e.toCartFailure(), gen)
        } finally {
            if (gen == generation) { inFlight = null; publishPending(); if (desired.isEmpty() && !clearRequested) _syncing.value = false }
        }
    }

    private suspend fun runClear(gen: Int) {
        val cart = loaded() ?: return
        _syncing.value = true
        try {
            val next = source.clear(cart.version, addressId())
            if (gen == generation) _state.value = CartState.Loaded(next)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Throwable) {
            if (gen == generation) onFailure(null, Intent.Clear, e.toCartFailure(), gen)
        } finally {
            if (gen == generation && desired.isEmpty()) _syncing.value = false
        }
    }

    private suspend fun onFailure(sku: String?, intent: Intent, f: CartFailure, gen: Int) {
        when {
            f is CartFailure.PreconditionFailed -> {
                // Someone else changed the cart. Discard every pending intent; show the server's truth; the customer retries.
                desired.clear(); clearRequested = false; publishPending()
                refetch(gen) { CartNotice.Stale }
            }
            f is CartFailure.NotFound -> {
                onAddressSuspect()
                refetch(gen) { if (intent is Intent.Remove) null else CartNotice.ItemUnavailable }
            }
            f is CartFailure.ItemLimitReached -> refetch(gen) { CartNotice.CartFull }
            f is CartFailure.InvalidRequest -> refetch(gen) { cart ->
                // Never infer the hidden cart cap. Say "maximum" only if the cart itself shows the server's inventory limit reached.
                val line = sku?.let { cart.item(it) }
                if (line != null && line.maxOrderQuantity > 0 && line.quantity >= line.maxOrderQuantity && line.serviceable == true) CartNotice.MaxReached
                else CartNotice.CouldntUpdate
            }
            f is CartFailure.ClientBug -> refetch(gen) { CartNotice.ClientBug }
            f is CartFailure.Unauthenticated -> { desired.clear(); clearRequested = false; publishPending(); _notice.value = CartNotice.AuthRequired }
            f.isAmbiguous -> reconcile(intent, sku, gen)
            f is CartFailure.Unavailable -> _notice.value = CartNotice.Unavailable          // not applied; version unchanged
            else -> _notice.value = CartNotice.CouldntUpdate
        }
    }

    private suspend fun refetch(gen: Int, notice: (ServerCart) -> CartNotice?) {
        try {
            val cart = source.get(addressId())
            if (gen == generation) { _state.value = CartState.Loaded(cart); _notice.value = notice(cart) }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Throwable) {
            if (gen == generation) _state.value = CartState.Failed(e.toCartFailure())
        }
    }

    /**
     * The outcome of the request is unknown. NEVER resend: read the cart, and count the intent as done only if the
     * cart already shows the intended ABSOLUTE state (valid because PUT / DELETE express a final state).
     */
    private suspend fun reconcile(intent: Intent, sku: String?, gen: Int) = refetch(gen) { cart ->
        val satisfied = when (intent) {
            is Intent.Set -> sku != null && cart.quantityOf(sku) == intent.quantity
            Intent.Remove -> sku != null && cart.quantityOf(sku) == 0
            Intent.Clear -> cart.isEmpty
        }
        if (satisfied) null else CartNotice.NotApplied
    }
}

/**
 * Connects the auth session and the delivery location to the cart:
 *  - signed in  -> load; signed out (logout OR definitive rejection) -> forget the local copy (the server cart stays);
 *  - the selected address changes / is cleared, or the selected address' PIN changes -> re-read, because line
 *    availability depends on `addressId`. A manual PIN with no selected address is NOT a cart location, so it does nothing.
 */
class CartSessionBinding(
    private val scope: CoroutineScope,
    private val active: Flow<Boolean>,
    private val selectedAddressId: Flow<String?>,
    private val pin: Flow<Pincode>,
    private val cart: CartStore
) {
    fun start() {
        scope.launch {
            var was = false
            active.collect { now ->
                if (now && !was) cart.load() else if (!now && was) cart.signOut()
                was = now
            }
        }
        scope.launch {
            var last: Pair<String?, String>? = null
            combine(selectedAddressId, pin) { id, p -> id to p.value }.collect { cur ->
                val prev = last
                last = cur
                if (prev != null && (cur.first != prev.first || (cur.first != null && cur.second != prev.second))) cart.refresh()
            }
        }
    }
}
