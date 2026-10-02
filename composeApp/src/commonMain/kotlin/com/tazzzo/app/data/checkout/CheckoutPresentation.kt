package com.tazzzo.app.data.checkout

import com.tazzzo.app.data.cart.ServerCart
import kotlin.time.Duration

/** Customer wording is fixed here: the quote is an ITEM subtotal, never a total, and final charges are not promised. */
object CheckoutCopy {
    const val SUBTOTAL_LABEL = "Item subtotal"
    const val CHARGES_NOTE = "Final charges are confirmed when your order is placed."
    const val ORDER_CTA = "Place order coming next"
    const val NEUTRAL_ITEM = "Item"
}

/** What the customer can do next. The composable only dispatches these. */
sealed interface CheckoutAction {
    /** Resend the SAME request with the SAME key (only offered after an ambiguous failure with unchanged inputs). */
    data object TryAgain : CheckoutAction
    /** A NEW attempt (new key): "Review checkout" / "Refresh checkout". */
    data object ReviewCheckout : CheckoutAction
    data object ChooseAddress : CheckoutAction
    data object ChangeAddress : CheckoutAction
    data object GoToCart : CheckoutAction
    data object SignIn : CheckoutAction
}

data class FailureView(val title: String, val hint: String, val actions: List<CheckoutAction>)

fun ItemRejection.text(): String = when (this) {
    is ItemRejection.Unrecognized -> "Unavailable right now"
    is ItemRejection.Known -> when (reason) {
        ItemRejection.Reason.PRODUCT_UNAVAILABLE -> "No longer available"
        ItemRejection.Reason.PRICE_UNAVAILABLE -> "Price unavailable"
        ItemRejection.Reason.OUT_OF_STOCK -> "Out of stock"
        ItemRejection.Reason.INSUFFICIENT_STOCK -> "Not enough stock for this quantity"
        ItemRejection.Reason.STOCK_UNKNOWN -> "Availability couldn't be confirmed"
        ItemRejection.Reason.NOT_BUYABLE -> "Can't be bought right now"
    }
}

/** App-written copy for a failure. [canRetrySameKey] decides whether "Try again" or a fresh "Review checkout" is offered. */
fun CheckoutFailure.view(canRetrySameKey: Boolean): FailureView {
    val retry = if (canRetrySameKey) CheckoutAction.TryAgain else CheckoutAction.ReviewCheckout
    return when (this) {
        CheckoutFailure.AddressRequired -> FailureView("Choose a delivery address", "We need one of your saved addresses to prepare checkout.", listOf(CheckoutAction.ChooseAddress))
        CheckoutFailure.CartUnavailable -> FailureView("Couldn't load your cart", "Please try again.", listOf(CheckoutAction.ReviewCheckout, CheckoutAction.GoToCart))
        CheckoutFailure.CartBusy -> FailureView("Your cart is still updating", "Try again in a moment.", listOf(CheckoutAction.ReviewCheckout))
        CheckoutFailure.CartHasIssues -> FailureView("Some items need your attention", "Sort them out in your cart first.", listOf(CheckoutAction.GoToCart))
        CheckoutFailure.CartChanged -> FailureView("Your cart changed. Review it before continuing.", "Nothing was changed for you.", listOf(CheckoutAction.GoToCart, CheckoutAction.ReviewCheckout))
        CheckoutFailure.CartEmpty -> FailureView("Your cart is empty", "Add something to check out.", listOf(CheckoutAction.GoToCart))
        CheckoutFailure.Unserviceable -> FailureView("This address isn't serviceable for this order.", "Choose a different delivery address.", listOf(CheckoutAction.ChangeAddress))
        is CheckoutFailure.ItemsUnavailable -> FailureView("Some items can't be checked out", "Review them in your cart. Nothing was removed.", listOf(CheckoutAction.GoToCart))
        CheckoutFailure.KeyConflict -> FailureView("Something went wrong", "Please start checkout again.", listOf(CheckoutAction.ReviewCheckout))
        CheckoutFailure.NotFound -> FailureView("We couldn't use that address", "Please choose your delivery address again.", listOf(CheckoutAction.ChangeAddress))
        CheckoutFailure.QuoteExpired -> FailureView("This checkout expired", "Refresh it to continue.", listOf(CheckoutAction.ReviewCheckout))
        CheckoutFailure.ClientBug -> FailureView("Something went wrong", "Please update the app and try again.", listOf(CheckoutAction.GoToCart))
        is CheckoutFailure.RateLimited -> FailureView("Too many attempts", "Please wait a moment and try again.", listOf(retry))
        CheckoutFailure.Unavailable -> FailureView("Checkout is temporarily unavailable", "Please try again shortly.", listOf(retry))
        CheckoutFailure.Network, CheckoutFailure.Timeout, CheckoutFailure.Server, CheckoutFailure.Unknown ->
            FailureView("Couldn't reach Tazzzo", "Check your connection and try again.", listOf(retry))
        CheckoutFailure.Unauthenticated -> FailureView("Log in to continue", "Your session ended.", listOf(CheckoutAction.SignIn))
    }
}

fun StaleReason.view(): FailureView = when (this) {
    StaleReason.CartChanged -> FailureView("Your cart changed. Review it before continuing.", "This quote is no longer current.", listOf(CheckoutAction.ReviewCheckout, CheckoutAction.GoToCart))
    StaleReason.AddressChanged -> FailureView("Your delivery address changed", "Review checkout again to continue.", listOf(CheckoutAction.ReviewCheckout, CheckoutAction.ChangeAddress))
    StaleReason.AddressRemoved -> FailureView("Your delivery address is no longer available", "Choose an address to continue.", listOf(CheckoutAction.ChooseAddress))
}

val EXPIRED_VIEW = FailureView("This checkout expired", "Refresh checkout to continue.", listOf(CheckoutAction.ReviewCheckout))

/** `4:32`. */
fun expiryLabel(remaining: Duration): String {
    val total = remaining.inWholeSeconds.coerceAtLeast(0)
    return "Expires in ${total / 60}:${(total % 60).toString().padStart(2, '0')}"
}

/** One quoted line. Money comes ONLY from the quote; the title from the cart only when it is safely the same cart. */
data class CheckoutLineView(val skuId: String, val title: String, val quantity: Int, val unitPriceLabel: String, val lineTotalLabel: String)

fun CheckoutQuote.lineViews(cart: ServerCart?): List<CheckoutLineView> {
    val safe = cart?.takeIf { it.version == cartVersion }                          // metadata from a different cart is never used
    return items.map { i ->
        CheckoutLineView(
            skuId = i.skuId,
            title = safe?.item(i.skuId)?.title?.takeIf { it.isNotBlank() } ?: CheckoutCopy.NEUTRAL_ITEM,
            quantity = i.quantity, unitPriceLabel = i.unitPrice.format(), lineTotalLabel = i.lineTotal.format()
        )
    }
}

/**
 * The review summary. Deliberately NO payable amount, fee, tax, discount or benefit line: [BenefitPreviewState] is
 * modelled and tested but never shown in PR-07, and it never touches the subtotal.
 */
data class CheckoutSummaryView(val itemsLabel: String, val subtotalLabel: String, val subtotalValue: String, val note: String)

fun CheckoutQuote.summary(): CheckoutSummaryView = CheckoutSummaryView(
    itemsLabel = if (itemCount == 1) "1 item" else "$itemCount items",
    subtotalLabel = CheckoutCopy.SUBTOTAL_LABEL, subtotalValue = subtotal.format(), note = CheckoutCopy.CHARGES_NOTE
)

fun DeliveryContent.displayLines(): List<String> = listOfNotNull(
    recipientName, addressLine1, addressLine2?.takeIf { it.isNotBlank() }, landmark?.takeIf { it.isNotBlank() },
    "$city, $state ${postalCode.value}"
)
