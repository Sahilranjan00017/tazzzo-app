package com.tazzzo.app.ui.home

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.tazzzo.app.LocalAppState
import com.tazzzo.app.Screen
import com.tazzzo.app.data.model.Order
import com.tazzzo.app.data.model.OrderStatus
import com.tazzzo.app.data.model.PaymentMethodKind
import com.tazzzo.app.data.repository.ServiceLocator
import com.tazzzo.app.theme.TazColors
import com.tazzzo.app.theme.TazIcons
import com.tazzzo.app.theme.TazRadius
import com.tazzzo.app.theme.TazSize
import com.tazzzo.app.theme.TazSpace
import com.tazzzo.app.theme.TazType
import com.tazzzo.app.ui.common.PillButton
import com.tazzzo.app.ui.common.ProductImage
import com.tazzzo.app.ui.common.StateHost
import com.tazzzo.app.ui.common.TazIcon
import com.tazzzo.app.ui.common.TazTopBar
import com.tazzzo.app.ui.interaction.TazHaptic
import com.tazzzo.app.ui.state.rememberLoad

/**
 * The order as a permanent receipt and a service centre.
 *
 * Calm by design: status, when it is coming, where, what, what it cost and
 * what was saved, how it was paid — then help and reorder. Every rupee here
 * is the same rupee the customer saw at checkout; nothing is recomputed.
 * "Need help" is ORDER-SCOPED: the app already knows the order id, so the
 * customer is never asked to type it.
 */
@Composable
fun OrderDetailScreen(orderId: String) {
    val app = LocalAppState.current
    val load = rememberLoad<Order?>(orderId, isEmpty = { it == null }) {
        ServiceLocator.orders.getOrders().firstOrNull { it.id == orderId }
    }
    Box(Modifier.fillMaxSize().background(TazColors.Cream)) {
        Column(Modifier.fillMaxSize()) {
            TazTopBar("Order #$orderId", onBack = { app.back() })
            StateHost(load, modifier = Modifier.fillMaxSize()) { order ->
                if (order == null) return@StateHost
                Column(
                    Modifier.fillMaxSize().verticalScroll(rememberScrollState())
                        .padding(horizontal = TazSpace.gutter)
                        .padding(bottom = TazSpace.cartBarClearance)
                ) {
                    Spacer(Modifier.height(TazSpace.md))
                    StatusCard(order)
                    Spacer(Modifier.height(TazSpace.md))
                    ItemsCard(order)
                    Spacer(Modifier.height(TazSpace.md))
                    BillCard(order)
                    Spacer(Modifier.height(TazSpace.md))
                    DetailsCard(order)
                    Spacer(Modifier.height(TazSpace.lg))
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(TazSpace.sm)) {
                        PillButton(
                            "Need help", onClick = { app.helpOrderId = order.id; app.navigate(Screen.Help) },
                            modifier = Modifier.weight(1f), filled = false, haptic = TazHaptic.Tap
                        )
                        PillButton(
                            "Order again",
                            onClick = {
                                // Current catalogue price and stock win — addToCart enforces both.
                                order.lines.forEach { l -> repeat(l.quantity) { app.addToCart(l.product) } }
                                app.navigate(Screen.Cart)
                            },
                            modifier = Modifier.weight(1f), haptic = TazHaptic.Add
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun Card(content: @Composable () -> Unit) {
    Column(
        Modifier.fillMaxWidth().clip(TazRadius.card).background(TazColors.Surface)
            .border(BorderStroke(1.dp, TazColors.CardBorder), TazRadius.card).padding(TazSpace.lg)
    ) { content() }
}

@Composable
private fun StatusCard(order: Order) = Card {
    val (label, tint) = when (order.status) {
        OrderStatus.PLACED -> "Order placed" to TazColors.TextPrimary
        OrderStatus.PACKED -> "Packed" to TazColors.TextPrimary
        OrderStatus.ON_THE_WAY -> "On the way" to TazColors.Warning
        OrderStatus.DELIVERED -> "Delivered" to TazColors.Success
    }
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(
            Modifier.size(TazSize.buttonHeightSm).clip(CircleShape)
                .background(if (order.status == OrderStatus.DELIVERED) TazColors.SuccessSoft else TazColors.SurfaceSunken),
            contentAlignment = Alignment.Center
        ) { TazIcon(TazIcons.Success, null, size = TazSize.iconSm, tint = tint) }
        Spacer(Modifier.width(TazSpace.md))
        Column(Modifier.weight(1f)) {
            Text(label, fontSize = TazType.titleSize, fontWeight = TazType.titleWeight, color = tint)
            Text(order.placedAtLabel, fontSize = TazType.captionSize, color = TazColors.TextSecondary)
        }
    }
    order.slot?.let { s ->
        Spacer(Modifier.height(TazSpace.md))
        HorizontalDivider(color = TazColors.CardBorder)
        Spacer(Modifier.height(TazSpace.md))
        KeyValue("Delivery slot", s.label + (if (s.fee.isZero) " · Free" else " · ${s.fee}"))
    }
}

@Composable
private fun ItemsCard(order: Order) = Card {
    val units = order.lines.sumOf { it.quantity }
    Text("$units item${if (units == 1) "" else "s"}", fontSize = TazType.titleSize, fontWeight = TazType.titleWeight, color = TazColors.TextPrimary)
    Spacer(Modifier.height(TazSpace.md))
    order.lines.forEachIndexed { i, line ->
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            ProductImage(line.product, Modifier.size(48.dp).clip(TazRadius.chip), aspectRatio = 1f, glyphSize = androidx.compose.ui.unit.TextUnit.Unspecified)
            Spacer(Modifier.width(TazSpace.md))
            Column(Modifier.weight(1f)) {
                Text(line.product.name, fontSize = TazType.bodySize, fontWeight = FontWeight.Medium, color = TazColors.TextPrimary, maxLines = 2, overflow = TextOverflow.Ellipsis)
                Text("${line.product.unit} · ${line.quantity} unit${if (line.quantity == 1) "" else "s"}", fontSize = TazType.captionSize, color = TazColors.TextTertiary)
            }
            Column(horizontalAlignment = Alignment.End) {
                Text("${line.lineTotal}", fontSize = TazType.priceSize, fontWeight = TazType.priceWeight, color = TazColors.TextPrimary)
                if (line.lineMrp > line.lineTotal) Text("${line.lineMrp}", fontSize = TazType.mrpSize, color = TazColors.TextTertiary, textDecoration = TextDecoration.LineThrough)
            }
        }
        if (i < order.lines.lastIndex) { Spacer(Modifier.height(TazSpace.sm)); HorizontalDivider(color = TazColors.CardBorder); Spacer(Modifier.height(TazSpace.sm)) }
    }
}

@Composable
private fun BillCard(order: Order) = Card {
    val b = order.bill
    Text("Bill summary", fontSize = TazType.titleSize, fontWeight = TazType.titleWeight, color = TazColors.TextPrimary)
    Spacer(Modifier.height(TazSpace.md))
    Row(Modifier.fillMaxWidth()) {
        Text("Item total", fontSize = TazType.bodySize, color = TazColors.TextSecondary, modifier = Modifier.weight(1f))
        if (b.itemMrpTotal > b.itemTotal) { Text("${b.itemMrpTotal}", fontSize = TazType.mrpSize, color = TazColors.TextTertiary, textDecoration = TextDecoration.LineThrough); Spacer(Modifier.width(TazSpace.xs)) }
        Text("${b.itemTotal}", fontSize = TazType.bodySize, fontWeight = FontWeight.SemiBold, color = TazColors.TextPrimary)
    }
    b.appliedPromotions.filter { it.discount.isPositive }.forEach { KeyValue(it.title, "−${it.discount}", TazColors.Green) }
    if (b.clubDiscount.isPositive) KeyValue("Tazzzo Club savings", "−${b.clubDiscount}", TazColors.Green)
    Row(Modifier.fillMaxWidth().padding(top = TazSpace.sm)) {
        Text("Delivery fee", fontSize = TazType.bodySize, color = TazColors.TextSecondary, modifier = Modifier.weight(1f))
        if (b.deliveryFee.isZero && b.deliveryFeeWaived.isPositive) { Text("${b.deliveryFeeWaived}", fontSize = TazType.mrpSize, color = TazColors.TextTertiary, textDecoration = TextDecoration.LineThrough); Spacer(Modifier.width(TazSpace.xs)) }
        Text(if (b.deliveryFee.isZero) "FREE" else "${b.deliveryFee}", fontSize = TazType.bodySize, fontWeight = FontWeight.Bold, color = if (b.deliveryFee.isZero) TazColors.Success else TazColors.TextPrimary)
    }
    KeyValue("Handling charge", "${b.handlingCharge}")
    Spacer(Modifier.height(TazSpace.sm)); HorizontalDivider(color = TazColors.CardBorder); Spacer(Modifier.height(TazSpace.sm))
    Row(Modifier.fillMaxWidth()) {
        Text("Total paid", fontSize = TazType.titleSize, fontWeight = TazType.titleWeight, color = TazColors.TextPrimary, modifier = Modifier.weight(1f))
        Text("${b.grandTotal}", fontSize = TazType.titleSize, fontWeight = TazType.titleWeight, color = TazColors.TextPrimary)
    }
    if (b.realisedSavings.isPositive) KeyValue("You saved", "${b.realisedSavings}", TazColors.Green)
}

@Composable
private fun DetailsCard(order: Order) = Card {
    Text("Order details", fontSize = TazType.titleSize, fontWeight = TazType.titleWeight, color = TazColors.TextPrimary)
    Spacer(Modifier.height(TazSpace.md))
    KeyValue("Order ID", "#${order.id}")
    KeyValue("Delivery address", order.address)
    order.payment?.let { KeyValue("Payment", when (it) { PaymentMethodKind.COD -> "Cash on Delivery"; PaymentMethodKind.UPI -> "UPI"; PaymentMethodKind.CARD -> "Card" }) }
    if (order.instructionIds.isNotEmpty()) {
        KeyValue("Instructions", com.tazzzo.app.config.defaultDeliveryInstructions.filter { it.id in order.instructionIds }.joinToString(" · ") { it.label })
    }
}

@Composable
private fun KeyValue(label: String, value: String, valueColor: androidx.compose.ui.graphics.Color = TazColors.TextPrimary) {
    Row(Modifier.fillMaxWidth().padding(vertical = TazSpace.xs), verticalAlignment = Alignment.Top) {
        Text(label, fontSize = TazType.bodySize, color = TazColors.TextSecondary, modifier = Modifier.weight(1f))
        Spacer(Modifier.width(TazSpace.md))
        Text(value, fontSize = TazType.bodySize, fontWeight = FontWeight.SemiBold, color = valueColor, maxLines = 3, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1.4f), textAlign = androidx.compose.ui.text.style.TextAlign.End)
    }
}
