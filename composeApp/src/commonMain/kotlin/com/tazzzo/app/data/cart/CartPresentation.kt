package com.tazzzo.app.data.cart

import com.tazzzo.app.data.catalog.CatalogCapabilities
import com.tazzzo.app.data.catalog.CatalogProduct
import com.tazzzo.app.data.catalog.PurchaseAction
import com.tazzzo.app.data.catalog.StockState
import com.tazzzo.app.data.catalog.purchaseAction

/** Customer copy for a line issue. Unknown codes are blocking and get a neutral message — never raw server text. */
fun LineIssue.message(): String = when (this) {
    is LineIssue.Unrecognized -> "Unavailable right now"
    is LineIssue.Known -> when (code) {
        KnownIssue.PRODUCT_UNAVAILABLE -> "No longer available"
        KnownIssue.PRICE_UNAVAILABLE -> "Price unavailable"
        KnownIssue.LOCATION_REQUIRED -> "Choose a delivery address to check availability"
        KnownIssue.UNSERVICEABLE -> "Not available at your address"
        KnownIssue.OUT_OF_STOCK -> "Out of stock"
        KnownIssue.STOCK_UNKNOWN -> "Availability couldn't be confirmed"
        KnownIssue.INSUFFICIENT_STOCK -> "Not enough stock for this quantity"
        KnownIssue.ENRICHMENT_UNAVAILABLE -> "Details unavailable right now"
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
    val blocked: Boolean,
    /** The stepper's + may be shown only when the line is buyable and its availability is known. */
    val canIncrease: Boolean,
    val maxQuantity: Int?
)

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
        issues = issues.map { it.message() }.distinct(),
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
