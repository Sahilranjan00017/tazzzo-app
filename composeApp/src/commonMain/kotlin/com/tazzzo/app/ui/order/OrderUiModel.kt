package com.tazzzo.app.ui.order

import com.tazzzo.app.data.checkout.CheckoutCopy
import com.tazzzo.app.data.order.CustomerOrder
import com.tazzzo.app.data.order.CustomerOrderStatus
import com.tazzzo.app.data.order.OrderPaymentCondition
import com.tazzzo.app.data.order.OrderState

/*
 * Presentation-only bindings for the order surfaces (UI-06). The REAL contract is: `POST /v1/customer/orders` (place or
 * replay) and `GET /v1/customer/orders/{orderId}`; there is NO list endpoint, so "history" does not exist and the Orders tab
 * can only show the order placed in this session (`OrderStore.recent`). Money is the order's persisted AUTHORITATIVE
 * snapshot through [com.tazzzo.app.data.order.view]; nothing here recomputes, infers or defaults an amount.
 */

object OrderCopy {
    const val CONFIRMATION_TITLE = "Order placed"
    const val CONFIRMATION_SUPPORT = "We've received your order and will deliver it to the address below."
    const val CONTINUE_SHOPPING = "Continue shopping"
    const val VIEW_ORDER = "View order"
    const val ORDERS_TITLE = "Orders"
    const val ORDER_TITLE = "Your order"
    const val THIS_SESSION = "Placed in this session"
    const val HISTORY_UNAVAILABLE_TITLE = "Order history isn't available yet"
    const val HISTORY_UNAVAILABLE_BODY = "Your orders will appear here once history is ready."
    const val HISTORY_UNAVAILABLE_NOTE = "Full order history isn't available yet."
    /** Only for a REAL history list that answered with zero orders; never shown while history is unavailable. */
    const val NO_ORDERS_TITLE = "No orders yet"
    const val NO_ORDERS_BODY = "Your orders will appear here after you place one."
    const val START_SHOPPING = "Start shopping"
    const val LOAD_FAILED_TITLE = "We couldn't load this order"
    const val LOAD_FAILED_BODY = "Please check your connection and try again."
    const val CONFIRMATION_LOAD_FAILED_BODY = "Your order is placed. We couldn't load its details right now."
    const val TRY_AGAIN = "Try again"
    /** A legacy order without the money snapshot: never ₹0, never derived. */
    const val AMOUNT_UNAVAILABLE = "Amount details unavailable"
    const val SECTION_ITEMS = "Items"
    const val SECTION_ADDRESS = "Delivery address"
    const val SECTION_PAYMENT = "Payment"
    const val SECTION_SUMMARY = "Summary"
    const val ORDER_NUMBER = "Order number"
}

/** The customer label for the backend's status. Only the statuses in the contract exist; nothing is a progression. */
fun CustomerOrderStatus.label(): String = when (this) {
    CustomerOrderStatus.CONFIRMED -> "Confirmed"
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
    if (paymentCondition != OrderPaymentCondition.COD_DUE) return OrderAmount(m.payable.format(), "Amount")
    return if (m.isNothingDue) OrderAmount("₹0", CheckoutCopy.NOTHING_DUE) else OrderAmount(m.payable.format(), "due on delivery")
}

/** The confirmation is shown ONLY for a confirmed real order held by the store — never for Placing, Ambiguous or Failed. */
fun OrderState.confirmedOrder(): CustomerOrder? = (this as? OrderState.Placed)?.order

/** Whether the Orders surface is a real history list. False today: the contract has no list endpoint. */
fun orderHistoryAvailable(historyIntegration: Boolean): Boolean = historyIntegration

/** The "no orders" vs "history unavailable" distinction, decided once. */
enum class OrdersEmptyKind { HistoryUnavailable, NoOrdersYet }

fun ordersEmptyKind(historyIntegration: Boolean): OrdersEmptyKind =
    if (historyIntegration) OrdersEmptyKind.NoOrdersYet else OrdersEmptyKind.HistoryUnavailable
