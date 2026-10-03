package com.tazzzo.app.ui.checkout

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.tazzzo.app.LocalAppState
import com.tazzzo.app.Screen
import com.tazzzo.app.data.cart.CartState
import com.tazzzo.app.data.cart.ServerCart
import com.tazzzo.app.data.checkout.AddressSelection
import com.tazzzo.app.data.checkout.CheckoutAction
import com.tazzzo.app.data.checkout.CheckoutCopy
import com.tazzzo.app.data.checkout.CheckoutFailure
import com.tazzzo.app.data.checkout.CheckoutQuote
import com.tazzzo.app.data.checkout.CheckoutState
import com.tazzzo.app.data.checkout.DeliveryContent
import com.tazzzo.app.data.checkout.EXPIRED_VIEW
import com.tazzzo.app.data.checkout.FailureView
import com.tazzzo.app.data.checkout.NO_BINDING_MONEY_VIEW
import com.tazzzo.app.data.checkout.displayLines
import com.tazzzo.app.data.checkout.expiryLabel
import com.tazzzo.app.data.checkout.lineViews
import com.tazzzo.app.data.checkout.summary
import com.tazzzo.app.data.checkout.text
import com.tazzzo.app.data.checkout.view
import com.tazzzo.app.data.order.OrderFailure
import com.tazzzo.app.data.order.OrderLaunchGate
import com.tazzzo.app.data.order.OrderState
import com.tazzzo.app.data.order.PlaceOrderAvailability
import com.tazzzo.app.data.order.placeOrderAvailability
import com.tazzzo.app.data.order.view
import com.tazzzo.app.data.repository.ServiceLocator
import com.tazzzo.app.theme.TazColors
import com.tazzzo.app.theme.TazIcons
import com.tazzzo.app.theme.TazRadius
import com.tazzzo.app.theme.TazSize
import com.tazzzo.app.theme.TazSpace
import com.tazzzo.app.theme.TazType
import com.tazzzo.app.ui.common.EditorialEmptyState
import com.tazzzo.app.ui.common.EditorialText
import com.tazzzo.app.ui.common.SkeletonBlock
import com.tazzzo.app.ui.common.TazIcon
import com.tazzzo.app.ui.common.TazzzoPrimaryButton
import com.tazzzo.app.ui.common.plain
import com.tazzzo.app.ui.interaction.TazPress
import com.tazzzo.app.ui.interaction.tazPressable
import kotlinx.coroutines.delay

/**
 * REMOTE checkout (UI-05 skin over the unchanged PR-07/08/09 machine). ONE review surface over the backend quote, arranged
 * as the approved Tazzzo cards: Delivery address → Payment (cash on delivery, the only launch method — a fact, not a
 * choice) → Review (the quote's lines and its BINDING money rows) → the sticky "Place order" bar. There is no slot step:
 * the backend has no slot contract, so none is drawn. Every action dispatches to the same store methods as before;
 * nothing here reads the local cart, a local bill, or decides money.
 */
@Composable
fun RemoteCheckoutScreen() {
    val app = LocalAppState.current
    val store = ServiceLocator.checkoutQuote
    val state by store.state.collectAsState()
    val selection by ServiceLocator.checkoutAddresses.selection.collectAsState()
    val cartState by ServiceLocator.cart.state.collectAsState()
    val cart = (cartState as? CartState.Loaded)?.cart
    val orders = ServiceLocator.orderStore
    val orderState by orders.state.collectAsState()

    fun act(a: CheckoutAction) = when (a) {
        CheckoutAction.TryAgain -> store.retry()
        CheckoutAction.CheckOrder -> orders.checkOrder()
        CheckoutAction.ReviewCheckout, CheckoutAction.RefreshCheckout -> { orders.acknowledge(); store.start() }   // NEW attempt, NEW key
        CheckoutAction.ChooseAddress, CheckoutAction.ChangeAddress -> { orders.acknowledge(); app.navigate(Screen.Addresses) }
        CheckoutAction.GoToCart -> { orders.acknowledge(); app.navigate(Screen.Cart) }
        CheckoutAction.SignIn -> app.navigate(Screen.Login)
    }

    val quote = (state as? CheckoutState.Ready)?.quote
    var remainingLabel by remember(quote?.quoteId) { mutableStateOf<String?>(null) }
    LaunchedEffect(quote?.quoteId) {
        if (quote == null) return@LaunchedEffect
        while (true) { remainingLabel = store.remaining()?.let { expiryLabel(it) }; delay(1_000) }
    }

    CheckoutScreenLayout(
        checkout = state, order = orderState,
        delivery = (selection as? AddressSelection.Selected)?.stamp?.delivery, cart = cart,
        availability = placeOrderAvailability(OrderLaunchGate.enabled(ServiceLocator.catalogCapabilities), state, orderState),
        remainingLabel = remainingLabel,
        actions = CheckoutActions(back = { app.back() }, act = ::act, placeOrder = { orders.place() })
    )
}

class CheckoutActions(val back: () -> Unit, val act: (CheckoutAction) -> Unit, val placeOrder: () -> Unit)

/** The layout, independent of the stores (evidence renders it with sample state). State precedence is the machine's. */
@Composable
fun CheckoutScreenLayout(
    checkout: CheckoutState,
    order: OrderState,
    delivery: DeliveryContent?,
    cart: ServerCart?,
    availability: PlaceOrderAvailability,
    actions: CheckoutActions,
    remainingLabel: String? = null
) {
    Box(Modifier.fillMaxSize().background(TazColors.Cream).testTag("checkout")) {
        Column(Modifier.fillMaxSize()) {
            Header(PurchaseCopy.CHECKOUT_TITLE, onBack = actions.back)
            // An order attempt in progress, unresolved or conclusively failed takes over the screen: only "Check order" is
            // offered for an unresolved one, never a second order.
            when {
                order is OrderState.Placing -> PlacingPanel()
                order is OrderState.Ambiguous -> Recovery(order.failure.view(), null, cart, actions.act)
                order is OrderState.Failed && order.failure != OrderFailure.NotLaunched -> Recovery(order.failure.view(), null, cart, actions.act)
                else -> when (checkout) {
                    CheckoutState.SignedOut -> EditorialEmptyState(TazIcons.Profile, "Log in to check out", PurchaseCopy.CART_SIGNED_OUT_BODY, "Log in", onAction = { actions.act(CheckoutAction.SignIn) })
                    CheckoutState.Idle -> EditorialEmptyState(TazIcons.Receipt, "Ready to review your order", null, PurchaseCopy.REVIEW_CHECKOUT, onAction = { actions.act(CheckoutAction.ReviewCheckout) })
                    CheckoutState.Creating -> CheckoutSkeleton()
                    // A quote without binding money (legacy) is never shown as orderable: only "Refresh checkout" (a new quote).
                    is CheckoutState.Ready -> if (checkout.quote.money == null) Recovery(NO_BINDING_MONEY_VIEW, null, cart, actions.act)
                        else ReadyContent(checkout.quote, delivery, cart, availability, remainingLabel, actions)
                    CheckoutState.Expired -> Recovery(EXPIRED_VIEW, null, cart, actions.act)
                    is CheckoutState.Stale -> Recovery(checkout.reason.view(), null, cart, actions.act)
                    is CheckoutState.Failed -> Recovery(checkout.failure.view(checkout.canRetrySameKey), checkout.failure, cart, actions.act)
                }
            }
        }
    }
}

// ---- ready ------------------------------------------------------------------------------------------------------------

@Composable
private fun ReadyContent(
    quote: CheckoutQuote, delivery: DeliveryContent?, cart: ServerCart?,
    availability: PlaceOrderAvailability, remainingLabel: String?, actions: CheckoutActions
) {
    val summary = quote.summary()
    val money = quote.money ?: return
    Box(Modifier.fillMaxSize()) {
        Column(
            Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = TazSpace.lg).testTag("checkoutContent"),
            verticalArrangement = Arrangement.spacedBy(TazSpace.md)
        ) {
            // 1 · Delivery address — the CURRENT local projection of the selected saved address; the quote holds only its id.
            StepCard(1, PurchaseCopy.STEP_ADDRESS, trailing = { TextAction("Change") { actions.act(CheckoutAction.ChangeAddress) } }) {
                Row(verticalAlignment = Alignment.Top) {
                    TazIcon(TazIcons.Location, null, size = TazSize.iconSm, tint = TazColors.BrandEditorial)
                    Spacer(Modifier.width(TazSpace.sm))
                    Column { delivery?.displayLines()?.forEach { Text(it, fontSize = TazType.bodySize, lineHeight = TazType.bodyLine, color = TazColors.TextPrimary) } }
                }
            }
            // 2 · Payment — COD is the only launch method. Rendered as the selected fact; no unsupported methods are listed.
            val pay = paymentCard(money)
            StepCard(2, PurchaseCopy.STEP_PAYMENT) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.size(22.dp).clip(CircleShape).background(TazColors.BrandEditorial), contentAlignment = Alignment.Center) {
                        TazIcon(TazIcons.Check, null, size = 14.dp, tint = TazColors.EditorialOnDark)
                    }
                    Spacer(Modifier.width(TazSpace.md))
                    Column(Modifier.weight(1f)) {
                        Text(pay.method, fontSize = TazType.titleSize, fontWeight = TazType.titleWeight, color = TazColors.TextPrimary)
                        Text(pay.dueLine ?: pay.hint, fontSize = TazType.captionSize, color = TazColors.TextSecondary)
                    }
                }
            }
            // 3 · Review — the quote's lines (money from the quote only; titles from the cart only when it is the same cart).
            StepCard(3, PurchaseCopy.STEP_REVIEW, trailing = { Text(summary.itemsLabel, fontSize = TazType.captionSize, color = TazColors.TextSecondary) }) {
                quote.lineViews(cart).forEachIndexed { i, l ->
                    if (i > 0) Spacer(Modifier.height(TazSpace.sm))
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text(l.title, fontSize = TazType.productNameSize, fontWeight = FontWeight.Medium, color = TazColors.TextPrimary, maxLines = 2, overflow = TextOverflow.Ellipsis)
                            Text("${l.quantity} × ${l.unitPriceLabel}", fontSize = TazType.captionSize, color = TazColors.TextSecondary)
                        }
                        Spacer(Modifier.width(TazSpace.md))
                        Text(l.lineTotalLabel, fontSize = TazType.bodySize, fontWeight = FontWeight.SemiBold, color = TazColors.TextPrimary)
                    }
                }
                Spacer(Modifier.height(TazSpace.md))
                Box(Modifier.fillMaxWidth().height(1.dp).background(TazColors.CardBorder))
                Spacer(Modifier.height(TazSpace.md))
                // The V1 binding rows: Item subtotal, Benefit discount (only when > 0), Amount due. Nothing else exists.
                summary.lines.forEach { line ->
                    Row(Modifier.fillMaxWidth().padding(vertical = TazSpace.xxs), verticalAlignment = Alignment.CenterVertically) {
                        Text(line.label, fontSize = TazType.bodySize, fontWeight = if (line.emphasised) FontWeight.SemiBold else FontWeight.Normal,
                            color = if (line.emphasised) TazColors.TextPrimary else TazColors.TextSecondary, modifier = Modifier.weight(1f))
                        Text(line.value, fontSize = if (line.emphasised) 20.sp else TazType.bodySize, fontWeight = if (line.emphasised) FontWeight.Bold else FontWeight.Medium, color = TazColors.TextPrimary)
                    }
                }
                summary.dueNote?.let { Spacer(Modifier.height(TazSpace.xs)); Text(it, fontSize = TazType.captionSize, color = TazColors.TextSecondary) }
                remainingLabel?.let { Text(it, fontSize = TazType.captionSize, color = TazColors.TextTertiary) }
            }
            Spacer(Modifier.height(PLACE_BAR_CLEARANCE).navigationBarsPadding())
        }
        PlaceOrderBar(money, availability, onPlace = actions.placeOrder, modifier = Modifier.align(Alignment.BottomCenter))
    }
}

/** The sticky purchase surface: the amount due left (`₹0` reads as "Nothing due on delivery"), the one deep-green CTA right. */
@Composable
private fun PlaceOrderBar(money: com.tazzzo.app.data.model.PayableMoney, availability: PlaceOrderAvailability, onPlace: () -> Unit, modifier: Modifier) {
    val due = dueHeadline(money)
    Column(
        modifier.fillMaxWidth().background(TazColors.Cream).padding(horizontal = TazSpace.lg).navigationBarsPadding().padding(bottom = TazSpace.md, top = TazSpace.sm)
            .shadow(16.dp, TazRadius.sheetAll, ambientColor = Color.Black.copy(alpha = 0.10f), spotColor = Color.Black.copy(alpha = 0.16f))
            .clip(TazRadius.sheetAll).background(TazColors.Surface).padding(horizontal = TazSpace.lg, vertical = TazSpace.md).testTag("placeOrderBar")
    ) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(due.amount, fontSize = 24.sp, fontWeight = FontWeight.Bold, color = TazColors.TextPrimary, maxLines = 1)
                Text(due.caption, fontSize = TazType.captionSize, lineHeight = TazType.captionLine, color = TazColors.TextSecondary, maxLines = 2)
            }
            Spacer(Modifier.width(TazSpace.md))
            // The REAL order. Enabled only when the machine says placement is valid; never reaches the mock OrderPlacement.
            TazzzoPrimaryButton(
                CheckoutCopy.ORDER_CTA, onClick = onPlace, enabled = availability.enabled,
                loading = availability is PlaceOrderAvailability.Placing, trailingArrow = false, modifier = Modifier.width(170.dp)
            )
        }
        if (availability is PlaceOrderAvailability.LaunchGated) {
            Spacer(Modifier.height(TazSpace.xs))
            Text(CheckoutCopy.LAUNCH_GATED, fontSize = TazType.captionSize, color = TazColors.TextSecondary)
        }
    }
}

// ---- recovery / placing / loading --------------------------------------------------------------------------------------

/**
 * A failure or recovery surface. The actions are the domain's [FailureView.actions] in order: PAYABLE_CHANGED offers exactly
 * one — a NEW checkout — and nothing here can turn it into a retry; an ambiguous placement offers "Check order" alone.
 */
@Composable
private fun Recovery(v: FailureView, failure: CheckoutFailure?, cart: ServerCart?, onAction: (CheckoutAction) -> Unit) {
    val copy = v.recoveryCopy()
    val icon = when {
        copy.isPayableChanged -> TazIcons.Receipt
        copy.primary == CheckoutAction.CheckOrder -> TazIcons.Slot
        copy.primary == CheckoutAction.ChooseAddress || copy.primary == CheckoutAction.ChangeAddress -> TazIcons.Location
        copy.primary == CheckoutAction.GoToCart -> TazIcons.Cart
        else -> TazIcons.Info
    }
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = TazSpace.xxl, vertical = TazSpace.xxxl).testTag("recovery"), horizontalAlignment = Alignment.CenterHorizontally) {
        Box(Modifier.size(84.dp).clip(CircleShape).background(TazColors.GreenSoft), contentAlignment = Alignment.Center) { TazIcon(icon, null, size = 34.dp, tint = TazColors.BrandEditorial) }
        Spacer(Modifier.height(TazSpace.xl))
        EditorialText(listOf(plain(copy.title)), size = TazType.editorialStateTitleSize, lineHeight = TazType.editorialStateTitleLine, color = TazColors.TextPrimary)
        copy.support?.let { Spacer(Modifier.height(TazSpace.sm)); Text(it, fontSize = TazType.bodySize, lineHeight = TazType.bodyLine, color = TazColors.TextSecondary, textAlign = TextAlign.Center) }
        if (failure is CheckoutFailure.ItemsUnavailable) {
            Spacer(Modifier.height(TazSpace.lg))
            Column(Modifier.fillMaxWidth().clip(TazRadius.tile).background(TazColors.Surface).padding(TazSpace.lg), verticalArrangement = Arrangement.spacedBy(TazSpace.sm)) {
                failure.items.forEach { r ->
                    val title = cart?.item(r.skuId)?.title ?: CheckoutCopy.NEUTRAL_ITEM
                    Row(Modifier.fillMaxWidth()) {
                        Text(title, fontSize = TazType.bodySize, color = TazColors.TextPrimary, modifier = Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Text(r.text(), fontSize = TazType.captionSize, fontWeight = FontWeight.SemiBold, color = TazColors.Danger)
                    }
                }
            }
        }
        copy.primary?.let { a ->
            Spacer(Modifier.height(TazSpace.xxl))
            TazzzoPrimaryButton(a.label(), onClick = { onAction(a) }, modifier = Modifier.fillMaxWidth(0.8f))
        }
        copy.secondary.forEach { a ->
            Spacer(Modifier.height(TazSpace.md))
            TextAction(a.label()) { onAction(a) }
        }
    }
}

@Composable
private fun PlacingPanel() {
    Column(Modifier.fillMaxSize().padding(horizontal = TazSpace.xxl, vertical = TazSpace.xxxl).testTag("placing"), horizontalAlignment = Alignment.CenterHorizontally) {
        Box(Modifier.size(84.dp).clip(CircleShape).background(TazColors.GreenSoft), contentAlignment = Alignment.Center) {
            androidx.compose.material3.CircularProgressIndicator(Modifier.size(30.dp), color = TazColors.BrandEditorial, strokeWidth = 2.5.dp)
        }
        Spacer(Modifier.height(TazSpace.xl))
        EditorialText(listOf(plain(PurchaseCopy.PLACING)), size = TazType.editorialStateTitleSize, lineHeight = TazType.editorialStateTitleLine, color = TazColors.TextPrimary)
        Spacer(Modifier.height(TazSpace.sm))
        Text("Please keep the app open.", fontSize = TazType.bodySize, color = TazColors.TextSecondary)
    }
}

@Composable
private fun CheckoutSkeleton() {
    Column(Modifier.fillMaxSize().padding(horizontal = TazSpace.lg).testTag("checkoutSkeleton"), verticalArrangement = Arrangement.spacedBy(TazSpace.md)) {
        Text(PurchaseCopy.PREPARING, fontSize = TazType.captionSize, color = TazColors.TextTertiary)
        repeat(3) { SkeletonBlock(height = if (it == 2) 180.dp else 96.dp, corner = TazRadius.tileDp) }
    }
}

// ---- shared pieces -------------------------------------------------------------------------------------------------------

@Composable
internal fun Header(title: String, onBack: () -> Unit) {
    Row(Modifier.fillMaxWidth().statusBarsPadding().padding(start = TazSpace.md, end = TazSpace.lg, top = TazSpace.sm, bottom = TazSpace.md), verticalAlignment = Alignment.CenterVertically) {
        Box(
            Modifier.size(TazSize.touchTarget).clip(CircleShape).background(TazColors.Surface)
                .tazPressable(onClick = onBack, pressScale = TazPress.compact, role = Role.Button).semantics { contentDescription = "Back" },
            contentAlignment = Alignment.Center
        ) { TazIcon(TazIcons.Back, null, size = TazSize.iconSm, tint = TazColors.BrandEditorial) }
        Spacer(Modifier.width(TazSpace.md))
        EditorialText(listOf(plain(title)), size = TazType.editorialPageTitleSize, lineHeight = TazType.editorialPageTitleLine, color = TazColors.BrandEditorial, textAlign = TextAlign.Start)
    }
}

@Composable
internal fun StepCard(step: Int, title: String, trailing: (@Composable () -> Unit)? = null, content: @Composable () -> Unit) {
    Column(Modifier.fillMaxWidth().clip(TazRadius.tile).background(TazColors.Surface).padding(TazSpace.lg)) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(24.dp).clip(CircleShape).background(TazColors.GreenSoft), contentAlignment = Alignment.Center) {
                Text("$step", fontSize = TazType.captionSize, fontWeight = FontWeight.SemiBold, color = TazColors.BrandEditorial)
            }
            Spacer(Modifier.width(TazSpace.sm))
            EditorialText(listOf(plain(title)), size = TazType.editorialSectionSize, lineHeight = TazType.editorialSectionLine, color = TazColors.TextPrimary, textAlign = TextAlign.Start, modifier = Modifier.weight(1f))
            trailing?.invoke()
        }
        Spacer(Modifier.height(TazSpace.md))
        content()
    }
}

@Composable
internal fun TextAction(text: String, ink: Color = TazColors.BrandEditorial, enabled: Boolean = true, onClick: () -> Unit) {
    Box(
        Modifier.height(36.dp).clip(TazRadius.chip).tazPressable(onClick = onClick, enabled = enabled, pressScale = TazPress.compact, role = Role.Button)
            .padding(horizontal = TazSpace.sm),
        contentAlignment = Alignment.Center
    ) { Text(text, fontSize = TazType.captionSize, fontWeight = FontWeight.SemiBold, color = if (enabled) ink else TazColors.TextDisabled, maxLines = 1) }
}

private val PLACE_BAR_CLEARANCE = 108.dp
