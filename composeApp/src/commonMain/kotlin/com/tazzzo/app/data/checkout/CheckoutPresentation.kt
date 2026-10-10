package com.tazzzo.app.data.checkout

import com.tazzzo.app.data.cart.ServerCart
import com.tazzzo.app.data.model.Money
import com.tazzzo.app.data.model.PayableMoney
import kotlin.time.Duration

/**
 * Customer wording is fixed here. A quote's `moneyPreview` is ADVISORY (backend contract: Order placement revalidates and
 * computes its own money, which may differ — not an error), so checkout shows it as the total FOR REVIEW with [PREVIEW_NOTE],
 * never as an amount "due on delivery". Only the order's AUTHORITATIVE money is "due on delivery". Amounts are shown as-is:
 * never estimated, never recomputed, never "paid". Only [SUBTOTAL_LABEL] appears when a quote has no money preview.
 */
object CheckoutCopy {
    const val SUBTOTAL_LABEL = "Item subtotal"
    const val DISCOUNT_LABEL = "Benefit discount"
    const val AMOUNT_DUE_LABEL = "Amount due"
    /** Cash on delivery with nothing to collect (a full discount). Never "Payment due" for ₹0. */
    const val NOTHING_DUE = "Nothing due on delivery"
    const val ORDER_CTA = "Place order"
    /** The quote's advisory payable, as the checkout review labels it (an order's money is "Amount due"). */
    const val PREVIEW_TOTAL_LABEL = "Total"
    /** Always shown with a quote's money: the order computes the amount that holds when it is placed. */
    const val PREVIEW_NOTE = "Final amount is confirmed when you place your order."
    /** The sticky Place-order bar's caption under the advisory total. */
    const val PREVIEW_CAPTION = "Total for review"
    /** The payment card on checkout: how the (order's) amount is paid, without asserting the preview is that amount. */
    const val PREVIEW_PAYMENT = "Pay your order total in cash when it arrives."
    /** Fail-closed copy when the order capability is off (a kill switch; REMOTE and MOCK have it on). Never about money. */
    const val ORDERING_PAUSED = "We can't take orders right now."
    /**
     * 409 PAYABLE_CHANGED. Kept only as a defensive mapping: the running backend does NOT send it (its money preview is advisory
     * and a differing order is placed with 200 — see [payableChangeNotice]).
     */
    const val PAYABLE_CHANGED = "Your order amount changed. Review checkout again."
    /** A quote that violates the contract. */
    const val CONTRACT_FAILURE = "Checkout couldn't be loaded correctly."
    const val NEUTRAL_ITEM = "Item"

    /** `₹90 due on delivery`, or [NOTHING_DUE] for ₹0. */
    fun dueOnDelivery(money: PayableMoney): String = if (money.isNothingDue) NOTHING_DUE else "${money.payable.format()} due on delivery"
}

/** What the customer can do next. The composable only dispatches these. */
sealed interface CheckoutAction {
    /** Resend the SAME request with the SAME key (only offered after an ambiguous failure with unchanged inputs). */
    data object TryAgain : CheckoutAction
    /** A NEW attempt (new key): "Review checkout". */
    data object ReviewCheckout : CheckoutAction
    /** Also a NEW attempt (new key), worded "Refresh checkout": after a contract failure or for a quote with no money preview. */
    data object RefreshCheckout : CheckoutAction
    data object ChooseAddress : CheckoutAction
    data object ChangeAddress : CheckoutAction
    data object GoToCart : CheckoutAction
    data object SignIn : CheckoutAction
    /** Re-POST the SAME quote to learn whether an unanswered order exists. Never a new quote. */
    data object CheckOrder : CheckoutAction
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
        // Never "Try again" with the same key: the same key replays the same stored quote.
        CheckoutFailure.ContractViolation -> FailureView(CheckoutCopy.CONTRACT_FAILURE, "Refresh checkout to continue.", listOf(CheckoutAction.RefreshCheckout))
    }
}

fun StaleReason.view(): FailureView = when (this) {
    StaleReason.CartChanged -> FailureView("Your cart changed. Review it before continuing.", "This quote is no longer current.", listOf(CheckoutAction.ReviewCheckout, CheckoutAction.GoToCart))
    StaleReason.AddressChanged -> FailureView("Your delivery address changed", "Review checkout again to continue.", listOf(CheckoutAction.ReviewCheckout, CheckoutAction.ChangeAddress))
    StaleReason.AddressRemoved -> FailureView("Your delivery address is no longer available", "Choose an address to continue.", listOf(CheckoutAction.ChooseAddress))
    StaleReason.PayableChanged -> PAYABLE_CHANGED_VIEW
}

/** PAYABLE_CHANGED, for both the refused order and the invalidated quote. Only a NEW quote is offered. */
val PAYABLE_CHANGED_VIEW = FailureView(CheckoutCopy.PAYABLE_CHANGED, "", listOf(CheckoutAction.ReviewCheckout))

/** A Ready quote without a money preview (legacy): it can never be ordered, only refreshed into a new quote. */
val NO_BINDING_MONEY_VIEW = FailureView("Checkout needs to be refreshed", "Refresh checkout to see the amount due.", listOf(CheckoutAction.RefreshCheckout))

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

/** One money row. [emphasised] marks the amount due. */
data class MoneyLine(val label: String, val value: String, val emphasised: Boolean = false)

/**
 * The money rows for a binding/authoritative [money], or — when there is none (legacy) — the item subtotal ALONE: no amount
 * due is ever derived. No fee, tax, COD, coupon, Coins or wallet row exists, not even as zero. Values use the canonical
 * [Money.format] (`₹100`, `₹49.50`, `₹0`).
 */
fun moneyLines(money: PayableMoney?, subtotal: Money): List<MoneyLine> =
    if (money == null) listOf(MoneyLine(CheckoutCopy.SUBTOTAL_LABEL, subtotal.format()))
    else listOfNotNull(
        MoneyLine(CheckoutCopy.SUBTOTAL_LABEL, money.merchandiseSubtotal.format()),
        if (money.hasDiscount) MoneyLine(CheckoutCopy.DISCOUNT_LABEL, (-money.benefitDiscount).format()) else null,
        MoneyLine(CheckoutCopy.AMOUNT_DUE_LABEL, money.payable.format(), emphasised = true)
    )

/**
 * The review summary, from the quote's ADVISORY money preview only. [benefit] is never shown (the discount row comes from the
 * money). The last row is the "Total" for review, never "Amount due"; [reviewNote] ([CheckoutCopy.PREVIEW_NOTE]) is shown with
 * it. [reviewNote] is null when there is no money preview (such a quote is never orderable).
 */
data class CheckoutSummaryView(val itemsLabel: String, val lines: List<MoneyLine>, val reviewNote: String?)

fun CheckoutQuote.summary(): CheckoutSummaryView = CheckoutSummaryView(
    itemsLabel = if (itemCount == 1) "1 item" else "$itemCount items",
    lines = moneyLines(money, subtotal).map { if (it.label == CheckoutCopy.AMOUNT_DUE_LABEL) it.copy(label = CheckoutCopy.PREVIEW_TOTAL_LABEL) else it },
    reviewNote = money?.let { CheckoutCopy.PREVIEW_NOTE }
)

/**
 * The total-changed notice for a placed order (mirrors the storefront's `order-total-changed`): the quote's advisory payable the
 * customer reviewed vs the order's AUTHORITATIVE payable. Null when either is unknown or they are equal. Not an error: the
 * order's amount is the one that holds and is the one displayed everywhere else.
 */
fun payableChangeNotice(reviewedPayable: Money?, orderPayable: Money?): String? {
    if (reviewedPayable == null || orderPayable == null || reviewedPayable == orderPayable) return null
    return "Your total changed from ${reviewedPayable.format()} to ${orderPayable.format()}. Benefits and prices are checked again " +
        "when an order is placed, and this is the amount of your order."
}

fun DeliveryContent.displayLines(): List<String> = listOfNotNull(
    recipientName, addressLine1, addressLine2?.takeIf { it.isNotBlank() }, landmark?.takeIf { it.isNotBlank() },
    "$city, $state ${postalCode.value}"
)
