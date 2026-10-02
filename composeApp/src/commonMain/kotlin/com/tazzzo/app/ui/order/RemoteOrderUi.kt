package com.tazzzo.app.ui.order

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.tazzzo.app.LocalAppState
import com.tazzzo.app.Screen
import com.tazzzo.app.data.order.CustomerOrder
import com.tazzzo.app.data.order.OrderView
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

/*
 * REMOTE order surfaces. Built ONLY from the backend order: never `lastOrder`, the local cart, `BillCalculator`, local coin or
 * Club state. Money is the order's AUTHORITATIVE money: "₹X due on delivery" / "Nothing due on delivery" — never "paid". A
 * legacy order without money shows its item subtotal alone, never as an amount due.
 */

/** Loading outcome for one order id. */
private sealed interface OrderLoad {
    data object Loading : OrderLoad
    data class Loaded(val order: CustomerOrder) : OrderLoad
    data object Missing : OrderLoad
}

@Composable
private fun rememberOrder(orderId: String): OrderLoad {
    val state by produceState<OrderLoad>(OrderLoad.Loading, orderId) {
        value = ServiceLocator.orderStore.fetch(orderId)?.let { OrderLoad.Loaded(it) } ?: OrderLoad.Missing
    }
    return state
}

@Composable
fun RemoteOrderSuccessScreen(orderId: String) {
    val app = LocalAppState.current
    val store = ServiceLocator.orderStore
    // Leaving the confirmation ends the "placed" state; the order itself stays readable for this session.
    DisposableEffect(Unit) { onDispose { store.acknowledge() } }
    val load = rememberOrder(orderId)
    Column(Modifier.fillMaxSize().background(TazColors.Cream)) {
        TazTopBar(title = "", onBack = { app.goHome() })
        when (load) {
            OrderLoad.Loading -> Column(Modifier.padding(TazSpace.lg), verticalArrangement = Arrangement.spacedBy(TazSpace.md)) {
                SkeletonBlock(height = 72.dp, corner = 14.dp); SkeletonBlock(height = 72.dp, corner = 14.dp)
            }
            OrderLoad.Missing -> EmptyState("📦", "Order placed", "We couldn't load the details right now.", "Continue shopping", onAction = { app.goHome() })
            is OrderLoad.Loaded -> Column(Modifier.fillMaxSize()) {
                OrderBody(load.order.view(), Modifier.weight(1f))
                Column(Modifier.fillMaxWidth().background(TazColors.Surface).padding(TazSpace.lg).navigationBarsPadding(), verticalArrangement = Arrangement.spacedBy(TazSpace.sm)) {
                    PillButton(text = "View order", onClick = { app.navigate(Screen.OrderDetail(orderId)) }, modifier = Modifier.fillMaxWidth(), filled = false)
                    PillButton(text = "Continue shopping", onClick = { app.goHome() }, modifier = Modifier.fillMaxWidth())
                }
            }
        }
    }
}

@Composable
fun RemoteOrderDetailScreen(orderId: String) {
    val app = LocalAppState.current
    val load = rememberOrder(orderId)
    Column(Modifier.fillMaxSize().background(TazColors.Cream)) {
        TazTopBar(title = "Order", onBack = { app.back() })
        when (load) {
            OrderLoad.Loading -> Column(Modifier.padding(TazSpace.lg), verticalArrangement = Arrangement.spacedBy(TazSpace.md)) { SkeletonBlock(height = 72.dp, corner = 14.dp) }
            OrderLoad.Missing -> EmptyState("📦", "We couldn't load this order", "Please try again later.", "Go back", onAction = { app.back() })
            is OrderLoad.Loaded -> OrderBody(load.order.view(), Modifier.fillMaxSize())
        }
    }
}

/**
 * REMOTE Orders: there is no history endpoint, so this is NOT a history screen. It shows the order placed in this session, if
 * any, clearly labelled, and otherwise says history isn't available yet.
 */
@Composable
fun RemoteOrdersScreen() = RemoteOrdersContent(inTab = false)

/**
 * The Orders surface, as the ORDERS tab ([inTab]) or as a pushed route. Truthful: there is no history endpoint, so the
 * empty state says so; the only order it can show is the one placed in this session.
 */
@Composable
fun RemoteOrdersContent(inTab: Boolean) {
    val app = LocalAppState.current
    val recent by ServiceLocator.orderStore.recent.collectAsState()
    Column(Modifier.fillMaxSize().background(TazColors.Cream)) {
        if (inTab) TazTopBar(title = "Orders") else TazTopBar(title = "Orders", onBack = { app.back() })
        val order = recent
        if (order == null) {
            EmptyState(
                "📦", com.tazzzo.app.ui.home.HomeCopy.ORDERS_UNAVAILABLE_TITLE, com.tazzzo.app.ui.home.HomeCopy.ORDERS_UNAVAILABLE_BODY,
                com.tazzzo.app.ui.home.HomeCopy.CONTINUE_SHOPPING, onAction = { if (inTab) app.homeTab = com.tazzzo.app.HomeTab.HOME else app.goHome() }
            )
        } else {
            val v = order.view()
            Column(Modifier.fillMaxSize().padding(TazSpace.lg), verticalArrangement = Arrangement.spacedBy(TazSpace.md)) {
                Text("Your recent order", fontSize = TazType.titleSize, fontWeight = TazType.titleWeight, color = TazColors.TextPrimary)
                Column(
                    Modifier.fillMaxWidth().clip(TazRadius.card).background(TazColors.Surface)
                        .tazPressable(onClick = { app.navigate(Screen.OrderDetail(order.orderId)) }, pressScale = TazPress.compact)
                        .padding(TazSpace.md),
                    verticalArrangement = Arrangement.spacedBy(TazSpace.xxs)
                ) {
                    Text(v.title, fontSize = TazType.bodySize, fontWeight = FontWeight.SemiBold, color = TazColors.TextPrimary)
                    Text(v.orderId, fontSize = TazType.captionSize, color = TazColors.TextSecondary)
                    Text(listOfNotNull(v.paymentLine, v.dueLine).joinToString(" · "), fontSize = TazType.captionSize, color = TazColors.TextSecondary)
                    Text("${v.itemsLabel} · ${v.headlineMoney.label} ${v.headlineMoney.value}", fontSize = TazType.captionSize, color = TazColors.TextTertiary)
                }
                Text("Full order history isn't available yet.", fontSize = TazType.captionSize, color = TazColors.TextTertiary)
            }
        }
    }
}

@Composable
private fun OrderBody(v: OrderView, modifier: Modifier = Modifier) {
    Column(modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(TazSpace.lg), verticalArrangement = Arrangement.spacedBy(TazSpace.md)) {
        Text(v.title, fontSize = TazType.h2Size, fontWeight = TazType.h2Weight, color = TazColors.TextPrimary)
        Text("Order ${v.orderId}", fontSize = TazType.bodySize, color = TazColors.TextSecondary)
        Column(Modifier.fillMaxWidth().clip(TazRadius.card).background(TazColors.Surface).padding(TazSpace.md), verticalArrangement = Arrangement.spacedBy(TazSpace.xxs)) {
            Text(v.paymentLine, fontSize = TazType.bodySize, fontWeight = FontWeight.SemiBold, color = TazColors.TextPrimary)
            Text(v.dueLine ?: "Payment details are on your order.", fontSize = TazType.captionSize, color = TazColors.TextSecondary)
        }
        if (v.addressLines.isNotEmpty()) {
            Column(Modifier.fillMaxWidth().clip(TazRadius.card).background(TazColors.Surface).padding(TazSpace.md), verticalArrangement = Arrangement.spacedBy(TazSpace.xxs)) {
                Text("Deliver to", fontSize = TazType.captionSize, color = TazColors.TextTertiary)
                v.addressLines.forEach { Text(it, fontSize = TazType.bodySize, color = TazColors.TextPrimary) }
            }
        }
        v.lines.forEach { l ->
            Row(Modifier.fillMaxWidth().clip(TazRadius.card).background(TazColors.Surface).padding(TazSpace.md), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(l.title, fontSize = TazType.productNameSize, fontWeight = FontWeight.Medium, color = TazColors.TextPrimary, maxLines = 2, overflow = TextOverflow.Ellipsis)
                    Text("${l.quantity} × ${l.unitPriceLabel}", fontSize = TazType.captionSize, color = TazColors.TextSecondary)
                }
                Text(l.lineTotalLabel, fontSize = TazType.bodySize, fontWeight = FontWeight.SemiBold, color = TazColors.TextPrimary)
            }
        }
        v.moneyLines.forEachIndexed { i, line ->
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text(if (i == 0) "${line.label} · ${v.itemsLabel}" else line.label, fontSize = TazType.bodySize,
                    fontWeight = if (line.emphasised) FontWeight.SemiBold else FontWeight.Normal,
                    color = if (line.emphasised) TazColors.TextPrimary else TazColors.TextSecondary, modifier = Modifier.weight(1f))
                Text(line.value, fontSize = if (line.emphasised) TazType.titleSize else TazType.bodySize,
                    fontWeight = if (line.emphasised) FontWeight.Bold else FontWeight.Medium, color = TazColors.TextPrimary)
            }
        }
        Spacer(Modifier.height(TazSpace.md))
    }
}
