package com.tazzzo.app.checkout

import com.tazzzo.app.cart.cartOf
import com.tazzzo.app.cart.line
import com.tazzzo.app.cart.rs
import com.tazzzo.app.data.cart.CartAccess
import com.tazzzo.app.data.cart.CartState
import com.tazzzo.app.data.cart.PendingTarget
import com.tazzzo.app.data.cart.ServerCart
import com.tazzzo.app.data.checkout.AddressSelection
import com.tazzzo.app.data.checkout.AddressStamp
import com.tazzzo.app.data.checkout.BenefitPreviewState
import com.tazzzo.app.data.checkout.CheckoutAddressSource
import com.tazzzo.app.data.checkout.CheckoutQuote
import com.tazzzo.app.data.checkout.CheckoutQuoteItem
import com.tazzzo.app.data.checkout.QuoteSource
import com.tazzzo.app.data.checkout.stamp
import com.tazzzo.app.address.ca
import com.tazzzo.app.data.address.AddressServiceability
import com.tazzzo.app.data.remote.ApiError
import com.tazzzo.app.data.remote.ApiException
import com.tazzzo.app.data.remote.ItemError
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

fun hx(status: Int, code: String? = null, items: List<ItemError> = emptyList(), retryAfter: Long? = null) =
    ApiException(ApiError.Http(status, code, items = items, retryAfterSeconds = retryAfter))

/** The selected address as the quote store sees it. Version and content are part of its identity. */
fun stampOf(id: String = "ADDR_abcdef1", version: Long = 4, postal: String = "560102", line1: String = "22, 14th Main"): AddressStamp =
    ca(id = id, version = version, postal = postal).copy(addressLine1 = line1).stamp()

fun selected(stamp: AddressStamp = stampOf()) = AddressSelection.Selected(stamp, AddressServiceability.SERVICEABLE)

class FakeAddresses(initial: AddressSelection = selected()) : CheckoutAddressSource {
    val flow = MutableStateFlow(initial)
    override val selection: StateFlow<AddressSelection> = flow
}

/** A cart as the quote store sees it. Only the read side exists: checkout can never mutate the cart. */
open class FakeCartAccess(initial: CartState = CartState.Loaded(cartOf(5, line("TZP-1", 2, unit = 50)))) : CartAccess {
    val flow = MutableStateFlow(initial)
    val pendingFlow = MutableStateFlow<Map<String, PendingTarget>>(emptyMap())
    var refreshes = 0
    /** What the server cart looks like after the NEXT refresh. */
    var onRefresh: (CartState) -> CartState = { it }
    /** Called at the start of every refresh (lets a test record ordering against other collaborators). */
    var onRefreshHook: () -> Unit = {}
    override val state: StateFlow<CartState> = flow
    override val pending: StateFlow<Map<String, PendingTarget>> = pendingFlow
    override suspend fun refreshAndAwait(): CartState { onRefreshHook(); refreshes++; flow.value = onRefresh(flow.value); return flow.value }
    fun version() = (flow.value as CartState.Loaded).cart.version
    fun setCart(c: ServerCart) { flow.value = CartState.Loaded(c) }
}

class QuoteCall(val cartVersion: Long, val addressId: String, val key: String)

/**
 * A scripted backend that enforces the REAL rules: per-customer key -> (cartVersion, addressId) fingerprint, replay returns
 * the ORIGINAL quote, a different fingerprint is 409, an expired replay is 410, and rejections store nothing.
 */
class FakeQuoteSource(private val cart: FakeCartAccess) : QuoteSource {
    var nowMs = 1_000_000L
    var lifetimeMs = 300_000L
    val calls = mutableListOf<QuoteCall>()
    private val stored = HashMap<String, Pair<Pair<Long, String>, CheckoutQuote>>()
    private val failures = ArrayDeque<Pair<Throwable, Boolean>>()
    var gate: CompletableDeferred<Unit>? = null
    var benefit: BenefitPreviewState = BenefitPreviewState.NotApplied
    var tamperCartVersion = false
    /** false = the server accepted the request at the version it was sent with, before the cart moved on. */
    var enforceVersion = true
    private var seq = 0

    /** Fail the next call; [applied] = the quote was nevertheless created (a lost response). */
    fun failNext(e: Throwable, applied: Boolean = false) { failures.addLast(e to applied) }
    fun quotesCreated() = stored.size

    private fun build(version: Long, addressId: String): CheckoutQuote {
        val c = (cart.flow.value as? CartState.Loaded)?.cart ?: ServerCart.EMPTY
        val items = c.items.map { CheckoutQuoteItem(it.skuId, it.quantity, it.unitPrice!!, it.lineTotal!!) }
        return CheckoutQuote(
            quoteId = "CHKQ_${++seq}abcdef", cartVersion = if (tamperCartVersion) version + 1 else version, addressId = addressId, items = items,
            itemCount = c.itemCount, distinctItemCount = items.size, subtotal = c.subtotal, currency = "INR",
            createdAtMillis = nowMs, expiresAtMillis = nowMs + lifetimeMs, benefit = benefit, requestId = "req_$seq"
        )
    }

    override suspend fun createQuote(cartVersion: Long, addressId: String, idempotencyKey: String): CheckoutQuote {
        calls += QuoteCall(cartVersion, addressId, idempotencyKey)
        gate?.await()
        val fp = cartVersion to addressId
        stored[idempotencyKey]?.let { (sfp, q) ->                      // the replay lookup comes BEFORE the cart-version check
            if (sfp != fp) throw hx(409, "IDEMPOTENCY_CONFLICT")
            if (q.expiresAtMillis <= nowMs) throw hx(410, "QUOTE_EXPIRED")
            return q
        }
        val f = failures.removeFirstOrNull()
        if (f != null) {
            if (f.second) stored[idempotencyKey] = fp to build(cartVersion, addressId)
            throw f.first
        }
        if (enforceVersion && cart.version() != cartVersion) throw hx(412, "PRECONDITION_FAILED")
        val q = build(cartVersion, addressId)
        stored[idempotencyKey] = fp to q
        return q
    }

    override suspend fun getQuote(quoteId: String): CheckoutQuote = stored.values.map { it.second }.first { it.quoteId == quoteId }
}
