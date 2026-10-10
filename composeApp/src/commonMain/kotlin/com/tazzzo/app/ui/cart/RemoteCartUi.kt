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
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.wrapContentSize
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
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.sp
import com.tazzzo.app.LocalAppState
import com.tazzzo.app.Screen
import com.tazzzo.app.data.cart.CartAction
import com.tazzzo.app.data.cart.CartFailure
import com.tazzzo.app.data.cart.addressScreen
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
fun RemoteAddControl(product: CatalogProduct, modifier: Modifier = Modifier, compact: Boolean = false, bar: Boolean = false) {
    val app = LocalAppState.current
    val cart = ServiceLocator.cart
    val state by cart.state.collectAsState()
    val pending by cart.pending.collectAsState()
    val confirmed = (state as? CartState.Loaded)?.cart?.quantityOf(product.skuId) ?: 0
    val control = purchaseControl(product, ServiceLocator.catalogCapabilities, confirmed, pending[product.skuId])
    if (bar) {
        BarAddControl(
            product, control,
            onAdd = { if (ServiceLocator.authSession.isAuthenticated) cart.increment(product.skuId, product.maxOrderQuantity) else app.navigate(Screen.Login) },
            onMinus = { cart.decrement(product.skuId) },
            onPlus = { cart.increment(product.skuId, product.maxOrderQuantity) },
            modifier = modifier
        )
        return
    }
    if (compact) {
        CompactAddControl(
            product, control,
            onAdd = { if (ServiceLocator.authSession.isAuthenticated) cart.increment(product.skuId, product.maxOrderQuantity) else app.navigate(Screen.Login) },
            onMinus = { cart.decrement(product.skuId) },
            onPlus = { cart.increment(product.skuId, product.maxOrderQuantity) },
            modifier = modifier
        )
        return
    }
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

/**
 * The product-card control of the approved design (UI Page `Home.jpeg`): a 36dp round deep-green "+" that becomes a
 * compact green stepper once the SERVER cart holds the SKU. Same decisions as the full control ([purchaseControl]);
 * only the drawing differs. A disabled product shows its reason as quiet text, never a dead button.
 */
@Composable
private fun CompactAddControl(
    product: CatalogProduct, control: PurchaseControl,
    onAdd: () -> Unit, onMinus: () -> Unit, onPlus: () -> Unit, modifier: Modifier = Modifier
) {
    when (control) {
        is PurchaseControl.Disabled -> Text(
            control.label, fontSize = TazType.microSize, fontWeight = FontWeight.SemiBold, color = TazColors.TextTertiary,
            textAlign = TextAlign.End, maxLines = 2,
            modifier = modifier.widthIn(max = 88.dp).semantics { disabled(); contentDescription = control.label }
        )
        PurchaseControl.Add -> Box(
            modifier.size(TazSize.touchTarget).wrapContentSize(unbounded = true).size(COMPACT_ADD)
                .clip(androidx.compose.foundation.shape.CircleShape).background(TazColors.BrandEditorial)
                .semantics(mergeDescendants = true) { contentDescription = "Add ${product.name} to cart" }
                .tazPressable(onClick = onAdd, pressScale = TazPress.control, role = Role.Button),
            contentAlignment = Alignment.Center
        ) { TazIcon(TazIcons.Plus, null, size = TazSize.iconSm, tint = TazColors.White) }
        is PurchaseControl.Stepper -> Row(
            modifier.height(COMPACT_ADD).clip(TazRadius.pill).background(TazColors.BrandEditorial),
            horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically
        ) {
            CompactStepperTarget("Decrease quantity of ${product.name}", onMinus) { TazIcon(TazIcons.Minus, null, size = TazSize.iconXs, tint = TazColors.White) }
            Text(
                "${control.quantity}", color = TazColors.White.copy(alpha = if (control.pending) 0.7f else 1f),
                fontSize = TazType.bodySize, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center, maxLines = 1,
                modifier = Modifier.widthIn(min = 18.dp)
            )
            CompactStepperTarget(
                if (control.canIncrease) "Increase quantity of ${product.name}" else "Maximum quantity of ${product.name} reached",
                { if (control.canIncrease) onPlus() }
            ) { TazIcon(TazIcons.Plus, null, size = TazSize.iconXs, tint = TazColors.White.copy(alpha = if (control.canIncrease) 1f else 0.4f)) }
        }
    }
}

/**
 * The PDP purchase surface (UI Page `Veg Page.jpeg`): a 54dp deep-green "Add to cart" pill with the bag glyph, which
 * becomes a 54dp stepper once the SERVER cart holds the SKU. Same [purchaseControl] decisions as every other style;
 * a disabled product shows its reason in a quiet pill, never a dead CTA.
 */
@Composable
private fun BarAddControl(
    product: CatalogProduct, control: PurchaseControl,
    onAdd: () -> Unit, onMinus: () -> Unit, onPlus: () -> Unit, modifier: Modifier = Modifier
) {
    when (control) {
        is PurchaseControl.Disabled -> Box(
            modifier.height(BAR_HEIGHT).clip(TazRadius.pill).background(TazColors.SurfaceSunken)
                .semantics { disabled(); contentDescription = control.label }.padding(horizontal = TazSpace.xl),
            contentAlignment = Alignment.Center
        ) { Text(control.label, fontSize = TazType.bodySize, fontWeight = FontWeight.SemiBold, color = TazColors.TextTertiary, maxLines = 1) }
        PurchaseControl.Add -> Row(
            modifier.height(BAR_HEIGHT).clip(TazRadius.pill).background(TazColors.BrandEditorial)
                .semantics(mergeDescendants = true) { contentDescription = "Add ${product.name} to cart" }
                .tazPressable(onClick = onAdd, pressScale = TazPress.control, role = Role.Button)
                .padding(horizontal = TazSpace.xxl),
            horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically
        ) {
            TazIcon(TazIcons.Bag, null, size = TazSize.iconMd, tint = TazColors.EditorialOnDark)
            Spacer(Modifier.width(TazSpace.md))
            Text("Add to cart", fontFamily = com.tazzzo.app.theme.tazEditorialFamily(), fontSize = 19.sp, color = TazColors.EditorialOnDark, maxLines = 1)
        }
        is PurchaseControl.Stepper -> Row(
            modifier.height(BAR_HEIGHT).clip(TazRadius.pill).background(TazColors.BrandEditorial).padding(horizontal = TazSpace.sm),
            horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically
        ) {
            BarStepperTarget("Decrease quantity of ${product.name}", onMinus) { TazIcon(TazIcons.Minus, null, size = TazSize.iconMd, tint = TazColors.EditorialOnDark) }
            Text(
                "${control.quantity}", color = TazColors.EditorialOnDark.copy(alpha = if (control.pending) 0.7f else 1f),
                fontSize = 20.sp, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center, maxLines = 1, modifier = Modifier.widthIn(min = 32.dp)
            )
            BarStepperTarget(
                if (control.canIncrease) "Increase quantity of ${product.name}" else "Maximum quantity of ${product.name} reached",
                { if (control.canIncrease) onPlus() }
            ) { TazIcon(TazIcons.Plus, null, size = TazSize.iconMd, tint = TazColors.EditorialOnDark.copy(alpha = if (control.canIncrease) 1f else 0.4f)) }
        }
    }
}

@Composable
private fun BarStepperTarget(contentDescription: String, onClick: () -> Unit, content: @Composable () -> Unit) {
    Box(
        Modifier.size(TazSize.touchTarget).clip(androidx.compose.foundation.shape.CircleShape)
            .semantics(mergeDescendants = true) { this.contentDescription = contentDescription }
            .tazPressable(onClick = onClick, pressScale = TazPress.compact, role = Role.Button),
        contentAlignment = Alignment.Center
    ) { content() }
}

private val BAR_HEIGHT = 54.dp

/** A 36dp visual target whose hit area is the 44dp accessibility minimum. */
@Composable
private fun CompactStepperTarget(contentDescription: String, onClick: () -> Unit, content: @Composable () -> Unit) {
    Box(
        Modifier.size(COMPACT_ADD).wrapContentSize(unbounded = true).size(TazSize.touchTarget)
            .semantics(mergeDescendants = true) { this.contentDescription = contentDescription }
            .tazPressable(onClick = onClick, pressScale = TazPress.compact, role = Role.Button),
        contentAlignment = Alignment.Center
    ) { content() }
}

private val COMPACT_ADD = 36.dp

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
    val canCheckout = ServiceLocator.catalogCapabilities.checkoutIntegration
    CartScreenLayout(
        state = state, pending = pending, syncing = syncing, confirmClear = confirmClear, checkoutSupported = canCheckout,
        actions = CartActions(
            back = { app.back() },
            startShopping = { app.homeTab = com.tazzzo.app.HomeTab.SHOP; app.goHome() },
            login = { app.navigate(Screen.Login) },
            retry = { cart.refresh() },
            minus = { cart.decrement(it) }, plus = { cart.increment(it) },
            lineAction = { sku, act ->
                when (act) {
                    CartAction.Remove -> cart.remove(sku)
                    CartAction.RetryCart -> cart.refresh()
                    is CartAction.ReduceQuantity -> cart.setQuantity(sku, act.target)
                    else -> act.addressScreen()?.let { app.navigate(it) }
                }
            },
            reviewCheckout = {
                // Real checkout: a quote of the SERVER cart. An unresolved order attempt must be checked first: never open a new quote beside it.
                val os = ServiceLocator.orderStore.state.value
                if (os !is com.tazzzo.app.data.order.OrderState.Placing && os !is com.tazzzo.app.data.order.OrderState.Ambiguous) ServiceLocator.checkoutQuote.enter()
                app.navigate(Screen.Checkout)
            },
            clear = { if (confirmClear) { confirmClear = false; cart.clear() } else confirmClear = true },
            cancelClear = { confirmClear = false }
        )
    )
}

class CartActions(
    val back: () -> Unit, val startShopping: () -> Unit, val login: () -> Unit, val retry: () -> Unit,
    val minus: (String) -> Unit, val plus: (String) -> Unit, val lineAction: (String, CartAction) -> Unit,
    val reviewCheckout: () -> Unit, val clear: () -> Unit, val cancelClear: () -> Unit
)

/**
 * The cart (UI-05), in the approved language: editorial "Your cart", one rounded card per SERVER line with the real photo
 * through the shared pipeline, the backend's unit price / struck MRP / line total, and the deep-green stepper on the same
 * cart mutations as everywhere else. The summary shows the server's item subtotal only — never a delivery fee, tax,
 * savings or coupon — and says so; the money preview belongs to checkout.
 */
@Composable
fun CartScreenLayout(
    state: CartState, pending: Map<String, PendingTarget>, syncing: Boolean, confirmClear: Boolean, checkoutSupported: Boolean, actions: CartActions
) {
    Box(Modifier.fillMaxSize().background(TazColors.Cream).testTag("cart")) {
        Column(Modifier.fillMaxSize()) {
            com.tazzzo.app.ui.checkout.Header(com.tazzzo.app.ui.checkout.PurchaseCopy.CART_TITLE, onBack = actions.back)
            when (state) {
                CartState.SignedOut -> com.tazzzo.app.ui.common.EditorialEmptyState(TazIcons.Profile, com.tazzzo.app.ui.checkout.PurchaseCopy.CART_SIGNED_OUT_TITLE, com.tazzzo.app.ui.checkout.PurchaseCopy.CART_SIGNED_OUT_BODY, "Log in", onAction = actions.login)
                CartState.Idle, CartState.Loading -> CartSkeleton()
                is CartState.Failed -> CartFailurePanel(state.failure, onRetry = actions.retry, onLogin = actions.login)
                is CartState.Loaded ->
                    if (state.cart.isEmpty) com.tazzzo.app.ui.common.EditorialEmptyState(TazIcons.Bag, com.tazzzo.app.ui.checkout.PurchaseCopy.CART_EMPTY_TITLE, com.tazzzo.app.ui.checkout.PurchaseCopy.CART_EMPTY_BODY, com.tazzzo.app.ui.checkout.PurchaseCopy.START_SHOPPING, onAction = actions.startShopping)
                    else CartContent(state.cart, pending, syncing, confirmClear, checkoutSupported, actions)
            }
        }
    }
}

@Composable
private fun CartContent(cart: ServerCart, pending: Map<String, PendingTarget>, syncing: Boolean, confirmClear: Boolean, checkoutSupported: Boolean, actions: CartActions) {
    val summary = cart.toSummary()
    Box(Modifier.fillMaxSize()) {
        LazyColumn(
            Modifier.fillMaxSize().testTag("cartLines"),
            contentPadding = androidx.compose.foundation.layout.PaddingValues(start = TazSpace.lg, end = TazSpace.lg, bottom = CART_BAR_CLEARANCE),
            verticalArrangement = Arrangement.spacedBy(TazSpace.md)
        ) {
            if (summary.hasBlockedLines) item {
                Row(Modifier.fillMaxWidth().clip(TazRadius.card).background(TazColors.WarningSoft).padding(TazSpace.md), verticalAlignment = Alignment.CenterVertically) {
                    TazIcon(TazIcons.Info, null, size = TazSize.iconSm, tint = TazColors.Warning)
                    Spacer(Modifier.width(TazSpace.sm))
                    Text("Some items need your attention before you can check out.", fontSize = TazType.captionSize, fontWeight = FontWeight.SemiBold, color = TazColors.Warning)
                }
            }
            // Keyed by skuId. The row is NOT clickable: the cart carries no productId, so it never opens a PDP.
            items(cart.items, key = { it.skuId }) { line ->
                CartLineCard(line, line.toView(), pending[line.skuId], onMinus = { actions.minus(line.skuId) }, onPlus = { actions.plus(line.skuId) }, onAction = { actions.lineAction(line.skuId, it) })
            }
            item {
                Row(Modifier.fillMaxWidth().padding(top = TazSpace.xs), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                    com.tazzzo.app.ui.checkout.TextAction(
                        if (confirmClear) com.tazzzo.app.ui.checkout.PurchaseCopy.CLEAR_CART_CONFIRM else com.tazzzo.app.ui.checkout.PurchaseCopy.CLEAR_CART, TazColors.Danger, onClick = actions.clear
                    )
                    if (confirmClear) com.tazzzo.app.ui.checkout.TextAction("Cancel", TazColors.TextSecondary, onClick = actions.cancelClear)
                }
            }
        }
        // The sticky summary: the server's item subtotal and the one deep-green CTA into the real checkout.
        val canCheckout = checkoutSupported && !summary.hasBlockedLines && !syncing && pending.isEmpty()
        // The strip under the bar is cream so scrolled lines never show through the inset gap.
        Column(
            Modifier.align(Alignment.BottomCenter).fillMaxWidth().background(TazColors.Cream).padding(horizontal = TazSpace.lg).navigationBarsPadding().padding(bottom = TazSpace.md, top = TazSpace.sm)
                .shadow(16.dp, TazRadius.sheetAll, ambientColor = androidx.compose.ui.graphics.Color.Black.copy(alpha = 0.10f), spotColor = androidx.compose.ui.graphics.Color.Black.copy(alpha = 0.16f))
                .clip(TazRadius.sheetAll).background(TazColors.Surface).padding(horizontal = TazSpace.lg, vertical = TazSpace.md).testTag("cartSummary")
        ) {
            val cta = if (summary.hasBlockedLines) com.tazzzo.app.ui.checkout.PurchaseCopy.RESOLVE_ITEMS else com.tazzzo.app.ui.checkout.PurchaseCopy.REVIEW_CHECKOUT
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(summary.subtotalLabel, fontSize = 24.sp, fontWeight = FontWeight.Bold, color = TazColors.TextPrimary, maxLines = 1)
                    Text(summary.itemsLabel, fontSize = TazType.captionSize, color = TazColors.TextSecondary, maxLines = 1)
                }
                if (!summary.hasBlockedLines) {
                    Spacer(Modifier.width(TazSpace.md))
                    com.tazzzo.app.ui.common.TazzzoPrimaryButton(cta, onClick = actions.reviewCheckout, enabled = canCheckout, trailingArrow = false, modifier = Modifier.widthIn(min = 160.dp, max = 200.dp))
                }
            }
            // A blocked cart's longer CTA takes its own full-width row so the label never wraps or clips at 320dp.
            if (summary.hasBlockedLines) {
                Spacer(Modifier.height(TazSpace.sm))
                com.tazzzo.app.ui.common.TazzzoPrimaryButton(cta, onClick = actions.reviewCheckout, enabled = false, trailingArrow = false, modifier = Modifier.fillMaxWidth())
            }
            Text(
                if (syncing) "Updating…" else summary.subtotalCaption,
                fontSize = TazType.captionSize, color = TazColors.TextTertiary, modifier = Modifier.padding(top = TazSpace.xs)
            )
        }
    }
}

/** One server line: photo well, title, issues, unit price + struck MRP, line total, and the stepper or the issue's recovery actions. */
@Composable
private fun CartLineCard(line: com.tazzzo.app.data.cart.CartItem, v: CartLineView, pending: PendingTarget?, onMinus: () -> Unit, onPlus: () -> Unit, onAction: (CartAction) -> Unit) {
    val shown = when (pending) { is PendingTarget.Quantity -> pending.quantity; PendingTarget.Removing -> 0; null -> v.quantity }
    Row(Modifier.fillMaxWidth().clip(TazRadius.tile).background(TazColors.Surface).padding(TazSpace.md), verticalAlignment = Alignment.Top) {
        com.tazzzo.app.ui.common.CatalogProductImage(url = line.imageUrl, name = v.title, modifier = Modifier.width(72.dp).clip(TazRadius.card), contentPadding = 4.dp)
        Spacer(Modifier.width(TazSpace.md))
        Column(Modifier.weight(1f)) {
            Text(v.title, fontSize = TazType.productNameSize, lineHeight = TazType.productNameLine, fontWeight = FontWeight.Medium, color = TazColors.TextPrimary, maxLines = 2, overflow = TextOverflow.Ellipsis)
            v.issues.forEach { Text(it, fontSize = TazType.captionSize, fontWeight = FontWeight.SemiBold, color = TazColors.Danger) }
            Spacer(Modifier.height(TazSpace.xs))
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(TazSpace.sm)) {
                v.unitPriceLabel?.let { Text(it, fontSize = TazType.priceSize, fontWeight = TazType.priceWeight, color = TazColors.TextPrimary) }
                v.mrpLabel?.let { Text(it, fontSize = TazType.mrpSize, color = TazColors.TextTertiary, textDecoration = TextDecoration.LineThrough) }
            }
            Spacer(Modifier.height(TazSpace.sm))
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                if (v.blocked) {
                    // Recovery depends on the issue: never "Remove" as the only way out of a recoverable one.
                    Row(Modifier.weight(1f), horizontalArrangement = Arrangement.spacedBy(TazSpace.xs)) {
                        v.actions.forEach { act ->
                            val label = act.label()
                            Box(
                                Modifier.height(36.dp).clip(TazRadius.pill).background(if (act == CartAction.Remove) TazColors.DangerSoft else TazColors.GreenSoft)
                                    .semantics { contentDescription = "$label ${v.title}" }
                                    .tazPressable(onClick = { onAction(act) }, pressScale = TazPress.compact, role = Role.Button).padding(horizontal = TazSpace.md),
                                contentAlignment = Alignment.Center
                            ) { Text(label, fontSize = TazType.captionSize, fontWeight = FontWeight.SemiBold, color = if (act == CartAction.Remove) TazColors.Danger else TazColors.BrandEditorial, maxLines = 1) }
                        }
                    }
                } else {
                    CartStepper(v.title, shown, pending != null, v.canIncrease, onMinus, onPlus)
                    Spacer(Modifier.weight(1f))
                }
                v.lineTotalLabel?.let { Text(it, fontSize = TazType.titleSize, fontWeight = FontWeight.Bold, color = TazColors.TextPrimary, maxLines = 1) }
            }
        }
    }
}

/** The cart's stepper: the compact deep-green pill (same callbacks as before; 44dp hit areas on − and +). */
@Composable
private fun CartStepper(name: String, quantity: Int, busy: Boolean, canIncrease: Boolean, onMinus: () -> Unit, onPlus: () -> Unit) {
    Row(Modifier.height(COMPACT_ADD).clip(TazRadius.pill).background(TazColors.BrandEditorial), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
        CompactStepperTarget("Decrease quantity of $name", onMinus) { TazIcon(TazIcons.Minus, null, size = TazSize.iconXs, tint = TazColors.White) }
        Text("$quantity", color = TazColors.White.copy(alpha = if (busy) 0.7f else 1f), fontSize = TazType.bodySize, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center, maxLines = 1, modifier = Modifier.widthIn(min = 22.dp))
        CompactStepperTarget(if (canIncrease) "Increase quantity of $name" else "Maximum quantity of $name reached", { if (canIncrease) onPlus() }) {
            TazIcon(TazIcons.Plus, null, size = TazSize.iconXs, tint = TazColors.White.copy(alpha = if (canIncrease) 1f else 0.4f))
        }
    }
}

@Composable
private fun CartSkeleton() {
    Column(Modifier.fillMaxSize().padding(horizontal = TazSpace.lg).testTag("cartSkeleton"), verticalArrangement = Arrangement.spacedBy(TazSpace.md)) {
        repeat(3) {
            Row(Modifier.fillMaxWidth().clip(TazRadius.tile).background(TazColors.Surface).padding(TazSpace.md)) {
                Box(Modifier.size(72.dp).clip(TazRadius.card).background(TazColors.SurfaceSunken))
                Spacer(Modifier.width(TazSpace.md))
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(TazSpace.sm)) { SkeletonBlock(height = 14.dp); SkeletonBlock(width = 90.dp, height = 12.dp); SkeletonBlock(width = 110.dp, height = 36.dp, corner = 18.dp) }
            }
        }
    }
}

@Composable
private fun CartAction.label(): String = when (this) {
    CartAction.Remove -> "Remove"
    CartAction.RetryCart -> "Retry"
    CartAction.ChooseAddress -> "Choose address"
    CartAction.ChangeAddress -> "Change address"
    is CartAction.ReduceQuantity -> "Update to $target"
}

@Composable
private fun CartFailurePanel(failure: CartFailure, onRetry: () -> Unit, onLogin: () -> Unit) {
    if (failure is CartFailure.Unauthenticated) {
        com.tazzzo.app.ui.common.EditorialEmptyState(TazIcons.Profile, com.tazzzo.app.ui.checkout.PurchaseCopy.CART_SIGNED_OUT_TITLE, "Your session ended.", "Log in", onAction = onLogin)
        return
    }
    val (title, hint) = when (failure) {
        CartFailure.Network, CartFailure.Timeout -> "Couldn't reach Tazzzo" to "Check your connection and try again."
        CartFailure.Unavailable -> "Cart is temporarily unavailable" to "Please try again shortly."
        else -> "Couldn't load your cart" to "Please try again."
    }
    com.tazzzo.app.ui.common.EditorialEmptyState(TazIcons.Offline, title, hint, "Try again", onAction = onRetry)
}

private val CART_BAR_CLEARANCE = 120.dp
