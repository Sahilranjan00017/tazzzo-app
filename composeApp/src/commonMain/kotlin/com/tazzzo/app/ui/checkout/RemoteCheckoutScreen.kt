package com.tazzzo.app.ui.checkout

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.rememberScrollState
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.tazzzo.app.LocalAppState
import com.tazzzo.app.Screen
import com.tazzzo.app.data.cart.CartState
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
import com.tazzzo.app.theme.TazRadius
import com.tazzzo.app.theme.TazSpace
import com.tazzzo.app.theme.TazType
import com.tazzzo.app.ui.common.EmptyState
import com.tazzzo.app.ui.common.PillButton
import com.tazzzo.app.ui.common.SkeletonBlock
import com.tazzzo.app.ui.common.TazTopBar
import com.tazzzo.app.ui.interaction.TazPress
import com.tazzzo.app.ui.interaction.tazPressable
import kotlinx.coroutines.delay

/**
 * REMOTE checkout: ONE functional review screen over the backend quote. No steps, no slot, no payment choice, no
 * coupons/promotions/coins/Club, no local bill. Nothing here reads the local cart, `app.bill()` or any mock repository.
 * The money shown is the quote's BINDING money; Place order is enabled only for such a quote and a launch-enabled build.
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

    Column(Modifier.fillMaxSize().background(TazColors.Cream)) {
        TazTopBar(title = "Checkout", onBack = { app.back() })
        val os = orderState
        // An order attempt in progress, unresolved or conclusively failed takes over the screen: only "Check order" is offered
        // for an unresolved one, never a second order.
        if (os is OrderState.Placing) {
            Column(Modifier.padding(TazSpace.lg), verticalArrangement = Arrangement.spacedBy(TazSpace.md)) {
                Text("Placing your order…", fontSize = TazType.bodySize, color = TazColors.TextSecondary)
                SkeletonBlock(height = 72.dp, corner = 14.dp)
            }
        } else if (os is OrderState.Ambiguous) {
            Recovery(os.failure.view(), null, cart, ::act)
        } else if (os is OrderState.Failed && os.failure != OrderFailure.NotLaunched) {
            Recovery(os.failure.view(), null, cart, ::act)
        } else when (val s = state) {
            CheckoutState.SignedOut -> EmptyState("🛒", "Log in to check out", "Your cart is saved to your account.", "Log in", onAction = { act(CheckoutAction.SignIn) })
            CheckoutState.Idle -> EmptyState("🛒", "Ready to review your order", null, "Review checkout", onAction = { act(CheckoutAction.ReviewCheckout) })
            CheckoutState.Creating -> Column(Modifier.padding(TazSpace.lg), verticalArrangement = Arrangement.spacedBy(TazSpace.md)) {
                Text("Preparing your checkout…", fontSize = TazType.bodySize, color = TazColors.TextSecondary)
                SkeletonBlock(height = 72.dp, corner = 14.dp); SkeletonBlock(height = 72.dp, corner = 14.dp)
            }
            // A quote without binding money (legacy) is never shown as orderable: only "Refresh checkout" (a new quote).
            is CheckoutState.Ready -> if (s.quote.money == null) Recovery(NO_BINDING_MONEY_VIEW, null, cart, ::act) else ReadyContent(s.quote, (selection as? AddressSelection.Selected)?.stamp?.delivery, cart,
                availability = placeOrderAvailability(OrderLaunchGate.enabled(ServiceLocator.catalogCapabilities), s, orderState),
                onPlaceOrder = { orders.place() },
                onChangeAddress = { act(CheckoutAction.ChangeAddress) })
            CheckoutState.Expired -> Recovery(EXPIRED_VIEW, null, cart, ::act)
            is CheckoutState.Stale -> Recovery(s.reason.view(), null, cart, ::act)
            is CheckoutState.Failed -> Recovery(s.failure.view(s.canRetrySameKey), s.failure, cart, ::act)
        }
    }
}

@Composable
private fun Recovery(v: FailureView, failure: CheckoutFailure?, cart: com.tazzzo.app.data.cart.ServerCart?, onAction: (CheckoutAction) -> Unit) {
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(TazSpace.lg), horizontalAlignment = Alignment.CenterHorizontally) {
        Text(v.title, fontSize = TazType.h2Size, fontWeight = TazType.h2Weight, color = TazColors.TextPrimary)
        Spacer(Modifier.height(TazSpace.xs))
        Text(v.hint, fontSize = TazType.bodySize, color = TazColors.TextSecondary)
        if (failure is CheckoutFailure.ItemsUnavailable) {
            Spacer(Modifier.height(TazSpace.md))
            failure.items.forEach { r ->
                val title = cart?.item(r.skuId)?.title ?: CheckoutCopy.NEUTRAL_ITEM
                Row(Modifier.fillMaxWidth().padding(vertical = TazSpace.xs)) {
                    Text(title, fontSize = TazType.bodySize, color = TazColors.TextPrimary, modifier = Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text(r.text(), fontSize = TazType.captionSize, fontWeight = FontWeight.SemiBold, color = TazColors.Danger)
                }
            }
        }
        Spacer(Modifier.height(TazSpace.lg))
        v.actions.forEach { a ->
            PillButton(text = a.label(), onClick = { onAction(a) }, modifier = Modifier.fillMaxWidth(), filled = a == v.actions.first())
            Spacer(Modifier.height(TazSpace.sm))
        }
    }
}

@Composable
private fun ReadyContent(
    quote: CheckoutQuote, delivery: DeliveryContent?, cart: com.tazzzo.app.data.cart.ServerCart?,
    availability: PlaceOrderAvailability, onPlaceOrder: () -> Unit, onChangeAddress: () -> Unit
) {
    val store = ServiceLocator.checkoutQuote
    var remainingLabel by remember(quote.quoteId) { mutableStateOf<String?>(null) }
    LaunchedEffect(quote.quoteId) {
        while (true) {
            remainingLabel = store.remaining()?.let { expiryLabel(it) }
            delay(1_000)
        }
    }
    val summary = quote.summary()
    Column(Modifier.fillMaxSize()) {
        Column(
            Modifier.weight(1f).fillMaxWidth().verticalScroll(rememberScrollState()).padding(TazSpace.lg),
            verticalArrangement = Arrangement.spacedBy(TazSpace.md)
        ) {
            // The address shown is the CURRENT local projection of the selected saved address; the quote itself holds only its id.
            Column(Modifier.fillMaxWidth().clip(TazRadius.card).background(TazColors.Surface).padding(TazSpace.md), verticalArrangement = Arrangement.spacedBy(TazSpace.xxs)) {
                Text("Deliver to", fontSize = TazType.captionSize, color = TazColors.TextTertiary)
                delivery?.displayLines()?.forEach { Text(it, fontSize = TazType.bodySize, color = TazColors.TextPrimary) }
                Text("Change address", fontSize = TazType.captionSize, fontWeight = FontWeight.SemiBold, color = TazColors.Success,
                    modifier = Modifier.tazPressable(onClick = onChangeAddress, pressScale = TazPress.compact).padding(top = TazSpace.xs))
            }
            quote.lineViews(cart).forEach { l ->
                Row(Modifier.fillMaxWidth().clip(TazRadius.card).background(TazColors.Surface).padding(TazSpace.md), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(l.title, fontSize = TazType.productNameSize, fontWeight = FontWeight.Medium, color = TazColors.TextPrimary, maxLines = 2, overflow = TextOverflow.Ellipsis)
                        Text("${l.quantity} × ${l.unitPriceLabel}", fontSize = TazType.captionSize, color = TazColors.TextSecondary)
                    }
                    Text(l.lineTotalLabel, fontSize = TazType.bodySize, fontWeight = FontWeight.SemiBold, color = TazColors.TextPrimary)
                }
            }
        }
        Column(Modifier.fillMaxWidth().background(TazColors.Surface).padding(TazSpace.lg).navigationBarsPadding(), verticalArrangement = Arrangement.spacedBy(TazSpace.sm)) {
            summary.lines.forEachIndexed { i, line ->
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Text(if (i == 0) "${line.label} · ${summary.itemsLabel}" else line.label, fontSize = TazType.bodySize,
                        fontWeight = if (line.emphasised) FontWeight.SemiBold else FontWeight.Normal,
                        color = if (line.emphasised) TazColors.TextPrimary else TazColors.TextSecondary, modifier = Modifier.weight(1f))
                    Text(line.value, fontSize = if (line.emphasised) TazType.titleSize else TazType.bodySize,
                        fontWeight = if (line.emphasised) FontWeight.Bold else FontWeight.Medium, color = TazColors.TextPrimary)
                }
            }
            summary.dueNote?.let { Text(it, fontSize = TazType.captionSize, color = TazColors.TextSecondary) }
            remainingLabel?.let { Text(it, fontSize = TazType.captionSize, color = TazColors.TextTertiary) }
            // The REAL order. Disabled with neutral copy while production placement is not launch-enabled.
            // It never reaches the mock OrderPlacement.
            PillButton(text = CheckoutCopy.ORDER_CTA, onClick = onPlaceOrder, enabled = availability.enabled, modifier = Modifier.fillMaxWidth())
            if (availability is PlaceOrderAvailability.LaunchGated) {
                Text(CheckoutCopy.LAUNCH_GATED, fontSize = TazType.captionSize, color = TazColors.TextSecondary)
            }
        }
    }
}

private fun CheckoutAction.label(): String = when (this) {
    CheckoutAction.TryAgain -> "Try again"
    CheckoutAction.ReviewCheckout -> "Review checkout"
    CheckoutAction.RefreshCheckout -> "Refresh checkout"
    CheckoutAction.ChooseAddress -> "Choose address"
    CheckoutAction.ChangeAddress -> "Change address"
    CheckoutAction.GoToCart -> "Go to cart"
    CheckoutAction.SignIn -> "Log in"
    CheckoutAction.CheckOrder -> "Check order"
}
