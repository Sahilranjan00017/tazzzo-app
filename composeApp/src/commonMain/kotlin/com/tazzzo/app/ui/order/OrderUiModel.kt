package com.tazzzo.app.ui.order

import com.tazzzo.app.data.catalog.AppendState
import com.tazzzo.app.data.catalog.CatalogFailure
import com.tazzzo.app.data.catalog.PagedState
import com.tazzzo.app.data.checkout.CheckoutCopy
import com.tazzzo.app.data.order.CustomerOrder
import com.tazzzo.app.data.order.CustomerOrderSummary
import com.tazzzo.app.data.order.CustomerOrderStatus
import com.tazzzo.app.data.order.OrderPaymentCondition
import com.tazzzo.app.data.order.OrderState

/*
 * Presentation-only bindings for the order surfaces (UI-06). The REAL contract is: `POST /v1/customer/orders` (place or
 * replay), `GET /v1/customer/orders` (the customer's own history, cursor-paged, newest first) and
 * `GET /v1/customer/orders/{orderId}`. Money is the order's persisted AUTHORITATIVE snapshot through
 * [com.tazzzo.app.data.order.view]; nothing here recomputes, infers or defaults an amount.
 */

object OrderCopy {
    const val CONFIRMATION_TITLE = "Order placed"
    const val CONFIRMATION_SUPPORT = "We've received your order and will deliver it to the address below."
    const val CONTINUE_SHOPPING = "Continue shopping"
    const val VIEW_ORDER = "View order"
    const val ORDERS_TITLE = "Orders"
    const val ORDER_TITLE = "Your order"
    /** Only for a REAL history list that answered with zero orders. */
    const val NO_ORDERS_TITLE = "No orders yet"
    const val NO_ORDERS_BODY = "Your orders will appear here after you place one."
    const val START_SHOPPING = "Start shopping"
    const val LOAD_FAILED_TITLE = "We couldn't load this order"
    const val LOAD_FAILED_BODY = "Please check your connection and try again."
    const val CONFIRMATION_LOAD_FAILED_BODY = "Your order is placed. We couldn't load its details right now."
    const val TRY_AGAIN = "Try again"
    const val HISTORY_FAILED_TITLE = "We couldn't load your orders"
    const val SIGNED_OUT_TITLE = "Log in to see your orders"
    const val SIGNED_OUT_BODY = "Your orders appear here once you're logged in."
    const val LOG_IN = "Log in"
    const val LOAD_MORE = "Load more"
    const val LOAD_MORE_FAILED = "We couldn't load more orders."
    const val SECTION_TIMELINE = "Status"
    const val SLOT_LABEL = "Delivery slot"
    const val PLACED_LABEL = "Placed"
    const val NEED_HELP = "Contact us about this order"
    /** A legacy order without the money snapshot: never ₹0, never derived. */
    const val AMOUNT_UNAVAILABLE = "Amount details unavailable"
    const val SECTION_ITEMS = "Items"
    const val SECTION_ADDRESS = "Delivery address"
    const val SECTION_PAYMENT = "Payment"
    const val SECTION_SUMMARY = "Summary"
    const val ORDER_NUMBER = "Order number"
}

/** The customer label (status chip) for the backend's status. Only the statuses in the contract exist; nothing is projected. */
fun CustomerOrderStatus.label(): String = when (this) {
    CustomerOrderStatus.CONFIRMED -> "Confirmed"
    CustomerOrderStatus.OUT_FOR_DELIVERY -> "Out for delivery"
    CustomerOrderStatus.DELIVERED -> "Delivered"
    CustomerOrderStatus.CANCELLED -> "Cancelled"
    CustomerOrderStatus.UNRECOGNIZED -> "Received"
}

/**
 * The headline amount of an order. With the persisted money snapshot: the payable (₹0 reads "Nothing due on delivery").
 * Without it (a legacy order): [OrderCopy.AMOUNT_UNAVAILABLE] — missing money is NOT zero and is never inferred from the
 * subtotal or the catalogue.
 */
data class OrderAmount(val amount: String?, val caption: String) {
    val isUnavailable: Boolean get() = amount == null
}

fun CustomerOrder.amountHeadline(): OrderAmount {
    val m = money ?: return OrderAmount(null, OrderCopy.AMOUNT_UNAVAILABLE)
    // Nothing is due on a cancelled order, and a delivered one is not "due on delivery" any more — but never "paid" either.
    if (status == CustomerOrderStatus.CANCELLED) return OrderAmount(m.payable.format(), "order total · cancelled")
    if (status == CustomerOrderStatus.DELIVERED) return OrderAmount(m.payable.format(), "cash on delivery")
    if (paymentCondition != OrderPaymentCondition.COD_DUE) return OrderAmount(m.payable.format(), "Amount")
    return if (m.isNothingDue) OrderAmount("₹0", CheckoutCopy.NOTHING_DUE) else OrderAmount(m.payable.format(), "due on delivery")
}

/**
 * Where a placed (or reconciled) order lands: the confirmation, except for an order that is already CANCELLED (e.g. "Check order"
 * found the earlier attempt and it was cancelled since) — that goes to its detail, never to "Order placed".
 */
fun CustomerOrder.opensConfirmation(): Boolean = status != CustomerOrderStatus.CANCELLED

/** The confirmation is shown ONLY for a confirmed real order held by the store — never for Placing, Ambiguous or Failed. */
fun OrderState.confirmedOrder(): CustomerOrder? = (this as? OrderState.Placed)?.order

/** What the Orders surface shows, decided once from the session and the history state. */
sealed interface OrdersSurface {
    data object SignedOut : OrdersSurface
    data object Loading : OrdersSurface
    data class Failed(val failure: CatalogFailure) : OrdersSurface
    /** A REAL history that answered with zero orders. */
    data object NoOrdersYet : OrdersSurface
    data class Content(val orders: List<CustomerOrderSummary>, val hasMore: Boolean, val append: AppendState) : OrdersSurface
}

fun ordersSurface(authenticated: Boolean, history: PagedState<CustomerOrderSummary>): OrdersSurface = when {
    !authenticated -> OrdersSurface.SignedOut
    else -> when (history) {
        PagedState.Idle, PagedState.LoadingFirst -> OrdersSurface.Loading
        is PagedState.FirstPageFailed -> OrdersSurface.Failed(history.failure)
        PagedState.Empty -> OrdersSurface.NoOrdersYet
        is PagedState.Content -> OrdersSurface.Content(history.items, history.hasMore, history.append)
    }
}
