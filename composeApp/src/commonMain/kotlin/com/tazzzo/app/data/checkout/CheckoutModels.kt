package com.tazzzo.app.data.checkout

import com.tazzzo.app.data.model.Money
import com.tazzzo.app.data.model.PayableMoney
import com.tazzzo.app.data.remote.ApiError
import com.tazzzo.app.data.remote.ApiException
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds

/*
 * The REAL checkout quote (`/v1/customer/checkout/quote`). A quote is an immutable server snapshot taken at
 * `createdAt`: it is NOT a stock reservation. Its [CheckoutQuote.money] (`moneyPreview`) is ADVISORY: placing an order
 * revalidates and computes the order's own authoritative money, which may differ (placed with 200, not an error; the app then
 * shows a "Your total changed" notice — see `payableChangeNotice`). There is no
 * delivery/platform fee, tax, tip, COD charge, coupon, Coins or wallet, address snapshot, product name/image/MRP, slot or
 * payment information, so none of those exist here. It is never persisted.
 */

/** One quoted line. The quote has no title/image: any display metadata comes from the cart, never from here. */
data class CheckoutQuoteItem(
    val skuId: String,
    val quantity: Int,
    val unitPrice: Money,
    val lineTotal: Money
) {
    override fun toString(): String = "CheckoutQuoteItem(***)"
}

/**
 * The Benefits decision stored with the quote, kept for wire compatibility only. The customer-facing discount comes from
 * [CheckoutQuote.money] (`moneyPreview.benefitDiscountPaise`); this is never shown, never subtracted from anything and
 * never used to derive an amount.
 */
sealed interface BenefitPreviewState {
    /** The field was absent: a legacy quote. NOT the same as [NotApplied]. */
    data object Legacy : BenefitPreviewState
    /** `applied = false`: evaluated, no applicable benefit (the backend does not say why). */
    data object NotApplied : BenefitPreviewState
    /** `applied = true`. */
    data class Applied(val discount: Money, val discountBps: Int) : BenefitPreviewState {
        override fun toString(): String = "Applied(***)"
    }
    /** Present but violating the contract. Kept apart so it can never be mistaken for a benefit. */
    data object Unreadable : BenefitPreviewState
}

data class CheckoutQuote(
    val quoteId: String,
    /** The cart version the quote was taken from. */
    val cartVersion: Long,
    val addressId: String,
    val items: List<CheckoutQuoteItem>,
    val itemCount: Int,
    val distinctItemCount: Int,
    /** The sum of current line prices at `createdAt` (the item subtotal). Equals [money]'s merchandise subtotal. */
    val subtotal: Money,
    val currency: String,
    val createdAtMillis: Long,
    val expiresAtMillis: Long,
    val benefit: BenefitPreviewState,
    /**
     * The ADVISORY money preview (`moneyPreview`). Null = a LEGACY quote created before the money model: that is NOT a zero
     * amount, and the app never orders such a quote — the customer must refresh checkout.
     */
    val money: PayableMoney?,
    val requestId: String?
) {
    /** How long the SERVER says this quote lives, from its own two timestamps (never the device clock). */
    val lifetime: Duration get() = (expiresAtMillis - createdAtMillis).milliseconds

    override fun toString(): String = "CheckoutQuote(${items.size} lines)"
}

/** The backend's closed per-line rejection reasons; an unknown one is kept, never guessed. */
sealed interface ItemRejection {
    val skuId: String
    data class Known(override val skuId: String, val reason: Reason) : ItemRejection { override fun toString() = "Rejection(***)" }
    data class Unrecognized(override val skuId: String) : ItemRejection { override fun toString() = "Rejection(***)" }

    enum class Reason { PRODUCT_UNAVAILABLE, PRICE_UNAVAILABLE, OUT_OF_STOCK, INSUFFICIENT_STOCK, STOCK_UNKNOWN, NOT_BUYABLE }

    companion object {
        fun of(skuId: String, raw: String?): ItemRejection =
            Reason.entries.firstOrNull { it.name == raw }?.let { Known(skuId, it) } ?: Unrecognized(skuId)
    }
}

/** Everything that can go wrong creating a quote, as data. Never carries server text. */
sealed interface CheckoutFailure {
    // ---- decided by the app before any request ----
    data object AddressRequired : CheckoutFailure
    data object CartUnavailable : CheckoutFailure
    data object CartBusy : CheckoutFailure
    /** The server cart itself reports blocked lines; sending it would only produce a 409. */
    data object CartHasIssues : CheckoutFailure

    // ---- the server's answers ----
    data object Unauthenticated : CheckoutFailure
    /** 412: the cart changed since it was read. */
    data object CartChanged : CheckoutFailure
    /** 409 CHECKOUT_CART_EMPTY */
    data object CartEmpty : CheckoutFailure
    /** 409 CHECKOUT_UNSERVICEABLE */
    data object Unserviceable : CheckoutFailure
    /** 409 CHECKOUT_ITEM_UNAVAILABLE — all-or-nothing, with the server's per-line reasons. */
    data class ItemsUnavailable(val items: List<ItemRejection>) : CheckoutFailure
    /** 409 IDEMPOTENCY_CONFLICT: an app key-lifecycle bug. */
    data object KeyConflict : CheckoutFailure
    /** 404: the address (or another contract entity) is unknown, foreign or changed. */
    data object NotFound : CheckoutFailure
    /** 410 QUOTE_EXPIRED */
    data object QuoteExpired : CheckoutFailure
    /** 400 / 415 / 428: the app sent something the contract forbids. Never retried. */
    data object ClientBug : CheckoutFailure
    data class RateLimited(val retryAfterSeconds: Long?) : CheckoutFailure
    /** 503 (including a temporary enrichment failure — never reported as an unavailable item). */
    data object Unavailable : CheckoutFailure
    data object Server : CheckoutFailure
    data object Network : CheckoutFailure
    data object Timeout : CheckoutFailure
    data object Unknown : CheckoutFailure
    /**
     * A 2xx quote that violates the contract (inconsistent money, a subtotal that disagrees with it, a quote for a cart or
     * address that was not asked for, an unreadable body). NOT ambiguous: replaying the same key returns the same stored
     * quote, so only a NEW attempt (new key) is offered.
     */
    data object ContractViolation : CheckoutFailure

    /**
     * The request may or may not have reached the backend. Backend replay semantics (same customer + same key + same
     * cartVersion/addressId returns the original quote, and rejections store nothing) make an EXPLICIT same-key retry safe.
     */
    val isAmbiguous: Boolean get() = this is Network || this is Timeout || this is Server || this is Unknown || this is Unavailable || this is RateLimited
}

fun Throwable.toCheckoutFailure(): CheckoutFailure {
    val api = (this as? ApiException)?.error ?: return CheckoutFailure.Unknown
    return when (api) {
        ApiError.Network -> CheckoutFailure.Network
        ApiError.Timeout -> CheckoutFailure.Timeout
        is ApiError.Decoding -> CheckoutFailure.ContractViolation
        is ApiError.Http -> when (api.status) {
            401 -> CheckoutFailure.Unauthenticated
            404 -> CheckoutFailure.NotFound
            410 -> CheckoutFailure.QuoteExpired
            412 -> CheckoutFailure.CartChanged
            400, 415, 428 -> CheckoutFailure.ClientBug
            429 -> CheckoutFailure.RateLimited(api.retryAfterSeconds)
            409 -> when (api.code) {
                "CHECKOUT_CART_EMPTY" -> CheckoutFailure.CartEmpty
                "CHECKOUT_UNSERVICEABLE" -> CheckoutFailure.Unserviceable
                "CHECKOUT_ITEM_UNAVAILABLE" ->
                    CheckoutFailure.ItemsUnavailable(api.items.mapNotNull { it.skuId?.let { sku -> ItemRejection.of(sku, it.reason) } })
                "IDEMPOTENCY_CONFLICT" -> CheckoutFailure.KeyConflict
                else -> CheckoutFailure.Unknown
            }
            503 -> CheckoutFailure.Unavailable
            in 500..599 -> CheckoutFailure.Server
            else -> CheckoutFailure.Unknown
        }
    }
}
