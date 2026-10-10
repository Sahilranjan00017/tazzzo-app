package com.tazzzo.app.ui.order

import androidx.compose.foundation.background
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.material3.pulltorefresh.PullToRefreshDefaults
import androidx.compose.material3.pulltorefresh.rememberPullToRefreshState
import androidx.compose.runtime.LaunchedEffect
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
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.tazzzo.app.HomeTab
import com.tazzzo.app.LocalAppState
import com.tazzzo.app.Screen
import com.tazzzo.app.data.catalog.AppendState
import com.tazzzo.app.data.catalog.PagedState
import com.tazzzo.app.data.order.CustomerOrder
import com.tazzzo.app.data.order.CustomerOrderSummary
import com.tazzzo.app.data.order.OrderView
import com.tazzzo.app.data.order.view
import com.tazzzo.app.data.repository.ServiceLocator
import com.tazzzo.app.theme.TazColors
import com.tazzzo.app.theme.TazIcons
import com.tazzzo.app.theme.TazRadius
import com.tazzzo.app.theme.TazSize
import com.tazzzo.app.theme.TazSpace
import com.tazzzo.app.theme.TazType
import com.tazzzo.app.ui.checkout.Header
import com.tazzzo.app.ui.checkout.TextAction
import com.tazzzo.app.ui.common.EditorialEmptyState
import com.tazzzo.app.ui.common.EditorialFailureState
import com.tazzzo.app.ui.common.EditorialText
import com.tazzzo.app.ui.common.SkeletonBlock
import com.tazzzo.app.ui.common.TazIcon
import com.tazzzo.app.ui.common.TazzzoPrimaryButton
import com.tazzzo.app.ui.common.plain
import com.tazzzo.app.ui.interaction.TazPress
import com.tazzzo.app.ui.interaction.tazPressable

/*
 * REMOTE order surfaces (UI-06 skin). Built ONLY from the backend order held by OrderStore (the winning persisted snapshot,
 * including a replayed/duplicate-key one) or fetched by id: never `lastOrder`, the local cart, `BillCalculator`, local coin
 * or Club state. Money is the order's AUTHORITATIVE snapshot: "₹X due on delivery" / "Nothing due on delivery" — never
 * "paid"; a legacy order without money says "Amount details unavailable", never ₹0. The Orders tab is the customer's real
 * history (`GET /v1/customer/orders`); the detail is `GET /v1/customer/orders/{id}`.
 */

/** Loading outcome for one order id. */
sealed interface OrderLoad {
    data object Loading : OrderLoad
    data class Loaded(val order: CustomerOrder) : OrderLoad
    /** Not readable right now (not found, or the request failed): the id is never shown as an error code. */
    data object Missing : OrderLoad
}

@Composable
private fun rememberOrder(orderId: String, attempt: Int): OrderLoad {
    val state by produceState<OrderLoad>(OrderLoad.Loading, orderId, attempt) {
        value = OrderLoad.Loading
        value = ServiceLocator.orderStore.fetch(orderId)?.let { OrderLoad.Loaded(it) } ?: OrderLoad.Missing
    }
    return state
}

// ---- confirmation -----------------------------------------------------------------------------------------------------

@Composable
fun RemoteOrderSuccessScreen(orderId: String) {
    val app = LocalAppState.current
    val store = ServiceLocator.orderStore
    // Leaving the confirmation ends the "placed" state; the order itself stays readable for this session.
    DisposableEffect(Unit) { onDispose { store.acknowledge() } }
    var attempt by remember(orderId) { mutableStateOf(0) }
    val load = rememberOrder(orderId, attempt)
    OrderConfirmationLayout(
        orderId = orderId, load = load,
        actions = OrderActions(back = { app.goHome() }, continueShopping = { app.goHome() }, viewOrder = { app.navigate(Screen.OrderDetail(orderId)) }, retry = { attempt++ })
    )
}

class OrderActions(val back: () -> Unit, val continueShopping: () -> Unit, val viewOrder: () -> Unit = {}, val retry: () -> Unit = {})

/**
 * The premium confirmation: green success mark, "Order placed", support line, the order number, the payment fact with
 * the amount due, the frozen delivery address, the ordered items, and the one "Continue shopping" CTA. Rendered only when
 * OrderStore holds a confirmed real order (the route is reached from `OrderState.Placed`), never from a local success model.
 */
@Composable
fun OrderConfirmationLayout(orderId: String, load: OrderLoad, actions: OrderActions) {
    Box(Modifier.fillMaxSize().background(TazColors.Cream).testTag("orderConfirmation")) {
        when (load) {
            OrderLoad.Loading -> Column(Modifier.fillMaxSize()) { Spacer(Modifier.statusBarsPadding().height(TazSpace.xl)); SuccessMark(); OrderSkeleton() }
            OrderLoad.Missing -> Column(Modifier.fillMaxSize()) {
                Spacer(Modifier.statusBarsPadding().height(TazSpace.xl))
                SuccessMark()
                EditorialText(listOf(plain(OrderCopy.CONFIRMATION_TITLE)), size = TazType.editorialHeroSize, lineHeight = TazType.editorialHeroLine, color = TazColors.TextPrimary, modifier = Modifier.fillMaxWidth())
                Spacer(Modifier.height(TazSpace.sm))
                Text(OrderCopy.CONFIRMATION_LOAD_FAILED_BODY, fontSize = TazType.bodySize, lineHeight = TazType.bodyLine, color = TazColors.TextSecondary, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth().padding(horizontal = TazSpace.xxl))
                Spacer(Modifier.height(TazSpace.lg))
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center) { TextAction(OrderCopy.TRY_AGAIN, onClick = actions.retry) }
            }
            is OrderLoad.Loaded -> {
                val v = load.order.view()
                val amount = load.order.amountHeadline()
                Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).testTag("orderConfirmationContent")) {
                    Spacer(Modifier.statusBarsPadding().height(TazSpace.xl))
                    SuccessMark()
                    EditorialText(listOf(plain(OrderCopy.CONFIRMATION_TITLE)), size = TazType.editorialHeroSize, lineHeight = TazType.editorialHeroLine, color = TazColors.TextPrimary, modifier = Modifier.fillMaxWidth())
                    Spacer(Modifier.height(TazSpace.sm))
                    Text(OrderCopy.CONFIRMATION_SUPPORT, fontSize = TazType.bodySize, lineHeight = TazType.bodyLine, color = TazColors.TextSecondary, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth().padding(horizontal = TazSpace.xxl))
                    Spacer(Modifier.height(TazSpace.xxl))
                    Column(Modifier.padding(horizontal = TazSpace.lg), verticalArrangement = Arrangement.spacedBy(TazSpace.md)) {
                        OrderNumberCard(v, load.order)
                        PaymentCard(v, amount)
                        if (v.addressLines.isNotEmpty()) AddressCard(v)
                        ItemsCard(v)
                        SummaryCard(v, amount)
                    }
                    Spacer(Modifier.height(CTA_CLEARANCE).navigationBarsPadding())
                }
            }
        }
        // The one CTA; "View order" is a quiet secondary action.
        Column(
            Modifier.align(Alignment.BottomCenter).fillMaxWidth().background(TazColors.Cream).padding(horizontal = TazSpace.lg).navigationBarsPadding().padding(bottom = TazSpace.md, top = TazSpace.sm)
        ) {
            TazzzoPrimaryButton(OrderCopy.CONTINUE_SHOPPING, onClick = actions.continueShopping, modifier = Modifier.fillMaxWidth().testTag("continueShopping"))
            if (load is OrderLoad.Loaded) Row(Modifier.fillMaxWidth().padding(top = TazSpace.xs), horizontalArrangement = Arrangement.Center) { TextAction(OrderCopy.VIEW_ORDER, onClick = actions.viewOrder) }
        }
    }
}

@Composable
private fun SuccessMark() {
    Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
        Box(Modifier.size(96.dp).clip(CircleShape).background(TazColors.GreenSoft), contentAlignment = Alignment.Center) {
            Box(Modifier.size(64.dp).clip(CircleShape).background(TazColors.BrandEditorial), contentAlignment = Alignment.Center) {
                TazIcon(TazIcons.Check, null, size = 34.dp, tint = TazColors.EditorialOnDark)
            }
        }
        Spacer(Modifier.height(TazSpace.xl))
    }
}

// ---- detail ------------------------------------------------------------------------------------------------------------

@Composable
fun RemoteOrderDetailScreen(orderId: String) {
    val app = LocalAppState.current
    var attempt by remember(orderId) { mutableStateOf(0) }
    val load = rememberOrder(orderId, attempt)
    OrderDetailLayout(orderId, load, OrderActions(back = { app.back() }, continueShopping = { app.goHome() }, retry = { attempt++ }))
}

/** Order detail: Checkout after placement — status, items, delivery address, payment, summary, order number. */
@Composable
fun OrderDetailLayout(orderId: String, load: OrderLoad, actions: OrderActions) {
    Column(Modifier.fillMaxSize().background(TazColors.Cream).testTag("orderDetail")) {
        Header(OrderCopy.ORDER_TITLE, onBack = actions.back)
        when (load) {
            OrderLoad.Loading -> OrderSkeleton()
            OrderLoad.Missing -> EditorialEmptyState(TazIcons.Receipt, OrderCopy.LOAD_FAILED_TITLE, OrderCopy.LOAD_FAILED_BODY, OrderCopy.TRY_AGAIN, onAction = actions.retry)
            is OrderLoad.Loaded -> {
                val v = load.order.view()
                val amount = load.order.amountHeadline()
                Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = TazSpace.lg), verticalArrangement = Arrangement.spacedBy(TazSpace.md)) {
                    StatusCard(load.order, v)
                    if (v.timeline.size > 1 || v.slotLabel != null) TimelineCard(v)
                    ItemsCard(v)
                    if (v.addressLines.isNotEmpty()) AddressCard(v)
                    PaymentCard(v, amount)
                    SummaryCard(v, amount)
                    OrderNumberCard(v, load.order)
                    Spacer(Modifier.navigationBarsPadding().height(TazSpace.xxl))
                }
            }
        }
    }
}

// ---- Orders tab ----------------------------------------------------------------------------------------------------------

/** REMOTE Orders as a pushed route. */
@Composable
fun RemoteOrdersScreen() = RemoteOrdersContent(inTab = false)

/**
 * The Orders surface, as the ORDERS tab ([inTab]) or as a pushed route: the customer's REAL history (`GET /v1/customer/orders`,
 * newest first), with pull-to-refresh, "Load more", an empty, a failure and a signed-out state.
 */
@Composable
fun RemoteOrdersContent(inTab: Boolean) {
    val app = LocalAppState.current
    val store = ServiceLocator.orderStore
    val history by store.history.collectAsState()
    val surface = ordersSurface(app.isAuthenticated, history)
    // Idle = never loaded in this session, or invalidated by a new order / a new sign-in: (re)load page 1.
    LaunchedEffect(app.isAuthenticated, history is PagedState.Idle) { if (app.isAuthenticated && history is PagedState.Idle) store.openHistory() }
    var failures by remember { mutableStateOf(0) }
    LaunchedEffect(surface is OrdersSurface.Failed) { if (surface is OrdersSurface.Failed) failures++ else if (surface is OrdersSurface.Content) failures = 0 }
    OrdersTabLayout(
        surface = surface, inTab = inTab, consecutiveFailures = failures,
        actions = OrdersActions(
            back = { app.back() },
            startShopping = { app.homeTab = HomeTab.SHOP; if (!inTab) app.goHome() },
            openOrder = { app.navigate(Screen.OrderDetail(it)) },
            refresh = { store.refreshHistory() },
            loadMore = { store.loadMoreHistory() },
            signIn = { app.navigate(Screen.Login) }
        )
    )
}

class OrdersActions(
    val back: () -> Unit, val startShopping: () -> Unit, val openOrder: (String) -> Unit,
    val refresh: () -> Unit = {}, val loadMore: () -> Unit = {}, val signIn: () -> Unit = {}
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun OrdersTabLayout(surface: OrdersSurface, inTab: Boolean, actions: OrdersActions, consecutiveFailures: Int = 1) {
    Column(Modifier.fillMaxSize().background(TazColors.Cream).testTag("ordersTab")) {
        if (inTab) TabHeader(OrderCopy.ORDERS_TITLE) else Header(OrderCopy.ORDERS_TITLE, onBack = actions.back)
        val pull = rememberPullToRefreshState()
        PullToRefreshBox(
            isRefreshing = false, onRefresh = actions.refresh, state = pull, modifier = Modifier.fillMaxSize(),
            indicator = { PullToRefreshDefaults.Indicator(state = pull, isRefreshing = false, modifier = Modifier.align(Alignment.TopCenter), containerColor = TazColors.Surface, color = TazColors.BrandEditorial) }
        ) {
            LazyColumn(Modifier.fillMaxSize().padding(horizontal = TazSpace.lg), verticalArrangement = Arrangement.spacedBy(TazSpace.md)) {
                when (surface) {
                    OrdersSurface.SignedOut -> item { EditorialEmptyState(TazIcons.Profile, OrderCopy.SIGNED_OUT_TITLE, OrderCopy.SIGNED_OUT_BODY, OrderCopy.LOG_IN, onAction = actions.signIn) }
                    OrdersSurface.Loading -> item { OrderSkeleton() }
                    is OrdersSurface.Failed -> item { EditorialFailureState(surface.failure, consecutiveFailures, onRetry = actions.refresh) }
                    OrdersSurface.NoOrdersYet -> item { EditorialEmptyState(TazIcons.Receipt, OrderCopy.NO_ORDERS_TITLE, OrderCopy.NO_ORDERS_BODY, OrderCopy.START_SHOPPING, onAction = actions.startShopping) }
                    is OrdersSurface.Content -> {
                        items(surface.orders, key = { it.orderId }) { o -> OrderSummaryCard(o, onClick = { actions.openOrder(o.orderId) }) }
                        if (surface.hasMore) item(key = "more") {
                            Column(Modifier.fillMaxWidth().padding(vertical = TazSpace.sm), horizontalAlignment = Alignment.CenterHorizontally) {
                                when (surface.append) {
                                    AppendState.Loading -> SkeletonBlock(height = 72.dp, corner = TazRadius.tileDp)
                                    is AppendState.Failed -> {
                                        Text(OrderCopy.LOAD_MORE_FAILED, fontSize = TazType.captionSize, color = TazColors.TextSecondary)
                                        TextAction(OrderCopy.TRY_AGAIN, onClick = actions.loadMore)
                                    }
                                    AppendState.Idle -> TextAction(OrderCopy.LOAD_MORE, onClick = actions.loadMore)
                                }
                            }
                        }
                    }
                }
                item { Spacer(Modifier.height(TazSize.floatingNavClearance)) }
            }
        }
    }
}

@Composable
private fun TabHeader(title: String) {
    Row(Modifier.fillMaxWidth().statusBarsPadding().padding(start = TazSpace.lg, end = TazSpace.lg, top = TazSpace.lg, bottom = TazSpace.md)) {
        EditorialText(listOf(plain(title)), size = TazType.editorialHeadlineSize, lineHeight = TazType.editorialHeadlineLine, color = TazColors.BrandEditorial, textAlign = TextAlign.Start)
    }
}

/** One history row: status chip, placed time, item count and the payable — all from the backend summary. */
@Composable
private fun OrderSummaryCard(order: CustomerOrderSummary, onClick: () -> Unit) {
    val v = order.view()
    Column(
        Modifier.fillMaxWidth().clip(TazRadius.tile).background(TazColors.Surface)
            .tazPressable(onClick = onClick, pressScale = TazPress.card).semantics { contentDescription = "Order ${order.orderId}, ${order.status.label()}" }
            .padding(TazSpace.lg),
        verticalArrangement = Arrangement.spacedBy(TazSpace.xs)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            StatusChip(order.status.label())
            Spacer(Modifier.weight(1f))
            Text(v.amount ?: v.caption, fontSize = if (v.amount != null) TazType.titleSize else TazType.captionSize, fontWeight = FontWeight.Bold, color = TazColors.TextPrimary)
        }
        Text(order.orderId, fontSize = TazType.bodySize, fontWeight = FontWeight.Medium, color = TazColors.TextPrimary, maxLines = 1, overflow = TextOverflow.Ellipsis)
        Text(listOfNotNull(v.placedLabel, v.itemsLabel, v.caption.takeIf { v.amount != null }).joinToString(" · "), fontSize = TazType.captionSize, color = TazColors.TextSecondary)
    }
}

// ---- cards ----------------------------------------------------------------------------------------------------------------

@Composable
private fun Card(title: String?, content: @Composable () -> Unit) {
    Column(Modifier.fillMaxWidth().clip(TazRadius.tile).background(TazColors.Surface).padding(TazSpace.lg)) {
        if (title != null) {
            EditorialText(listOf(plain(title)), size = TazType.editorialSectionSize, lineHeight = TazType.editorialSectionLine, color = TazColors.TextPrimary, textAlign = TextAlign.Start)
            Spacer(Modifier.height(TazSpace.md))
        }
        content()
    }
}

@Composable
private fun StatusChip(label: String) {
    Box(Modifier.clip(TazRadius.pill).background(TazColors.GreenSoft).padding(horizontal = TazSpace.md, vertical = TazSpace.xs)) {
        Text(label, fontSize = TazType.captionSize, fontWeight = FontWeight.SemiBold, color = TazColors.BrandEditorial)
    }
}

@Composable
private fun StatusCard(order: CustomerOrder, v: OrderView) {
    Card(null) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(40.dp).clip(CircleShape).background(TazColors.BrandEditorial), contentAlignment = Alignment.Center) { TazIcon(TazIcons.Check, null, size = TazSize.iconMd, tint = TazColors.EditorialOnDark) }
            Spacer(Modifier.width(TazSpace.md))
            Column(Modifier.weight(1f)) {
                EditorialText(listOf(plain(v.title)), size = TazType.editorialSectionSize, lineHeight = TazType.editorialSectionLine, color = TazColors.TextPrimary, textAlign = TextAlign.Start)
                Text(v.dueLine ?: v.paymentLine, fontSize = TazType.captionSize, color = TazColors.TextSecondary)
            }
            StatusChip(order.status.label())
        }
    }
}

/** Only the steps the backend recorded, oldest first; plus the chosen delivery slot when the order has one. */
@Composable
private fun TimelineCard(v: OrderView) {
    Card(OrderCopy.SECTION_TIMELINE) {
        v.timeline.forEach { step ->
            Row(Modifier.fillMaxWidth().padding(vertical = TazSpace.xxs), verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(8.dp).clip(CircleShape).background(TazColors.BrandEditorial))
                Spacer(Modifier.width(TazSpace.sm))
                Text(step.label, fontSize = TazType.bodySize, color = TazColors.TextPrimary, modifier = Modifier.weight(1f))
                Text(step.timeLabel, fontSize = TazType.captionSize, color = TazColors.TextSecondary)
            }
        }
        v.slotLabel?.let {
            Spacer(Modifier.height(TazSpace.xs))
            Text("${OrderCopy.SLOT_LABEL}: $it", fontSize = TazType.captionSize, color = TazColors.TextSecondary)
        }
    }
}

@Composable
private fun OrderNumberCard(v: OrderView, order: CustomerOrder) {
    Card(null) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(OrderCopy.ORDER_NUMBER, fontSize = TazType.captionSize, color = TazColors.TextSecondary)
                Text(v.orderId, fontSize = TazType.titleSize, fontWeight = TazType.titleWeight, color = TazColors.TextPrimary, maxLines = 2, overflow = TextOverflow.Ellipsis, modifier = Modifier.testTag("orderId"))
                v.placedLabel?.let { Text("${OrderCopy.PLACED_LABEL} $it", fontSize = TazType.captionSize, color = TazColors.TextSecondary) }
            }
            StatusChip(order.status.label())
        }
    }
}

@Composable
private fun PaymentCard(v: OrderView, amount: OrderAmount) {
    Card(OrderCopy.SECTION_PAYMENT) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(22.dp).clip(CircleShape).background(TazColors.BrandEditorial), contentAlignment = Alignment.Center) { TazIcon(TazIcons.Check, null, size = 14.dp, tint = TazColors.EditorialOnDark) }
            Spacer(Modifier.width(TazSpace.md))
            Column(Modifier.weight(1f)) {
                Text(v.paymentLine, fontSize = TazType.titleSize, fontWeight = TazType.titleWeight, color = TazColors.TextPrimary)
                // COD_DUE with money: "₹X due on delivery" / "Nothing due on delivery". Legacy: the amount-free due line. Never "paid".
                Text(v.dueLine ?: "Payment details are on your order.", fontSize = TazType.captionSize, color = TazColors.TextSecondary)
            }
        }
    }
}

@Composable
private fun AddressCard(v: OrderView) {
    Card(OrderCopy.SECTION_ADDRESS) {
        Row(verticalAlignment = Alignment.Top) {
            TazIcon(TazIcons.Location, null, size = TazSize.iconSm, tint = TazColors.BrandEditorial)
            Spacer(Modifier.width(TazSpace.sm))
            Column { v.addressLines.forEach { Text(it, fontSize = TazType.bodySize, lineHeight = TazType.bodyLine, color = TazColors.TextPrimary) } }
        }
    }
}

/** The persisted item snapshot: title, quantity × unit price, line total. No live catalogue read, no imagery (none is persisted). */
@Composable
private fun ItemsCard(v: OrderView) {
    Card(v.itemsLabel) {
        v.lines.forEachIndexed { i, l ->
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
    }
}

/** Item subtotal · Benefit discount (only when > 0) · Amount due — from the persisted money; a legacy order shows its subtotal alone. */
@Composable
private fun SummaryCard(v: OrderView, amount: OrderAmount) {
    Card(OrderCopy.SECTION_SUMMARY) {
        v.moneyLines.forEach { line ->
            Row(Modifier.fillMaxWidth().padding(vertical = TazSpace.xxs), verticalAlignment = Alignment.CenterVertically) {
                Text(line.label, fontSize = TazType.bodySize, fontWeight = if (line.emphasised) FontWeight.SemiBold else FontWeight.Normal, color = if (line.emphasised) TazColors.TextPrimary else TazColors.TextSecondary, modifier = Modifier.weight(1f))
                Text(line.value, fontSize = if (line.emphasised) 20.sp else TazType.bodySize, fontWeight = if (line.emphasised) FontWeight.Bold else FontWeight.Medium, color = TazColors.TextPrimary)
            }
        }
        if (amount.isUnavailable) {
            Spacer(Modifier.height(TazSpace.xs))
            Text(OrderCopy.AMOUNT_UNAVAILABLE, fontSize = TazType.captionSize, color = TazColors.TextTertiary, modifier = Modifier.testTag("amountUnavailable"))
        } else {
            Spacer(Modifier.height(TazSpace.xs))
            Text(amount.caption.replaceFirstChar { it.uppercase() }.let { if (amount.amount == "₹0") it else "${amount.amount} ${amount.caption}" }, fontSize = TazType.captionSize, color = TazColors.TextSecondary)
        }
    }
}

@Composable
private fun OrderSkeleton() {
    Column(Modifier.fillMaxWidth().padding(horizontal = TazSpace.lg).testTag("orderSkeleton"), verticalArrangement = Arrangement.spacedBy(TazSpace.md)) {
        SkeletonBlock(height = 72.dp, corner = TazRadius.tileDp)
        SkeletonBlock(height = 140.dp, corner = TazRadius.tileDp)
        SkeletonBlock(height = 96.dp, corner = TazRadius.tileDp)
        SkeletonBlock(height = 120.dp, corner = TazRadius.tileDp)
    }
}

private val CTA_CLEARANCE = 128.dp
