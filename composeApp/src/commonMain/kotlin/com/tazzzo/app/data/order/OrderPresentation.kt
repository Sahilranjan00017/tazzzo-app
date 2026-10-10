package com.tazzzo.app.data.order

import com.tazzzo.app.data.checkout.CheckoutAction
import com.tazzzo.app.data.checkout.CheckoutCopy
import com.tazzzo.app.data.checkout.CheckoutState
import com.tazzzo.app.data.checkout.FailureView
import com.tazzzo.app.data.checkout.MoneyLine
import com.tazzzo.app.data.checkout.PAYABLE_CHANGED_VIEW
import com.tazzzo.app.data.checkout.moneyLines

/** What the Place-order control may do right now. The gating lives HERE and in capabilities — never inside [OrderStore]. */
sealed interface PlaceOrderAvailability {
    data object Available : PlaceOrderAvailability
    /** The order capability is off (fail-closed; never the case in REMOTE or MOCK): disabled, with neutral copy. */
    data object LaunchGated : PlaceOrderAvailability
    data object Placing : PlaceOrderAvailability
    /** An earlier attempt is unresolved: only "Check order" is offered, never a second order. */
    data object NeedsCheck : PlaceOrderAvailability
    data object NoReadyQuote : PlaceOrderAvailability
    /** The Ready quote carries no money preview (legacy): never orderable, only "Refresh checkout". */
    data object NeedsRefresh : PlaceOrderAvailability

    val enabled: Boolean get() = this is Available
}

fun placeOrderAvailability(gateOpen: Boolean, checkout: CheckoutState, order: OrderState): PlaceOrderAvailability = when {
    order is OrderState.Placing -> PlaceOrderAvailability.Placing
    order is OrderState.Ambiguous -> PlaceOrderAvailability.NeedsCheck
    checkout is CheckoutState.Ready && checkout.quote.money == null -> PlaceOrderAvailability.NeedsRefresh
    !gateOpen -> PlaceOrderAvailability.LaunchGated
    order is OrderState.Placed -> PlaceOrderAvailability.NoReadyQuote
    checkout !is CheckoutState.Ready -> PlaceOrderAvailability.NoReadyQuote
    else -> PlaceOrderAvailability.Available
}

/** App-written copy for every order failure. No server text, ever. */
fun OrderFailure.view(): FailureView = when (this) {
    OrderFailure.NotLaunched -> FailureView(CheckoutCopy.ORDERING_PAUSED, "Please try again later.", emptyList())
    OrderFailure.QuoteNotReady -> FailureView("Review your checkout first", "Your checkout needs to be refreshed.", listOf(CheckoutAction.ReviewCheckout))
    OrderFailure.Unauthenticated -> FailureView("Log in to continue", "Your session ended.", listOf(CheckoutAction.SignIn))
    OrderFailure.QuoteExpired -> FailureView("This checkout expired", "Refresh checkout to continue.", listOf(CheckoutAction.ReviewCheckout))
    OrderFailure.NotFound -> FailureView("We couldn't find this checkout", "Please start checkout again.", listOf(CheckoutAction.ReviewCheckout))
    OrderFailure.AddressChanged -> FailureView("Your delivery address changed", "Review checkout again to continue.", listOf(CheckoutAction.ReviewCheckout, CheckoutAction.ChangeAddress))
    OrderFailure.NotServiceable -> FailureView("This address isn't serviceable for this order.", "Choose a different delivery address.", listOf(CheckoutAction.ChangeAddress))
    OrderFailure.PriceChanged -> FailureView("Some prices changed", "Review checkout to see the latest.", listOf(CheckoutAction.ReviewCheckout))
    OrderFailure.ProductUnavailable -> FailureView("Some items are no longer available", "Review them in your cart. Nothing was removed.", listOf(CheckoutAction.GoToCart))
    OrderFailure.StockUnavailable -> FailureView("Some items are out of stock", "Review them in your cart. Nothing was removed.", listOf(CheckoutAction.GoToCart))
    OrderFailure.ReservationExpired -> FailureView("We couldn't hold your items in time", "Review checkout and try again.", listOf(CheckoutAction.ReviewCheckout))
    // Definitive: no order exists and this quote is dead. Only a NEW quote — never "Check order", never the same quote.
    OrderFailure.PayableChanged -> PAYABLE_CHANGED_VIEW
    OrderFailure.CartAlreadyPurchased -> FailureView("This cart was already ordered", "Check your cart for what is left.", listOf(CheckoutAction.GoToCart))
    OrderFailure.SlotUnavailable -> FailureView("That delivery slot isn't available", "Review checkout and choose another slot.", listOf(CheckoutAction.ReviewCheckout))
    OrderFailure.StaleVersion -> FailureView("This order changed", "Review checkout to see the latest.", listOf(CheckoutAction.ReviewCheckout))
    OrderFailure.InvalidTransition -> FailureView("That change isn't possible now", "Nothing was changed.", listOf(CheckoutAction.GoToCart))
    OrderFailure.NotCancellable -> FailureView("This order can't be cancelled", "Nothing was changed.", listOf(CheckoutAction.GoToCart))
    OrderFailure.CancellationWindowClosed -> FailureView("The cancellation window has closed", "Nothing was changed.", listOf(CheckoutAction.GoToCart))
    OrderFailure.ClientBug -> FailureView("Something went wrong", "Please update the app and try again.", listOf(CheckoutAction.GoToCart))
    OrderFailure.Unavailable, OrderFailure.Server, OrderFailure.Network, OrderFailure.Timeout, OrderFailure.Unknown ->
        FailureView("We couldn't confirm your order", "Your order may have been placed. Check before trying anything else.", listOf(CheckoutAction.CheckOrder))
}

/**
 * The customer-facing order. Built ONLY from the backend order and its AUTHORITATIVE money. COD_DUE is money due on
 * delivery, never "paid" — not even once DELIVERED (the backend does not record cash collection). A CANCELLED order has nothing
 * due. A legacy order (no money) shows its item subtotal alone: no amount due is derived.
 */
data class OrderLineView(val title: String, val quantity: Int, val unitPriceLabel: String, val lineTotalLabel: String)

/** One dated step the backend recorded (placed, out for delivery, delivered, cancelled). Never a projected/expected step. */
data class OrderTimelineStep(val label: String, val timeLabel: String)

data class OrderView(
    val title: String,
    val orderId: String,
    val paymentLine: String,
    /**
     * COD_DUE: `₹90 due on delivery` / `Nothing due on delivery` from the authoritative money, or the amount-free
     * `Payment due on delivery` for a legacy order. Status-specific for DELIVERED / CANCELLED. Null for an unrecognized payment
     * condition (neutral copy instead).
     */
    val dueLine: String?,
    val lines: List<OrderLineView>,
    val itemsLabel: String,
    /** Item subtotal, Benefit discount (only when > 0), Amount due — or the item subtotal alone for a legacy order. */
    val moneyLines: List<MoneyLine>,
    val addressLines: List<String>,
    val placedLabel: String? = null,
    val slotLabel: String? = null,
    val timeline: List<OrderTimelineStep> = emptyList()
) {
    /** The one line a compact surface shows: the amount due, or the item subtotal for a legacy order. */
    val headlineMoney: MoneyLine get() = moneyLines.last()
}

/** Copy for the order lifecycle. App-written only. */
object OrderStatusCopy {
    const val CANCELLED_NOTHING_DUE = "Cancelled · nothing is due"
    const val DELIVERED_COD = "Delivered · cash on delivery"
    /** The last money row of a cancelled or delivered order: it is no longer an amount "due". */
    const val ORDER_TOTAL = "Order total"
}

fun CustomerOrderStatus.title(): String = when (this) {
    CustomerOrderStatus.CONFIRMED -> "Order confirmed"
    CustomerOrderStatus.OUT_FOR_DELIVERY -> "Out for delivery"
    CustomerOrderStatus.DELIVERED -> "Delivered"
    CustomerOrderStatus.CANCELLED -> "Order cancelled"
    CustomerOrderStatus.UNRECOGNIZED -> "Order received"
}

/** Whether money is still owed on delivery for this status (never for DELIVERED or CANCELLED). */
internal val CustomerOrderStatus.awaitsDelivery: Boolean
    get() = this == CustomerOrderStatus.CONFIRMED || this == CustomerOrderStatus.OUT_FOR_DELIVERY || this == CustomerOrderStatus.UNRECOGNIZED

fun CustomerOrder.view(): OrderView = OrderView(
    title = status.title(),
    orderId = orderId,
    paymentLine = if (paymentMethod == CustomerPaymentMethod.COD) "Cash on delivery" else "Payment",
    dueLine = when {
        status == CustomerOrderStatus.CANCELLED -> OrderStatusCopy.CANCELLED_NOTHING_DUE
        status == CustomerOrderStatus.DELIVERED -> OrderStatusCopy.DELIVERED_COD
        paymentCondition != OrderPaymentCondition.COD_DUE -> null
        else -> money?.let { CheckoutCopy.dueOnDelivery(it) } ?: "Payment due on delivery"
    },
    lines = items.map { OrderLineView(it.title, it.quantity, it.unitPrice.format(), it.lineTotal.format()) },
    itemsLabel = if (itemCount == 1) "1 item" else "$itemCount items",
    moneyLines = moneyLines(money, subtotal).let { rows ->
        if (status.awaitsDelivery) rows
        else rows.map { if (it.label == CheckoutCopy.AMOUNT_DUE_LABEL) it.copy(label = OrderStatusCopy.ORDER_TOTAL) else it }
    },
    addressLines = deliveryAddress?.let { a ->
        listOfNotNull(
            a.recipientName, a.addressLine1, a.addressLine2?.takeIf { it.isNotBlank() }, a.landmark?.takeIf { it.isNotBlank() },
            listOfNotNull(a.city, a.state, a.postalCode).joinToString(" ").ifBlank { null }
        )
    }.orEmpty(),
    placedLabel = (confirmedAtMillis ?: createdAtMillis)?.let { OrderTime.label(it) },
    slotLabel = deliverySlotLabel,
    timeline = listOfNotNull(
        (confirmedAtMillis ?: createdAtMillis)?.let { "Order placed" to it },
        outForDeliveryAtMillis?.let { "Out for delivery" to it },
        deliveredAtMillis?.let { "Delivered" to it },
        cancelledAtMillis?.let { "Cancelled" to it }
    ).sortedBy { it.second }.map { OrderTimelineStep(it.first, OrderTime.label(it.second)) }
)

/** One history row. Nothing here is derived: a missing payable reads "Amount details unavailable", never ₹0. */
data class OrderSummaryView(
    val orderId: String,
    val title: String,
    val placedLabel: String?,
    val itemsLabel: String,
    /** The payable (or null for a legacy order). */
    val amount: String?,
    val caption: String
)

fun CustomerOrderSummary.view(): OrderSummaryView {
    val p = payable
    return OrderSummaryView(
        orderId = orderId,
        title = status.title(),
        placedLabel = createdAtMillis?.let { "Placed ${OrderTime.label(it)}" },
        itemsLabel = if (itemCount == 1) "1 item" else "$itemCount items",
        amount = p?.format(),
        caption = when {
            status == CustomerOrderStatus.CANCELLED -> "Cancelled"
            p == null -> "Amount details unavailable"
            status == CustomerOrderStatus.DELIVERED -> "Cash on delivery"
            paymentMethod != CustomerPaymentMethod.COD -> "Amount"
            p.paise == 0L -> CheckoutCopy.NOTHING_DUE
            else -> "due on delivery"
        }
    )
}

/**
 * Order times in India Standard Time (UTC+05:30, no daylight saving): Tazzzo delivers in India only, and the backend sends UTC
 * instants. `10 Oct 2026, 2:45 pm`. Pure, so it is tested without a device or a time-zone database.
 */
object OrderTime {
    private const val IST_OFFSET_MILLIS = (5 * 60 + 30) * 60_000L
    private val MONTHS = listOf("Jan", "Feb", "Mar", "Apr", "May", "Jun", "Jul", "Aug", "Sep", "Oct", "Nov", "Dec")

    fun label(utcMillis: Long): String {
        val local = utcMillis + IST_OFFSET_MILLIS
        val days = floorDiv(local, 86_400_000L)
        val msOfDay = local - days * 86_400_000L
        val (y, m, d) = civilFromDays(days)
        val h = (msOfDay / 3_600_000L).toInt()
        val min = ((msOfDay / 60_000L) % 60).toInt()
        val h12 = if (h % 12 == 0) 12 else h % 12
        return "$d ${MONTHS[m - 1]} $y, $h12:${min.toString().padStart(2, '0')} ${if (h < 12) "am" else "pm"}"
    }

    private fun floorDiv(a: Long, b: Long): Long { val q = a / b; return if ((a % b != 0L) && ((a < 0) != (b < 0))) q - 1 else q }

    // Howard Hinnant's civil-from-days (the inverse of Iso8601's days-from-civil).
    private fun civilFromDays(z0: Long): Triple<Int, Int, Int> {
        val z = z0 + 719_468L
        val era = (if (z >= 0) z else z - 146_096L) / 146_097L
        val doe = z - era * 146_097L
        val yoe = (doe - doe / 1_460 + doe / 36_524 - doe / 146_096) / 365
        val doy = doe - (365 * yoe + yoe / 4 - yoe / 100)
        val mp = (5 * doy + 2) / 153
        val d = (doy - (153 * mp + 2) / 5 + 1).toInt()
        val m = (if (mp < 10) mp + 3 else mp - 9).toInt()
        val y = (yoe + era * 400 + (if (m <= 2) 1 else 0)).toInt()
        return Triple(y, m, d)
    }
}
