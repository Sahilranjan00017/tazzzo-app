package com.tazzzo.app.ui.home

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.tazzzo.app.LocalAppState
import com.tazzzo.app.Screen
import com.tazzzo.app.ui.interaction.TazPress
import com.tazzzo.app.ui.interaction.tazPressable
import com.tazzzo.app.theme.TazColors
import com.tazzzo.app.theme.TazIcons
import com.tazzzo.app.theme.TazRadius
import com.tazzzo.app.theme.TazSize
import com.tazzzo.app.theme.TazSpace
import com.tazzzo.app.theme.TazType
import com.tazzzo.app.ui.common.PillButton
import com.tazzzo.app.ui.common.TazIcon

/** Celebration mark — large enough to own the moment, calm enough to be trusted. */
private val CelebrationCircle = 112.dp
private val CelebrationIcon = 56.dp

/**
 * Order confirmation.
 *
 * The emotional beat of the whole app, so it earns one piece of motion (a
 * single scale-in on the success mark) and then gets out of the way. Every
 * figure below the fold is read from the placed order — nothing is estimated,
 * promised or invented here.
 */
@Composable
fun OrderSuccessScreen(orderId: String) {
    val app = LocalAppState.current
    val scale = remember { Animatable(0.8f) }
    LaunchedEffect(Unit) {
        scale.animateTo(
            targetValue = 1f,
            animationSpec = tween(400, easing = FastOutSlowInEasing)
        )
    }

    val order = app.lastOrder

    Box(Modifier.fillMaxSize().background(TazColors.Cream).statusBarsPadding()) {
        Column(
            Modifier.fillMaxSize().verticalScroll(rememberScrollState())
                .navigationBarsPadding()
                .padding(horizontal = TazSpace.xxl, vertical = TazSpace.huge),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Box(
                Modifier.size(CelebrationCircle).scale(scale.value).clip(CircleShape)
                    .background(TazColors.GreenSoft),
                contentAlignment = Alignment.Center
            ) {
                TazIcon(
                    TazIcons.Success, null,
                    size = CelebrationIcon, tint = TazColors.Green
                )
            }

            Spacer(Modifier.height(TazSpace.xl))
            Text(
                "Order placed",
                fontSize = TazType.h1Size, fontWeight = TazType.h1Weight,
                lineHeight = TazType.h1Line, color = TazColors.Green,
                textAlign = TextAlign.Center
            )
            Spacer(Modifier.height(TazSpace.xs))
            Text(
                "Order #$orderId",
                fontSize = TazType.captionSize, color = TazColors.TextSecondary,
                textAlign = TextAlign.Center
            )

            Spacer(Modifier.height(TazSpace.xxl))

            // ----- Summary: everything the customer needs to feel certain -----
            if (order != null) {
                Column(
                    Modifier.fillMaxWidth().clip(TazRadius.card)
                        .background(TazColors.Surface)
                        .border(BorderStroke(1.dp, TazColors.CardBorder), TazRadius.card)
                        .padding(TazSpace.lg)
                ) {
                    Text(
                        "Amount paid",
                        fontSize = TazType.captionSize, color = TazColors.TextSecondary
                    )
                    Spacer(Modifier.height(TazSpace.xxs))
                    Text(
                        "₹${order.bill.grandTotal}",
                        fontSize = TazType.priceHeroSize, fontWeight = FontWeight.Bold,
                        color = TazColors.TextPrimary
                    )

                    Spacer(Modifier.height(TazSpace.md))
                    HorizontalDivider(color = TazColors.CardBorder)
                    Spacer(Modifier.height(TazSpace.md))

                    val units = order.lines.sumOf { it.quantity }
                    SummaryRow("Items", "$units item${if (units == 1) "" else "s"}")
                    Spacer(Modifier.height(TazSpace.md))
                    SummaryRow("Placed", order.placedAtLabel)
                    Spacer(Modifier.height(TazSpace.md))
                    SummaryRow("Delivering to", order.address)
                    order.slot?.let { s ->
                        Spacer(Modifier.height(TazSpace.sm))
                        SummaryRow("Delivery slot", s.label + (if (s.feeRupees == 0) " · Free" else " · ₹${s.feeRupees}"))
                    }
                    if (order.instructionIds.isNotEmpty()) {
                        Spacer(Modifier.height(TazSpace.sm))
                        SummaryRow(
                            "Instructions",
                            com.tazzzo.app.config.defaultDeliveryInstructions
                                .filter { it.id in order.instructionIds }.joinToString(" · ") { it.label }
                        )
                    }
                    if (order.bill.realisedSavings > 0) {
                        Spacer(Modifier.height(TazSpace.sm))
                        SummaryRow("You saved", "₹${order.bill.realisedSavings}")
                    }
                    // Rendered only when the order actually carries a method.
                    order.payment?.let { method ->
                        Spacer(Modifier.height(TazSpace.sm))
                        SummaryRow(
                            "Paid by",
                            when (method) {
                                com.tazzzo.app.data.model.PaymentMethodKind.COD -> "Cash on Delivery"
                                com.tazzzo.app.data.model.PaymentMethodKind.UPI -> "UPI"
                                com.tazzzo.app.data.model.PaymentMethodKind.CARD -> "Card"
                            }
                        )
                    }

                    if (order.bill.coinsEarned > 0) {
                        Spacer(Modifier.height(TazSpace.md))
                        HorizontalDivider(color = TazColors.CardBorder)
                        Spacer(Modifier.height(TazSpace.md))
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            TazIcon(
                                TazIcons.Coin, null,
                                size = TazSize.iconSm, tint = TazColors.CoinInk
                            )
                            Spacer(Modifier.width(TazSpace.sm))
                            Text(
                                "+${order.bill.coinsEarned} Tazzzo Coins earned",
                                fontSize = TazType.bodySize, fontWeight = FontWeight.SemiBold,
                                color = TazColors.Success, modifier = Modifier.weight(1f)
                            )
                        }
                    }
                }
                Spacer(Modifier.height(TazSpace.lg))
                // The retention moment: what this order did for the customer's
                // Club standing, and how far the next reward is. Members only —
                // nothing is shown to a guest that they did not earn.
                if (app.isClubMember) {
                    com.tazzzo.app.ui.club.ClubProgressCard(app.membership)
                    Spacer(Modifier.height(TazSpace.lg))
                }
                Spacer(Modifier.height(TazSpace.sm))
            }

            PillButton(
                text = "Track order",
                onClick = {
                    app.goHome()
                    app.navigate(Screen.OrderDetail(orderId))
                },
                modifier = Modifier.fillMaxWidth().heightIn(min = TazSize.buttonHeight)
            )
            Spacer(Modifier.height(TazSpace.md))
            Box(
                Modifier.fillMaxWidth().clip(TazRadius.pill)
                    .tazPressable(onClick = { app.goHome() }, pressScale = TazPress.compact)
                    .defaultMinSize(minHeight = 48.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    "Continue shopping",
                    fontSize = TazType.buttonSize, fontWeight = TazType.buttonWeight,
                    color = TazColors.Green, textAlign = TextAlign.Center
                )
            }
        }
    }
}

@Composable
private fun SummaryRow(label: String, value: String) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.Top) {
        Text(
            label, fontSize = TazType.bodySize, color = TazColors.TextSecondary,
            lineHeight = TazType.bodyLine
        )
        Spacer(Modifier.width(TazSpace.lg))
        Text(
            value, fontSize = TazType.bodySize, fontWeight = FontWeight.SemiBold,
            color = TazColors.TextPrimary, lineHeight = TazType.bodyLine,
            textAlign = TextAlign.End, modifier = Modifier.weight(1f),
            maxLines = 3, overflow = TextOverflow.Ellipsis
        )
    }
}
