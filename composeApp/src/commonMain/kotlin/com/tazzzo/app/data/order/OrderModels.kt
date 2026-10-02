package com.tazzzo.app.data.order

import com.tazzzo.app.data.model.Money
import com.tazzzo.app.data.remote.ApiError
import com.tazzzo.app.data.remote.ApiException

/*
 * The REAL customer order (`/v1/customer/orders`). The backend order is the only truth: nothing here comes from the
 * mock `Order`, the local cart or `BillCalculator`. Today the order carries per-line prices and an ITEM SUBTOTAL only:
 * no delivery/platform fee, tax, discount, COD charge or payable amount exists on the wire, so none exists here.
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
    /** The sum of the line prices. NOT an amount due. */
    val subtotal: Money,
    val currency: String,
    val deliveryAddress: OrderDeliveryAddress?,
    val createdAtMillis: Long?,
    val confirmedAtMillis: Long?
) {
    override fun toString(): String = "CustomerOrder(${items.size} lines)"
}

/** Everything that can go wrong placing an order, as data. Never carries server text. */
sealed interface OrderFailure {
    // ---- decided by the app before any request ----
    /** Production order placement is not launch-enabled (no authoritative payable yet). */
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
    /** 409 CART_VERSION_ALREADY_PURCHASED — reconciled against the original quote before it is reported. */
    data object CartAlreadyPurchased : OrderFailure
    /** 400 / 415: the app sent something the contract forbids (including an unsupported payment method). */
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
            400, 415 -> OrderFailure.ClientBug
            409 -> when (api.code) {
                "ADDRESS_CHANGED" -> OrderFailure.AddressChanged
                "NOT_SERVICEABLE" -> OrderFailure.NotServiceable
                "PRICE_CHANGED" -> OrderFailure.PriceChanged
                "PRODUCT_UNAVAILABLE" -> OrderFailure.ProductUnavailable
                "STOCK_UNAVAILABLE" -> OrderFailure.StockUnavailable
                "RESERVATION_EXPIRED" -> OrderFailure.ReservationExpired
                "CART_VERSION_ALREADY_PURCHASED" -> OrderFailure.CartAlreadyPurchased
                else -> OrderFailure.Unknown
            }
            503 -> OrderFailure.Unavailable
            in 500..599 -> OrderFailure.Server
            else -> OrderFailure.Unknown
        }
    }
}
