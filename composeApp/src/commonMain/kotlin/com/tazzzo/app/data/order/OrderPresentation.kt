package com.tazzzo.app.data.order

import com.tazzzo.app.data.checkout.CheckoutAction
import com.tazzzo.app.data.checkout.CheckoutCopy
import com.tazzzo.app.data.checkout.CheckoutState
import com.tazzzo.app.data.checkout.FailureView

/** What the Place-order control may do right now. The gating lives HERE and in capabilities — never inside [OrderStore]. */
sealed interface PlaceOrderAvailability {
    data object Available : PlaceOrderAvailability
    /** Production order placement is not launch-enabled: disabled, with neutral copy. */
    data object LaunchGated : PlaceOrderAvailability
    data object Placing : PlaceOrderAvailability
    /** An earlier attempt is unresolved: only "Check order" is offered, never a second order. */
    data object NeedsCheck : PlaceOrderAvailability
    data object NoReadyQuote : PlaceOrderAvailability

    val enabled: Boolean get() = this is Available
}

fun placeOrderAvailability(gateOpen: Boolean, checkout: CheckoutState, order: OrderState): PlaceOrderAvailability = when {
    order is OrderState.Placing -> PlaceOrderAvailability.Placing
    order is OrderState.Ambiguous -> PlaceOrderAvailability.NeedsCheck
    !gateOpen -> PlaceOrderAvailability.LaunchGated
    order is OrderState.Placed -> PlaceOrderAvailability.NoReadyQuote
    checkout !is CheckoutState.Ready -> PlaceOrderAvailability.NoReadyQuote
    else -> PlaceOrderAvailability.Available
}

/** App-written copy for every order failure. No server text, ever. */
fun OrderFailure.view(): FailureView = when (this) {
    OrderFailure.NotLaunched -> FailureView(CheckoutCopy.LAUNCH_GATED, "", emptyList())
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
    OrderFailure.CartAlreadyPurchased -> FailureView("This cart was already ordered", "Check your cart for what is left.", listOf(CheckoutAction.GoToCart))
    OrderFailure.ClientBug -> FailureView("Something went wrong", "Please update the app and try again.", listOf(CheckoutAction.GoToCart))
    OrderFailure.Unavailable, OrderFailure.Server, OrderFailure.Network, OrderFailure.Timeout, OrderFailure.Unknown ->
        FailureView("We couldn't confirm your order", "Your order may have been placed. Check before trying anything else.", listOf(CheckoutAction.CheckOrder))
}

/** The customer-facing order. Built ONLY from the backend order. COD_DUE is "payment due on delivery", never "paid". */
data class OrderLineView(val title: String, val quantity: Int, val unitPriceLabel: String, val lineTotalLabel: String)

data class OrderView(
    val title: String,
    val orderId: String,
    val paymentLine: String,
    /** Null for an unrecognized payment condition: neutral copy is shown instead of any guess. */
    val dueLine: String?,
    val lines: List<OrderLineView>,
    val itemsLabel: String,
    val subtotalLabel: String,
    val subtotalValue: String,
    val note: String,
    val addressLines: List<String>
)

fun CustomerOrder.view(): OrderView = OrderView(
    title = if (status == CustomerOrderStatus.CONFIRMED) "Order confirmed" else "Order received",
    orderId = orderId,
    paymentLine = if (paymentMethod == CustomerPaymentMethod.COD) "Cash on delivery" else "Payment",
    dueLine = if (paymentCondition == OrderPaymentCondition.COD_DUE) "Payment due on delivery" else null,
    lines = items.map { OrderLineView(it.title, it.quantity, it.unitPrice.format(), it.lineTotal.format()) },
    itemsLabel = if (itemCount == 1) "1 item" else "$itemCount items",
    subtotalLabel = CheckoutCopy.SUBTOTAL_LABEL,
    subtotalValue = subtotal.format(),
    note = CheckoutCopy.CHARGES_NOTE,
    addressLines = deliveryAddress?.let { a ->
        listOfNotNull(
            a.recipientName, a.addressLine1, a.addressLine2?.takeIf { it.isNotBlank() }, a.landmark?.takeIf { it.isNotBlank() },
            listOfNotNull(a.city, a.state, a.postalCode).joinToString(" ").ifBlank { null }
        )
    }.orEmpty()
)
