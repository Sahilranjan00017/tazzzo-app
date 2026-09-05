package com.tazzzo.app.ui.home

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.tazzzo.app.LocalAppState
import com.tazzzo.app.analytics.Analytics
import com.tazzzo.app.analytics.AnalyticsEvents
import com.tazzzo.app.Screen
import com.tazzzo.app.data.model.Order
import com.tazzzo.app.data.model.OrderStatus
import com.tazzzo.app.data.repository.ServiceLocator
import com.tazzzo.app.ui.interaction.TazPress
import com.tazzzo.app.ui.interaction.tazPressable
import com.tazzzo.app.ui.interaction.TazHaptic
import com.tazzzo.app.theme.TazColors
import com.tazzzo.app.theme.TazIcons
import com.tazzzo.app.theme.TazRadius
import com.tazzzo.app.theme.TazSize
import com.tazzzo.app.theme.TazSpace
import com.tazzzo.app.theme.TazType
import com.tazzzo.app.ui.common.CartBar
import com.tazzzo.app.ui.common.PillButton
import com.tazzzo.app.ui.common.TazIcon
import com.tazzzo.app.ui.common.TazTopBar
import com.tazzzo.app.ui.state.rememberLoad
import com.tazzzo.app.ui.common.StateHost

/**
 * The four states an order can be in. The model has exactly these — the UI
 * never invents a "cancelled" or "failed" step it cannot source.
 */
private val stepLabels = listOf("Placed", "Packed", "On the way", "Delivered")

internal fun orderStatusLabel(status: OrderStatus): String = when (status) {
    OrderStatus.PLACED -> "Placed"
    OrderStatus.PACKED -> "Packed"
    OrderStatus.ON_THE_WAY -> "On the way"
    OrderStatus.DELIVERED -> "Delivered"
}

private fun itemsSummary(order: Order): String {
    val names = order.lines.map { it.product.name }
    val head = names.take(2).joinToString(", ")
    return if (names.size > 2) "$head + ${names.size - 2} more" else head
}

@Composable
fun OrdersScreen() {
    val app = LocalAppState.current
    // One shared load model: loading / empty / error+retry. Previously this was
    // a bare LaunchedEffect with no try/catch, so a thrown network error would
    // kill the coroutine and leave the screen spinning forever.
    val orders = rememberLoad { ServiceLocator.orders.getOrders() }

    Box(Modifier.fillMaxSize().background(TazColors.Cream)) {
        Column(Modifier.fillMaxSize()) {
            TazTopBar("Your Orders", onBack = { app.back() })
            StateHost(
                handle = orders,
                modifier = Modifier.fillMaxSize(),
                loading = {
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator(color = TazColors.Green)
                    }
                },
                empty = { EmptyOrdersState() }
            ) { list ->
                LazyColumn(
                    contentPadding = PaddingValues(
                        start = TazSpace.gutter, end = TazSpace.gutter,
                        top = TazSpace.md, bottom = TazSpace.cartBarClearance
                    ),
                    verticalArrangement = Arrangement.spacedBy(TazSpace.md)
                ) {
                    items(list, key = { it.id }) { order -> OrderCard(order) }
                }
            }
        }
        CartBar()
    }
}

@Composable
private fun EmptyOrdersState() {
    val app = LocalAppState.current
    Column(
        Modifier.fillMaxSize().padding(TazSpace.xxxl),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Box(
            Modifier.size(96.dp).clip(CircleShape).background(TazColors.SurfaceSunken),
            contentAlignment = Alignment.Center
        ) {
            TazIcon(TazIcons.Bag, null, size = TazSize.iconLg, tint = TazColors.TextSecondary)
        }
        Spacer(Modifier.height(TazSpace.lg))
        Text(
            "No orders yet", fontSize = TazType.h2Size, fontWeight = TazType.h2Weight,
            lineHeight = TazType.h2Line, color = TazColors.TextPrimary
        )
        Spacer(Modifier.height(TazSpace.xs))
        Text(
            "Everything you order lands here for easy re-ordering.",
            fontSize = TazType.bodySize, lineHeight = TazType.bodyLine,
            color = TazColors.TextSecondary, textAlign = TextAlign.Center
        )
        Spacer(Modifier.height(TazSpace.xl))
        PillButton("Start Shopping", onClick = { app.goHome() })
    }
}

// ---------------------------------------------------------------------------
// Shared order chrome — the Orders screen and the Order Again tab render the
// same object, so they render it the same way.
// ---------------------------------------------------------------------------

/**
 * Status pill. Delivered reads as success, in-transit as a warning-toned
 * "still moving", everything earlier as neutral brand green.
 */
@Composable
internal fun OrderStatusChip(status: OrderStatus) {
    val bg = when (status) {
        OrderStatus.DELIVERED -> TazColors.SuccessSoft
        OrderStatus.ON_THE_WAY -> TazColors.WarningSoft
        OrderStatus.PLACED, OrderStatus.PACKED -> TazColors.GreenSoft
    }
    val fg = when (status) {
        OrderStatus.DELIVERED -> TazColors.Success
        OrderStatus.ON_THE_WAY -> TazColors.Warning
        OrderStatus.PLACED, OrderStatus.PACKED -> TazColors.GreenMid
    }
    Box(
        Modifier.clip(TazRadius.pill).background(bg)
            .padding(horizontal = TazSpace.sm, vertical = TazSpace.xs)
    ) {
        Text(
            orderStatusLabel(status), fontSize = TazType.microSize,
            lineHeight = TazType.microLine, fontWeight = FontWeight.Bold, color = fg
        )
    }
}

/** Four dots joined by rails, filled up to the order's current status. */
@Composable
internal fun OrderProgressRow(status: OrderStatus) {
    Column(Modifier.fillMaxWidth()) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            stepLabels.forEachIndexed { i, _ ->
                val reached = i <= status.ordinal
                Box(
                    Modifier.size(10.dp).clip(CircleShape)
                        .background(if (reached) TazColors.Green else TazColors.CardBorder)
                )
                if (i < stepLabels.lastIndex) {
                    Box(
                        Modifier.weight(1f).height(2.dp).background(
                            if (i < status.ordinal) TazColors.Green else TazColors.CardBorder
                        )
                    )
                }
            }
        }
        Spacer(Modifier.height(TazSpace.xs))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            stepLabels.forEachIndexed { i, label ->
                Text(
                    label, fontSize = TazType.microSize, lineHeight = TazType.microLine,
                    fontWeight = if (i == status.ordinal) FontWeight.Bold else TazType.microWeight,
                    color = if (i <= status.ordinal) TazColors.Green else TazColors.TextTertiary
                )
            }
        }
    }
}

/**
 * Outlined "Reorder" control. The add-to-cart loop, the REORDER analytics and
 * the landing screen are identical wherever it appears.
 */
@Composable
internal fun ReorderPill(order: Order, modifier: Modifier = Modifier) {
    val app = LocalAppState.current
    Box(
        modifier
            .defaultMinSize(minHeight = TazSize.touchTarget)
            .clip(TazRadius.pill)
            .background(TazColors.Surface)
            .border(BorderStroke(1.5.dp, TazColors.Green), TazRadius.pill)
            .tazPressable(
                pressScale = TazPress.compact,
                haptic = TazHaptic.Add,     // items really do enter the cart
                onClick = {
                order.lines.forEach { line ->
                    repeat(line.quantity) { app.addToCart(line.product) }
                        .also {
                            Analytics.track(
                                AnalyticsEvents.REORDER, mapOf("order_id" to order.id)
                            )
                        }
                }
                    // Land in the cart so the customer can review before checkout.
                    app.navigate(Screen.Cart)
                }
            )
            .padding(horizontal = TazSpace.lg),
        contentAlignment = Alignment.Center
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            TazIcon(TazIcons.OrderAgain, null, size = TazSize.iconXs, tint = TazColors.Green)
            Spacer(Modifier.width(TazSpace.xs))
            Text(
                "Reorder", fontSize = TazType.buttonSize, fontWeight = TazType.buttonWeight,
                color = TazColors.Green, maxLines = 1
            )
        }
    }
}

/** Card chrome shared by both order surfaces. */
@Composable
internal fun OrderCardHeader(order: Order) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        TazIcon(TazIcons.Receipt, null, size = TazSize.iconSm, tint = TazColors.Green)
        Spacer(Modifier.width(TazSpace.sm))
        Text(
            "#${order.id}", fontSize = TazType.titleSize, fontWeight = TazType.titleWeight,
            lineHeight = TazType.titleLine, color = TazColors.TextPrimary,
            modifier = Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis
        )
        OrderStatusChip(order.status)
    }
}

@Composable
private fun OrderCard(order: Order) {
    val app = LocalAppState.current
    Column(
        Modifier.fillMaxWidth()
            .clip(TazRadius.card)
            .background(TazColors.Surface)
            .border(BorderStroke(1.dp, TazColors.CardBorder), TazRadius.card)
            .padding(TazSpace.lg)
    ) {
        OrderCardHeader(order)
        Spacer(Modifier.height(TazSpace.xxs))
        Text(
            order.placedAtLabel, fontSize = TazType.captionSize,
            lineHeight = TazType.captionLine, color = TazColors.TextTertiary
        )

        Spacer(Modifier.height(TazSpace.lg))
        OrderProgressRow(order.status)

        Spacer(Modifier.height(TazSpace.md))
        Text(
            itemsSummary(order), fontSize = TazType.bodySize, lineHeight = TazType.bodyLine,
            color = TazColors.TextSecondary, maxLines = 1, overflow = TextOverflow.Ellipsis
        )

        Spacer(Modifier.height(TazSpace.md))
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text(
                "₹${order.bill.grandTotal}", fontSize = TazType.priceSize,
                fontWeight = TazType.priceWeight, color = TazColors.TextPrimary,
                modifier = Modifier.weight(1f)
            )
            ReorderPill(order)
            Spacer(Modifier.width(TazSpace.xs))
            Box(
                Modifier
                    .defaultMinSize(minHeight = TazSize.touchTarget)
                    .clip(TazRadius.pill)
                    .tazPressable(onClick = { app.navigate(Screen.Help) }, pressScale = TazPress.compact)
                    .padding(horizontal = TazSpace.md),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    "Get help", fontSize = TazType.captionSize,
                    lineHeight = TazType.captionLine, fontWeight = FontWeight.SemiBold,
                    color = TazColors.TextSecondary, maxLines = 1
                )
            }
        }
    }
}
