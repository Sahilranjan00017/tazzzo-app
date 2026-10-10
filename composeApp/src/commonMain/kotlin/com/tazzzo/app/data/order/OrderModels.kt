package com.tazzzo.app.data.order

import com.tazzzo.app.data.model.Money
import com.tazzzo.app.data.model.PayableMoney
import com.tazzzo.app.data.remote.ApiError
import com.tazzzo.app.data.remote.ApiException

/*
 * The REAL customer order (`/v1/customer/orders`). The backend order is the only truth: nothing here comes from the
 * mock `Order`, the local cart or `BillCalculator`. Its money is the AUTHORITATIVE `money` block committed at placement
 * (equal to the quote's binding money). No delivery/platform fee, tax, COD charge, coupon, Coins or wallet exists on the
 * wire, so none exists here.
 */

/** The backend only ever returns `CONFIRMED`. Anything else is kept as unrecognized and never guessed at. */
enum class CustomerOrderStatus { CONFIRMED, UNRECOGNIZED;
    companion object { fun of(raw: String?) = if (raw == "CONFIRMED") CONFIRMED else UNRECOGNIZED }
}

enum class CustomerPaymentMethod { COD, UNRECOGNIZED;
    companion object { fun of(raw: String?) = if (raw == "COD") COD else UNRECOGNIZED }
}

/**
 * `COD_DUE` = confirmed, inventory consumed, payment OWED ON DELIVERY, nothing collected. It is NEVER "paid". An unknown
 * value fails closed to neutral copy.
 */
enum class OrderPaymentCondition { COD_DUE, UNRECOGNIZED;
    companion object { fun of(raw: String?) = if (raw == "COD_DUE") COD_DUE else UNRECOGNIZED }
}

data class CustomerOrderItem(
    val skuId: String,
    val title: String,
    val brandCode: String?,
    val quantity: Int,
    val unitPrice: Money,
    val lineTotal: Money
) {
    override fun toString(): String = "CustomerOrderItem(***)"
}

/** The address as it was FROZEN into the order (no coordinates, no address id). */
data class OrderDeliveryAddress(
    val label: String?, val recipientName: String?, val recipientPhone: String?, val addressLine1: String?,
    val addressLine2: String?, val landmark: String?, val city: String?, val state: String?, val postalCode: String?
) {
    override fun toString(): String = "OrderDeliveryAddress(***)"
}

data class CustomerOrder(
    val orderId: String,
    val status: CustomerOrderStatus,
    val paymentMethod: CustomerPaymentMethod,
    val paymentCondition: OrderPaymentCondition,
    val items: List<CustomerOrderItem>,
    val itemCount: Int,
    /** The sum of the line prices (the item subtotal). Equals [money]'s merchandise subtotal. */
    val subtotal: Money,
    val currency: String,
    /**
     * The AUTHORITATIVE money: `payable` is due on delivery (COD_DUE), never "paid". Null = an order created before the money
     * model: no amount due is derived for it, and it is never a zero amount.
     */
    val money: PayableMoney?,
    val deliveryAddress: OrderDeliveryAddress?,
    val createdAtMillis: Long?,
    val confirmedAtMillis: Long?
) {
    override fun toString(): String = "CustomerOrder(${items.size} lines)"
}

/** Everything that can go wrong placing an order, as data. Never carries server text. */
sealed interface OrderFailure {
    // ---- decided by the app before any request ----
    /** Production order placement is not launch-enabled (deployment / end-to-end sign-off pending). */
    data object NotLaunched : OrderFailure
    /** There is no Ready, current quote to order from. */
    data object QuoteNotReady : OrderFailure

    // ---- definitive: the backend answered, and NO order exists for this quote ----
    data object Unauthenticated : OrderFailure
    data object QuoteExpired : OrderFailure
    data object NotFound : OrderFailure
    data object AddressChanged : OrderFailure
    data object NotServiceable : OrderFailure
    data object PriceChanged : OrderFailure
    data object ProductUnavailable : OrderFailure
    data object StockUnavailable : OrderFailure
    data object ReservationExpired : OrderFailure
    /**
     * 409 PAYABLE_CHANGED: the quote's binding money is no longer the current money (or the quote has none). NO order was
     * created. Definitive: the pending attempt is deleted, the quote is invalidated, and only a NEW quote (new key) that the
     * customer reviews and confirms may be ordered. Never Ambiguous, never "Check order", never re-sent.
     */
    data object PayableChanged : OrderFailure
    /** 409 CART_VERSION_ALREADY_PURCHASED — reconciled against the original quote before it is reported. */
    data object CartAlreadyPurchased : OrderFailure
    /**
     * 409 DELIVERY_SLOT_UNAVAILABLE: the chosen slot is full, closed or unknown; nothing was written. Definitive: review checkout
     * (a new quote) and pick another slot.
     */
    data object SlotUnavailable : OrderFailure
    /** 409 STALE_VERSION: the order changed under the caller; nothing was applied. Definitive: reload and review. */
    data object StaleVersion : OrderFailure
    /** 409 INVALID_TRANSITION: the order's status does not allow that change. Definitive; nothing was changed. */
    data object InvalidTransition : OrderFailure
    /** 409 ORDER_NOT_CANCELLABLE: the order's status no longer allows cancellation. Definitive (no cancel path in the app yet). */
    data object NotCancellable : OrderFailure
    /** 409 CANCELLATION_WINDOW_CLOSED: the customer cancellation window has passed or is closed. Definitive (no cancel path yet). */
    data object CancellationWindowClosed : OrderFailure
    /** 400 / 413 / 415: the app sent something the contract forbids (including an unsupported payment method or an oversized body). */
    data object ClientBug : OrderFailure

    // ---- ambiguous: the order may or may not exist ----
    data object Unavailable : OrderFailure
    data object Server : OrderFailure
    data object Network : OrderFailure
    data object Timeout : OrderFailure
    /** Includes an unreadable 2xx body and any status this build does not know. */
    data object Unknown : OrderFailure

    /**
     * The order may exist. Re-POSTing the SAME quote is idempotent and cannot create a second order (the backend's replay
     * lookup runs before every other check), so it is the reconciliation. Every definitive 4xx above proves NO order exists.
     */
    val isAmbiguous: Boolean get() = this is Unavailable || this is Server || this is Network || this is Timeout || this is Unknown
}

fun Throwable.toOrderFailure(): OrderFailure {
    val api = (this as? ApiException)?.error ?: return OrderFailure.Unknown
    return when (api) {
        ApiError.Network -> OrderFailure.Network
        ApiError.Timeout -> OrderFailure.Timeout
        is ApiError.Decoding -> OrderFailure.Unknown
        is ApiError.Http -> when (api.status) {
            401 -> OrderFailure.Unauthenticated
            404 -> OrderFailure.NotFound
            410 -> OrderFailure.QuoteExpired
            400, 413, 415 -> OrderFailure.ClientBug
            409 -> when (api.code) {
                "ADDRESS_CHANGED" -> OrderFailure.AddressChanged
                "NOT_SERVICEABLE" -> OrderFailure.NotServiceable
                "PRICE_CHANGED" -> OrderFailure.PriceChanged
                "PRODUCT_UNAVAILABLE" -> OrderFailure.ProductUnavailable
                "STOCK_UNAVAILABLE" -> OrderFailure.StockUnavailable
                "RESERVATION_EXPIRED" -> OrderFailure.ReservationExpired
                "CART_VERSION_ALREADY_PURCHASED" -> OrderFailure.CartAlreadyPurchased
                "PAYABLE_CHANGED" -> OrderFailure.PayableChanged
                "DELIVERY_SLOT_UNAVAILABLE" -> OrderFailure.SlotUnavailable
                "STALE_VERSION" -> OrderFailure.StaleVersion
                "INVALID_TRANSITION" -> OrderFailure.InvalidTransition
                "ORDER_NOT_CANCELLABLE" -> OrderFailure.NotCancellable
                "CANCELLATION_WINDOW_CLOSED" -> OrderFailure.CancellationWindowClosed
                else -> OrderFailure.Unknown
            }
            503 -> OrderFailure.Unavailable
            in 500..599 -> OrderFailure.Server
            else -> OrderFailure.Unknown
        }
    }
}
