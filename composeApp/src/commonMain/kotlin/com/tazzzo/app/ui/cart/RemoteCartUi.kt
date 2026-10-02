package com.tazzzo.app.ui.cart

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.disabled
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.tazzzo.app.LocalAppState
import com.tazzzo.app.Screen
import com.tazzzo.app.data.cart.CartFailure
import com.tazzzo.app.data.cart.CartLineView
import com.tazzzo.app.data.cart.CartState
import com.tazzzo.app.data.cart.PendingTarget
import com.tazzzo.app.data.cart.PurchaseControl
import com.tazzzo.app.data.cart.ServerCart
import com.tazzzo.app.data.cart.badgeLabel
import com.tazzzo.app.data.cart.purchaseControl
import com.tazzzo.app.data.cart.text
import com.tazzzo.app.data.cart.toSummary
import com.tazzzo.app.data.cart.toView
import com.tazzzo.app.data.catalog.CatalogProduct
import com.tazzzo.app.data.repository.ServiceLocator
import com.tazzzo.app.theme.TazColors
import com.tazzzo.app.theme.TazIcons
import com.tazzzo.app.theme.TazMotion
import com.tazzzo.app.theme.TazRadius
import com.tazzzo.app.theme.TazSize
import com.tazzzo.app.theme.TazSpace
import com.tazzzo.app.theme.TazType
import com.tazzzo.app.ui.common.EmptyState
import com.tazzzo.app.ui.common.PillButton
import com.tazzzo.app.ui.common.SkeletonBlock
import com.tazzzo.app.ui.common.StepperTouchTarget
import com.tazzzo.app.ui.common.TazIcon
import com.tazzzo.app.ui.common.TazTopBar
import com.tazzzo.app.ui.interaction.TazPress
import com.tazzzo.app.ui.interaction.tazPressable
import androidx.compose.animation.core.tween

/** Surfaces a one-shot cart notice through the app's transient message, then clears it. */
@Composable
fun CartNoticeHost() {
    val app = LocalAppState.current
    val notice by ServiceLocator.cart.notice.collectAsState()
    LaunchedEffect(notice) {
        notice?.let { app.transientMessage = it.text(); ServiceLocator.cart.dismissNotice() }
    }
}

/**
 * ADD -> stepper for a REMOTE product. Tapping ADD while signed out opens Login; the add is NOT remembered or
 * replayed afterwards (the customer taps ADD again). The product row opens the PDP by `productId`; nothing here
 * ever navigates by `skuId`.
 */
@Composable
fun RemoteAddControl(product: CatalogProduct, modifier: Modifier = Modifier) {
    val app = LocalAppState.current
    val cart = ServiceLocator.cart
    val state by cart.state.collectAsState()
    val pending by cart.pending.collectAsState()
    val confirmed = (state as? CartState.Loaded)?.cart?.quantityOf(product.skuId) ?: 0
    val control = purchaseControl(product, ServiceLocator.catalogCapabilities, confirmed, pending[product.skuId])
    when (control) {
        is PurchaseControl.Disabled -> Box(
            modifier.fillMaxWidth().clip(TazRadius.chip).background(TazColors.SurfaceSunken)
                .padding(vertical = TazSpace.xs, horizontal = TazSpace.sm)
                .semantics { disabled(); contentDescription = control.label },
            contentAlignment = Alignment.Center
        ) {
            Text(control.label, fontSize = TazType.captionSize, fontWeight = FontWeight.SemiBold, color = TazColors.TextTertiary, textAlign = TextAlign.Center)
        }
        PurchaseControl.Add -> Box(
            modifier.defaultMinSize(minWidth = 74.dp).fillMaxWidth().height(TazSize.buttonHeightSm)
                .clip(TazRadius.pill).border(BorderStroke(1.5.dp, TazColors.Success), TazRadius.pill)
                .semantics(mergeDescendants = true) { contentDescription = "Add ${product.name} to cart" }
                .tazPressable(
                    onClick = {
                        if (ServiceLocator.authSession.isAuthenticated) cart.increment(product.skuId, product.maxOrderQuantity)
                        else app.navigate(Screen.Login)
                    },
                    pressScale = TazPress.control, role = Role.Button
                ),
            contentAlignment = Alignment.Center
        ) { Text("ADD", color = TazColors.Success, fontSize = TazType.buttonSize, fontWeight = TazType.buttonWeight, maxLines = 1) }
        is PurchaseControl.Stepper -> QuantityPill(
            name = product.name, quantity = control.quantity, busy = control.pending, canIncrease = control.canIncrease,
            onMinus = { cart.decrement(product.skuId) },
            onPlus = { cart.increment(product.skuId, product.maxOrderQuantity) },
            modifier = modifier
        )
    }
}

@Composable
private fun QuantityPill(
    name: String, quantity: Int, busy: Boolean, canIncrease: Boolean,
    onMinus: () -> Unit, onPlus: () -> Unit, modifier: Modifier = Modifier
) {
    Row(
        modifier.defaultMinSize(minWidth = 74.dp).height(TazSize.buttonHeightSm)
            .clip(TazRadius.pill).background(TazColors.Success),
        horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically
    ) {
        StepperTouchTarget("Decrease quantity of $name", onMinus) { TazIcon(TazIcons.Minus, null, size = TazSize.iconSm, tint = TazColors.White) }
        Text(
            "$quantity", color = TazColors.White.copy(alpha = if (busy) 0.7f else 1f), fontSize = TazType.buttonSize,
            fontWeight = FontWeight.Bold, textAlign = TextAlign.Center, maxLines = 1
        )
        StepperTouchTarget(
            if (canIncrease) "Increase quantity of $name" else "Maximum quantity of $name reached",
            { if (canIncrease) onPlus() }
        ) { TazIcon(TazIcons.Plus, null, size = TazSize.iconSm, tint = TazColors.White.copy(alpha = if (canIncrease) 1f else 0.4f)) }
    }
}

/** The floating "View cart" bar for REMOTE. Count and subtotal only: never SKU names, never analytics. */
@Composable
fun BoxScope.RemoteCartBar(aboveNav: Boolean = false) {
    val app = LocalAppState.current
    val state by ServiceLocator.cart.state.collectAsState()
    val cart = (state as? CartState.Loaded)?.cart
    val count = cart?.itemCount ?: 0
    AnimatedVisibility(
        visible = count > 0,
        modifier = Modifier.align(Alignment.BottomCenter),
        enter = slideInVertically(tween(TazMotion.normal)) { it } + fadeIn(tween(TazMotion.fast)),
        exit = slideOutVertically(tween(TazMotion.fast)) { it } + fadeOut(tween(TazMotion.fast))
    ) {
        val s = cart?.toSummary()
        Row(
            Modifier
                .padding(bottom = if (aboveNav) TazSize.navBarHeight + 1.dp else 0.dp)
                .padding(horizontal = TazSpace.gutter, vertical = TazSpace.md)
                .navigationBarsPadding().fillMaxWidth()
                .shadow(10.dp, androidx.compose.foundation.shape.RoundedCornerShape(16.dp))
                .clip(androidx.compose.foundation.shape.RoundedCornerShape(16.dp))
                .background(TazColors.Green)
                .tazPressable(onClick = { app.navigate(Screen.Cart) }, pressScale = TazPress.compact)
                .height(64.dp).padding(horizontal = TazSpace.lg),
            verticalAlignment = Alignment.CenterVertically
        ) {
            TazIcon(TazIcons.Cart, null, size = TazSize.iconMd, tint = TazColors.White)
            Spacer(Modifier.width(TazSpace.md))
            Text(
                "${s?.itemsLabel ?: ""} · ${s?.subtotalLabel ?: ""}", color = TazColors.White, fontSize = TazType.titleSize,
                fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f)
            )
            Text("View cart", color = TazColors.White, fontSize = TazType.buttonSize, fontWeight = TazType.buttonWeight, maxLines = 1)
        }
    }
}

@Composable
fun RemoteCartScreen() {
    val app = LocalAppState.current
    val cart = ServiceLocator.cart
    val state by cart.state.collectAsState()
    val pending by cart.pending.collectAsState()
    val syncing by cart.syncing.collectAsState()
    var confirmClear by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { cart.refresh() }          // a GET also advances expiry housekeeping: always show the server's latest
    Column(Modifier.fillMaxSize().background(TazColors.Cream)) {
        TazTopBar(title = "Cart", onBack = { app.back() })
        Box(Modifier.weight(1f).fillMaxWidth()) {
            when (val s = state) {
                CartState.SignedOut -> EmptyState("🛒", "Log in to see your cart", "Your cart is saved to your account.", "Log in", onAction = { app.navigate(Screen.Login) })
                CartState.Idle, CartState.Loading -> Column(Modifier.padding(TazSpace.lg), verticalArrangement = Arrangement.spacedBy(TazSpace.md)) {
                    SkeletonBlock(height = 72.dp, corner = 14.dp); SkeletonBlock(height = 72.dp, corner = 14.dp)
                }
                is CartState.Failed -> CartFailurePanel(s.failure, onRetry = { cart.refresh() }, onLogin = { app.navigate(Screen.Login) })
                is CartState.Loaded -> {
                    if (s.cart.isEmpty) EmptyState("🛒", "Your cart is empty", "Add something from the catalogue.", "Browse", onAction = { app.back() })
                    else CartContent(s.cart, pending, syncing, confirmClear,
                        onClear = { if (confirmClear) { confirmClear = false; cart.clear() } else confirmClear = true },
                        onCancelClear = { confirmClear = false })
                }
            }
        }
    }
}

@Composable
private fun CartContent(
    cart: ServerCart, pending: Map<String, PendingTarget>, syncing: Boolean, confirmClear: Boolean,
    onClear: () -> Unit, onCancelClear: () -> Unit
) {
    val store = ServiceLocator.cart
    val summary = cart.toSummary()
    Column(Modifier.fillMaxSize()) {
        LazyColumn(
            Modifier.weight(1f).fillMaxWidth(),
            contentPadding = androidx.compose.foundation.layout.PaddingValues(TazSpace.lg),
            verticalArrangement = Arrangement.spacedBy(TazSpace.md)
        ) {
            if (summary.hasBlockedLines) item {
                Text(
                    "Some items can't be bought right now. Remove them to continue.",
                    fontSize = TazType.captionSize, color = TazColors.Danger,
                    modifier = Modifier.fillMaxWidth().clip(TazRadius.card).background(TazColors.Surface).padding(TazSpace.md)
                )
            }
            // Keyed by skuId. The row is NOT clickable: the cart carries no productId, so it never opens a PDP.
            items(cart.items, key = { it.skuId }) { line ->
                CartLineRow(line.toView(), pending[line.skuId], onMinus = { store.decrement(line.skuId) }, onPlus = { store.increment(line.skuId) }, onRemove = { store.remove(line.skuId) })
            }
        }
        Column(
            Modifier.fillMaxWidth().background(TazColors.Surface).padding(TazSpace.lg).navigationBarsPadding(),
            verticalArrangement = Arrangement.spacedBy(TazSpace.sm)
        ) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text("${summary.itemsLabel} · Subtotal", fontSize = TazType.bodySize, color = TazColors.TextSecondary, modifier = Modifier.weight(1f))
                Text(summary.subtotalLabel, fontSize = TazType.titleSize, fontWeight = FontWeight.Bold, color = TazColors.TextPrimary)
            }
            Text(summary.subtotalCaption, fontSize = TazType.captionSize, color = TazColors.TextTertiary)
            // REMOTE checkout does not exist yet: this never reaches the mock checkout.
            PillButton(
                text = "Checkout isn't available yet", onClick = {}, enabled = false,
                disabledHint = null, modifier = Modifier.fillMaxWidth()
            )
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Text(
                    if (confirmClear) "Tap again to clear your cart" else "Clear cart",
                    fontSize = TazType.captionSize, fontWeight = FontWeight.SemiBold, color = TazColors.Danger,
                    modifier = Modifier.tazPressable(onClick = onClear, pressScale = TazPress.compact).padding(TazSpace.sm)
                )
                if (confirmClear) Text(
                    "Cancel", fontSize = TazType.captionSize, color = TazColors.TextSecondary,
                    modifier = Modifier.tazPressable(onClick = onCancelClear, pressScale = TazPress.compact).padding(TazSpace.sm)
                )
                if (syncing) Text("Updating…", fontSize = TazType.captionSize, color = TazColors.TextTertiary)
            }
        }
    }
}

@Composable
private fun CartLineRow(v: CartLineView, pending: PendingTarget?, onMinus: () -> Unit, onPlus: () -> Unit, onRemove: () -> Unit) {
    val shown = when (pending) { is PendingTarget.Quantity -> pending.quantity; PendingTarget.Removing -> 0; null -> v.quantity }
    Column(
        Modifier.fillMaxWidth().clip(TazRadius.card).background(TazColors.Surface).padding(TazSpace.md),
        verticalArrangement = Arrangement.spacedBy(TazSpace.xs)
    ) {
        Text(v.title, fontSize = TazType.productNameSize, fontWeight = FontWeight.Medium, color = TazColors.TextPrimary, maxLines = 2, overflow = TextOverflow.Ellipsis)
        v.issues.forEach { Text(it, fontSize = TazType.captionSize, fontWeight = FontWeight.SemiBold, color = TazColors.Danger) }
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(TazSpace.sm)) {
            v.unitPriceLabel?.let { Text(it, fontSize = TazType.priceSize, fontWeight = TazType.priceWeight, color = TazColors.TextPrimary) }
            v.mrpLabel?.let { Text(it, fontSize = TazType.mrpSize, color = TazColors.TextTertiary, textDecoration = TextDecoration.LineThrough) }
            Spacer(Modifier.weight(1f))
            v.lineTotalLabel?.let { Text(it, fontSize = TazType.bodySize, fontWeight = FontWeight.SemiBold, color = TazColors.TextPrimary) }
        }
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            if (v.blocked) {
                // A blocked line can only be removed: no + (never quietly "fix" a line the server rejected).
                Text(
                    "Remove", fontSize = TazType.captionSize, fontWeight = FontWeight.SemiBold, color = TazColors.Danger,
                    modifier = Modifier.semantics { contentDescription = "Remove ${v.title}" }
                        .tazPressable(onClick = onRemove, pressScale = TazPress.compact, role = Role.Button).padding(TazSpace.sm)
                )
            } else {
                QuantityPill(v.title, shown, pending != null, v.canIncrease, onMinus, onPlus)
            }
        }
    }
}

@Composable
private fun CartFailurePanel(failure: CartFailure, onRetry: () -> Unit, onLogin: () -> Unit) {
    if (failure is CartFailure.Unauthenticated) {
        EmptyState("🛒", "Log in to see your cart", "Your session ended.", "Log in", onAction = onLogin)
        return
    }
    val (title, hint) = when (failure) {
        CartFailure.Network, CartFailure.Timeout -> "Couldn't reach Tazzzo" to "Check your connection and try again."
        CartFailure.Unavailable -> "Cart is temporarily unavailable" to "Please try again shortly."
        else -> "Couldn't load your cart" to "Please try again."
    }
    EmptyState("🛒", title, hint, "Try again", onAction = onRetry)
}
