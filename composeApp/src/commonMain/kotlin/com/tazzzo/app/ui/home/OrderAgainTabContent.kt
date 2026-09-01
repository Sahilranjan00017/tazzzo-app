package com.tazzzo.app.ui.home

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
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
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.tazzzo.app.HomeTab
import com.tazzzo.app.LocalAppState
import com.tazzzo.app.data.model.Order
import com.tazzzo.app.data.model.Product
import com.tazzzo.app.data.repository.ServiceLocator
import com.tazzzo.app.theme.TazColors
import com.tazzzo.app.theme.TazIcons
import com.tazzzo.app.theme.TazRadius
import com.tazzzo.app.theme.TazSize
import com.tazzzo.app.theme.TazSpace
import com.tazzzo.app.theme.TazType
import com.tazzzo.app.ui.common.EmojiBox
import com.tazzzo.app.ui.common.PillButton
import com.tazzzo.app.ui.common.ProductRail
import com.tazzzo.app.ui.common.TazIcon

/** "Order Again" bottom tab — past orders with one-tap reorder. */
@Composable
fun OrderAgainTabContent() {
    var orders by remember { mutableStateOf<List<Order>>(emptyList()) }
    var bestsellers by remember { mutableStateOf<List<Product>>(emptyList()) }
    var loading by remember { mutableStateOf(true) }
    LaunchedEffect(Unit) {
        loading = true
        orders = ServiceLocator.orders.getOrders()
        bestsellers = ServiceLocator.catalog.getBestsellers()
        loading = false
    }

    Column(Modifier.fillMaxSize().background(TazColors.Cream)) {
        // Header
        Column(
            Modifier.fillMaxWidth().background(TazColors.Surface).statusBarsPadding()
                .padding(horizontal = TazSpace.gutter, vertical = TazSpace.md)
        ) {
            Text(
                "Order Again", fontSize = TazType.h2Size, fontWeight = TazType.h2Weight,
                lineHeight = TazType.h2Line, color = TazColors.TextPrimary
            )
            Text(
                "Your past orders, one tap away", fontSize = TazType.captionSize,
                lineHeight = TazType.captionLine, color = TazColors.TextSecondary
            )
        }
        Box(Modifier.fillMaxWidth().height(1.dp).background(TazColors.CardBorder))

        when {
            loading -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(color = TazColors.Green)
            }

            orders.isEmpty() -> EmptyOrderAgainState()

            else -> LazyColumn(
                Modifier.fillMaxSize(),
                contentPadding = PaddingValues(
                    top = TazSpace.md, bottom = TazSpace.cartBarClearance
                ),
                verticalArrangement = Arrangement.spacedBy(TazSpace.md)
            ) {
                items(orders, key = { it.id }) { order ->
                    OrderAgainCard(order, Modifier.padding(horizontal = TazSpace.gutter))
                }
                item { Spacer(Modifier.height(TazSpace.sm)) }
                item { ProductRail("Bestsellers you may need", bestsellers) }
            }
        }
    }
}

@Composable
private fun EmptyOrderAgainState() {
    val app = LocalAppState.current
    Column(
        Modifier.fillMaxSize().padding(horizontal = TazSpace.xxxl),
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
            "Everything you order lands here for easy re-ordering",
            fontSize = TazType.bodySize, lineHeight = TazType.bodyLine,
            color = TazColors.TextSecondary, textAlign = TextAlign.Center
        )
        Spacer(Modifier.height(TazSpace.xl))
        PillButton("Start Shopping", onClick = { app.homeTab = HomeTab.HOME })
    }
}

/**
 * Same card chrome as the Orders screen, but this surface exists to rebuild a
 * basket — so it keeps the full line list rather than a one-line summary.
 */
@Composable
private fun OrderAgainCard(order: Order, modifier: Modifier = Modifier) {
    Column(
        modifier.fillMaxWidth()
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

        // Lines — the product emoji is CONTENT, so it stays.
        order.lines.forEach { line ->
            Row(
                Modifier.fillMaxWidth().padding(vertical = TazSpace.xs),
                verticalAlignment = Alignment.CenterVertically
            ) {
                EmojiBox(
                    line.product.emoji, TazType.bodySize, TazColors.SurfaceSunken,
                    Modifier.size(36.dp)
                )
                Spacer(Modifier.width(TazSpace.md))
                Text(
                    line.product.name, fontSize = TazType.productNameSize,
                    lineHeight = TazType.productNameLine,
                    fontWeight = TazType.productNameWeight, color = TazColors.TextPrimary,
                    modifier = Modifier.weight(1f), maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    "x${line.quantity}", fontSize = TazType.captionSize,
                    lineHeight = TazType.captionLine, color = TazColors.TextTertiary
                )
                Spacer(Modifier.width(TazSpace.md))
                Text(
                    "₹${line.lineTotal}", fontSize = TazType.priceSize,
                    fontWeight = TazType.priceWeight, color = TazColors.TextPrimary
                )
            }
        }

        Spacer(Modifier.height(TazSpace.md))

        // Footer: total + reorder
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text(
                "₹${order.bill.grandTotal}", fontSize = TazType.priceSize,
                fontWeight = TazType.priceWeight, color = TazColors.TextPrimary,
                modifier = Modifier.weight(1f)
            )
            ReorderPill(order)
        }
    }
}
