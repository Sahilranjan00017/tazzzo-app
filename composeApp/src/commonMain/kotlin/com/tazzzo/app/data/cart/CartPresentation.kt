package com.tazzzo.app.data.cart

import com.tazzzo.app.Screen
import com.tazzzo.app.data.catalog.CatalogCapabilities
import com.tazzzo.app.data.catalog.CatalogProduct
import com.tazzzo.app.data.catalog.PurchaseAction
import com.tazzzo.app.data.catalog.StockState
import com.tazzzo.app.data.catalog.purchaseAction

/** What the customer can do about a blocked line. The composable only dispatches these; it never talks to the network. */
sealed interface CartAction {
    data object Remove : CartAction
    /** Re-read the cart with the currently selected addressId. NEVER replays a previous mutation. */
    data object RetryCart : CartAction
    /** No address was supplied to the cart: open the real address screen. */
    data object ChooseAddress : CartAction
    /** The selected address is outside serviceability: open the real address screen. */
    data object ChangeAddress : CartAction
    /** An explicit, customer-triggered absolute quantity (never applied automatically). */
    data class ReduceQuantity(val target: Int) : CartAction
}

/** Where an address action goes: the real (PR-05) address experience, never the legacy mock address form or checkout. */
fun CartAction.addressScreen(): Screen? = when (this) {
    CartAction.ChooseAddress, CartAction.ChangeAddress -> Screen.Addresses
    else -> null
}

/**
 * Customer copy for one issue. [available] is the line's server `maxOrderQuantity` (only used for INSUFFICIENT_STOCK).
 * An unrecognized code is blocking and gets neutral copy — never raw server text.
 */
fun LineIssue.message(available: Int = 0): String = when (this) {
    is LineIssue.Unrecognized -> "Unavailable right now"
    is LineIssue.Known -> when (code) {
        KnownIssue.PRODUCT_UNAVAILABLE -> "Product is no longer available."
        KnownIssue.PRICE_UNAVAILABLE -> "Price is temporarily unavailable."
        KnownIssue.LOCATION_REQUIRED -> "Choose a delivery address to check availability."
        KnownIssue.UNSERVICEABLE -> "This item can't be delivered to the selected address."
        KnownIssue.OUT_OF_STOCK -> "Out of stock."
        KnownIssue.STOCK_UNKNOWN -> "Availability couldn't be confirmed."
        KnownIssue.INSUFFICIENT_STOCK -> if (available > 0) "Only $available available." else "Not enough stock for this quantity."
        KnownIssue.ENRICHMENT_UNAVAILABLE -> "Product information is temporarily unavailable."
    }
}

/** Recovery actions for one issue, primary first. Remove is never the only way out of a recoverable issue. */
fun LineIssue.actions(quantity: Int, available: Int): List<CartAction> = when (this) {
    is LineIssue.Unrecognized -> listOf(CartAction.RetryCart, CartAction.Remove)
    is LineIssue.Known -> when (code) {
        KnownIssue.PRODUCT_UNAVAILABLE -> listOf(CartAction.Remove)
        KnownIssue.PRICE_UNAVAILABLE -> listOf(CartAction.RetryCart, CartAction.Remove)
        KnownIssue.LOCATION_REQUIRED -> listOf(CartAction.ChooseAddress, CartAction.Remove)
        KnownIssue.UNSERVICEABLE -> listOf(CartAction.ChangeAddress, CartAction.Remove)
        KnownIssue.OUT_OF_STOCK -> listOf(CartAction.Remove, CartAction.RetryCart)
        KnownIssue.STOCK_UNKNOWN -> listOf(CartAction.RetryCart, CartAction.Remove)
        KnownIssue.INSUFFICIENT_STOCK ->
            if (available in 1 until quantity) listOf(CartAction.ReduceQuantity(available), CartAction.Remove)
            else listOf(CartAction.RetryCart, CartAction.Remove)
        KnownIssue.ENRICHMENT_UNAVAILABLE -> listOf(CartAction.RetryCart, CartAction.Remove)
    }
}

/** Customer copy for a one-shot cart notice. The hidden server cap is never named. */
fun CartNotice.text(): String = when (this) {
    CartNotice.Stale -> "Your cart changed. Review it and try again."
    CartNotice.CouldntUpdate -> "Couldn't update your cart. Try again."
    CartNotice.MaxReached -> "That's the maximum available for this item."
    CartNotice.ItemUnavailable -> "That item isn't available right now."
    CartNotice.CartFull -> "Your cart is full. Remove something to add more."
    CartNotice.NotApplied -> "We couldn't confirm that change, so it wasn't applied."
    CartNotice.ClientBug -> "Something went wrong. Please update the app."
    CartNotice.AuthRequired -> "Sign in to use your cart."
    CartNotice.Unavailable -> "Cart is temporarily unavailable. Try again shortly."
}

/** Everything the cart screen shows for one line. Pure: derived only from the server's line. */
data class CartLineView(
    val skuId: String,
    val title: String,
    val quantity: Int,
    val unitPriceLabel: String?,
    val mrpLabel: String?,
    val lineTotalLabel: String?,
    val issues: List<String>,
    /** Deduplicated recovery actions across all of the line's issues, primary first. */
    val actions: List<CartAction>,
    val blocked: Boolean,
    /** The stepper's + may be shown only when the line is buyable and its availability is known. */
    val canIncrease: Boolean,
    val maxQuantity: Int?
)

/** A line blocked with no issue code still gets a way out. Unknown codes stay blocking. */
private fun CartItem.recoveryActions(): List<CartAction> {
    if (!isBlocked) return emptyList()
    val all = issues.flatMap { it.actions(quantity, maxOrderQuantity) }.distinct()
    return all.ifEmpty { listOf(CartAction.RetryCart, CartAction.Remove) }
}

fun CartItem.toView(): CartLineView {
    val availabilityKnown = serviceable == true && (stockState == StockState.IN_STOCK || stockState == StockState.LOW_STOCK)
    val canIncrease = !isBlocked && availabilityKnown && (maxOrderQuantity <= 0 || quantity < maxOrderQuantity)
    return CartLineView(
        skuId = skuId,
        title = title ?: "Unavailable item",
        quantity = quantity,
        unitPriceLabel = unitPrice?.format(),
        mrpLabel = if (unitPrice != null && mrp != null && mrp > unitPrice) mrp.format() else null,
        lineTotalLabel = lineTotal?.format(),
        issues = issues.map { it.message(maxOrderQuantity) }.distinct(),
        actions = recoveryActions(),
        blocked = isBlocked,
        canIncrease = canIncrease,
        maxQuantity = if (availabilityKnown && maxOrderQuantity > 0) maxOrderQuantity else null
    )
}

data class CartSummaryView(
    val itemsLabel: String,
    /** The sum of current line prices. Deliberately NOT called a total: no delivery, tax or offers. */
    val subtotalLabel: String,
    val subtotalCaption: String,
    val blockedLines: Int,
    val hasBlockedLines: Boolean
)

fun ServerCart.toSummary(): CartSummaryView {
    val blocked = items.count { it.isBlocked }
    return CartSummaryView(
        itemsLabel = if (itemCount == 1) "1 item" else "$itemCount items",
        subtotalLabel = subtotal.format(),
        subtotalCaption = "Item subtotal. Delivery and offers are added at checkout.",
        blockedLines = blocked,
        hasBlockedLines = blocked > 0
    )
}

/** The floating bar / badge label. Count only: no SKU names, no analytics. */
fun ServerCart.badgeLabel(): String? = itemCount.takeIf { it > 0 }?.let { if (it > 99) "99+" else it.toString() }

/**
 * What a catalogue card / PDP shows for a product in REMOTE mode. The ONLY place that decides between
 * Add, a stepper and a disabled message:
 *  - the product's own eligibility (price, server `buyable`, capability) is [purchaseAction];
 *  - a signed-out customer sees Add, and tapping it asks for sign-in (NO silent replay afterwards);
 *  - a line already in the cart shows its server-confirmed quantity (or the pending target while a request is in flight).
 */
sealed interface PurchaseControl {
    data class Disabled(val label: String) : PurchaseControl
    data object Add : PurchaseControl
    data class Stepper(val quantity: Int, val pending: Boolean, val canIncrease: Boolean) : PurchaseControl
}

fun purchaseControl(
    product: CatalogProduct,
    caps: CatalogCapabilities,
    confirmedQuantity: Int,
    pending: PendingTarget?
): PurchaseControl {
    val action = purchaseAction(product, caps)
    if (action is PurchaseAction.Disabled && confirmedQuantity == 0 && pending == null) return PurchaseControl.Disabled(action.label)
    val shown = when (pending) {
        is PendingTarget.Quantity -> pending.quantity
        PendingTarget.Removing -> 0
        null -> confirmedQuantity
    }
    if (shown <= 0) return if (action is PurchaseAction.Disabled) PurchaseControl.Disabled(action.label) else PurchaseControl.Add
    val cap = product.maxOrderQuantity.takeIf { it > 0 }
    val canIncrease = action !is PurchaseAction.Disabled && (cap == null || shown < cap)
    return PurchaseControl.Stepper(shown, pending != null, canIncrease)
}
