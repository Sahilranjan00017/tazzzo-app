package com.tazzzo.app.ui.checkout

import com.tazzzo.app.data.checkout.CheckoutAction
import com.tazzzo.app.data.checkout.CheckoutCopy
import com.tazzzo.app.data.checkout.FailureView
import com.tazzzo.app.data.checkout.PAYABLE_CHANGED_VIEW
import com.tazzzo.app.data.model.PayableMoney

/*
 * Presentation-only bindings for the UI-05 purchase flow. Every decision (what a quote is, how its advisory money is shown, which
 * action is safe after which failure, idempotency, PAYABLE_CHANGED precedence) stays in data/checkout and data/order;
 * this file only chooses words and arrangement for the surfaces, and is unit-tested.
 */

object PurchaseCopy {
    const val CART_TITLE = "Your cart"
    const val CART_EMPTY_TITLE = "Your cart is empty"
    const val CART_EMPTY_BODY = "Good things are a few taps away."
    const val START_SHOPPING = "Start shopping"
    const val CART_SIGNED_OUT_TITLE = "Log in to see your cart"
    const val CART_SIGNED_OUT_BODY = "Your cart is saved to your account."
    const val REVIEW_CHECKOUT = "Review checkout"
    const val RESOLVE_ITEMS = "Resolve items to continue"
    const val CLEAR_CART = "Clear cart"
    const val CLEAR_CART_CONFIRM = "Tap again to clear your cart"
    const val ADDRESSES_TITLE = "Delivery address"
    const val ADD_ADDRESS = "Add new address"
    const val ADDRESS_EMPTY_TITLE = "No saved addresses yet"
    const val ADDRESS_EMPTY_BODY = "Add one to keep your delivery details handy."
    const val ADDRESS_LIMIT_TITLE = "You've saved the maximum number of addresses"
    const val CHECKOUT_TITLE = "Checkout"
    const val STEP_ADDRESS = "Delivery address"
    const val STEP_PAYMENT = "Payment"
    const val STEP_REVIEW = "Review"
    const val COD = "Cash on delivery"
    const val COD_HINT = "Pay when your order arrives."
    const val PLACING = "Placing your order…"
    const val PREPARING = "Preparing your checkout…"
    const val ZERO_DUE_HEADLINE = "₹0"
    /** PAYABLE_CHANGED, split into the approved headline + support line (the domain constant is the single sentence). */
    const val PAYABLE_CHANGED_TITLE = "Your order amount changed."
    const val PAYABLE_CHANGED_SUPPORT = "Review checkout again before placing your order."
    /** An unresolved placement: the existing approved wording, kept. */
    const val AMBIGUOUS_TITLE = "We couldn't confirm your order"
}

/** A failure surface as the screen renders it: one headline, one support line, the primary action, the rest secondary. */
data class RecoveryCopy(val title: String, val support: String?, val primary: CheckoutAction?, val secondary: List<CheckoutAction>) {
    val isPayableChanged: Boolean get() = title == PurchaseCopy.PAYABLE_CHANGED_TITLE
}

/**
 * Words for a [FailureView]. The ACTIONS are the domain's, untouched: PAYABLE_CHANGED keeps exactly one — a NEW checkout —
 * and never "Try again" or "Check order"; an ambiguous placement keeps "Check order" alone. Only the PAYABLE_CHANGED
 * sentence is split into headline + support.
 */
fun FailureView.recoveryCopy(): RecoveryCopy {
    val (title, support) =
        if (this == PAYABLE_CHANGED_VIEW || title == CheckoutCopy.PAYABLE_CHANGED) PurchaseCopy.PAYABLE_CHANGED_TITLE to PurchaseCopy.PAYABLE_CHANGED_SUPPORT
        else title to hint.takeIf { it.isNotBlank() }
    return RecoveryCopy(title, support, actions.firstOrNull(), actions.drop(1))
}

fun CheckoutAction.label(): String = when (this) {
    CheckoutAction.TryAgain -> "Try again"
    CheckoutAction.ReviewCheckout -> PurchaseCopy.REVIEW_CHECKOUT
    CheckoutAction.RefreshCheckout -> "Refresh checkout"
    CheckoutAction.ChooseAddress -> "Choose address"
    CheckoutAction.ChangeAddress -> "Change address"
    CheckoutAction.GoToCart -> "Go to cart"
    CheckoutAction.SignIn -> "Log in"
    CheckoutAction.CheckOrder -> "Check order"
}

/** The payment card: COD is the only launch method, shown as a fact, never as a choice among unsupported methods. */
data class PaymentCardView(val method: String, val dueLine: String?, val hint: String)

fun paymentCard(money: PayableMoney?): PaymentCardView =
    PaymentCardView(PurchaseCopy.COD, money?.let { CheckoutCopy.PREVIEW_PAYMENT }, PurchaseCopy.COD_HINT)

/**
 * The sticky bar's headline: the quote's ADVISORY total "for review" (`₹0` reads as an amount, never as an error). Never
 * "due on delivery" — only the placed order's authoritative money is due.
 */
data class DueHeadline(val amount: String, val caption: String)

fun dueHeadline(money: PayableMoney): DueHeadline =
    DueHeadline(if (money.isNothingDue) PurchaseCopy.ZERO_DUE_HEADLINE else money.payable.format(), CheckoutCopy.PREVIEW_CAPTION)
