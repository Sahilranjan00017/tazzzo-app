package com.tazzzo.app.ui.home

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.tazzzo.app.LocalAppState
import com.tazzzo.app.Screen
import com.tazzzo.app.ui.interaction.TazPress
import com.tazzzo.app.ui.interaction.tazPressable
import com.tazzzo.app.config.MembershipConfig
import com.tazzzo.app.ui.interaction.TazHaptic
import androidx.compose.foundation.layout.width
import com.tazzzo.app.theme.TazColors
import com.tazzzo.app.theme.TazIcons
import com.tazzzo.app.theme.TazRadius
import com.tazzzo.app.theme.TazSize
import com.tazzzo.app.theme.TazSpace
import com.tazzzo.app.theme.TazType
import com.tazzzo.app.ui.common.EmojiBox
import com.tazzzo.app.ui.common.PillButton
import com.tazzzo.app.ui.common.QuantityStepper
import com.tazzzo.app.ui.common.TazIcon
import com.tazzzo.app.ui.common.TazTopBar
import com.tazzzo.app.config.AppConfig
import com.tazzzo.app.config.DeliveryCopy

/** Product art well in a cart row. The emoji is CONTENT; the well is design. */
private val RowImageWell = 56.dp
private val RowEmojiSize = 26.sp

@Composable
fun CartScreen() {
    val app = LocalAppState.current

    val lines = app.cartLines()
    val bill = app.bill(lines)

    var showAddressDialog by remember { mutableStateOf(false) }

    Column(Modifier.fillMaxSize().background(TazColors.Cream)) {
        TazTopBar("My Cart", onBack = { app.back() })

        if (lines.isEmpty()) {
            // ----- Empty state: designed, not a fallback -----
            Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                Column(
                    Modifier.padding(horizontal = TazSpace.xxl),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Box(
                        Modifier.size(96.dp).clip(CircleShape)
                            .background(TazColors.SurfaceSunken),
                        contentAlignment = Alignment.Center
                    ) {
                        TazIcon(
                            TazIcons.Bag, null,
                            size = TazSize.iconLg, tint = TazColors.TextSecondary
                        )
                    }
                    Spacer(Modifier.height(TazSpace.lg))
                    Text(
                        "Your cart is empty",
                        fontSize = TazType.h2Size, fontWeight = TazType.h2Weight,
                        lineHeight = TazType.h2Line, color = TazColors.TextPrimary
                    )
                    Spacer(Modifier.height(TazSpace.xs))
                    Text(
                        "Add fresh picks from any aisle and they'll show up here.",
                        fontSize = TazType.bodySize, lineHeight = TazType.bodyLine,
                        color = TazColors.TextSecondary,
                        textAlign = androidx.compose.ui.text.style.TextAlign.Center
                    )
                    Spacer(Modifier.height(TazSpace.xl))
                    PillButton(
                        "Start shopping",
                        onClick = { app.resetTo(Screen.Home) },
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }
        } else {
            Column(
                Modifier.weight(1f).verticalScroll(rememberScrollState())
                    .padding(horizontal = TazSpace.gutter)
            ) {
                Spacer(Modifier.height(TazSpace.md))

                // ----- Delivery card -----
                InfoStripCard(TazIcons.Delivery) {
                    Text(
                        DeliveryCopy.headline(AppConfig.deliveryPromise),
                        fontSize = TazType.bodySize, fontWeight = FontWeight.SemiBold,
                        color = TazColors.TextPrimary, maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    // Total units (same figure the cart bar shows), not line count.
                    Text(
                        "Shipment of ${app.cartItemCount} item${if (app.cartItemCount > 1) "s" else ""}",
                        fontSize = TazType.captionSize, color = TazColors.TextSecondary,
                        maxLines = 1, overflow = TextOverflow.Ellipsis
                    )
                }

                Spacer(Modifier.height(TazSpace.md))

                // ----- Items card -----
                CartCard {
                    lines.forEachIndexed { index, line ->
                        Row(
                            Modifier.fillMaxWidth().padding(TazSpace.md),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            EmojiBox(
                                line.product.emoji, RowEmojiSize, TazColors.SurfaceSunken,
                                Modifier.size(RowImageWell)
                            )
                            Spacer(Modifier.width(TazSpace.md))
                            Column(Modifier.weight(1f)) {
                                Text(
                                    line.product.name,
                                    fontSize = TazType.productNameSize,
                                    fontWeight = TazType.productNameWeight,
                                    lineHeight = TazType.productNameLine,
                                    color = TazColors.TextPrimary,
                                    maxLines = 2, overflow = TextOverflow.Ellipsis
                                )
                                Text(
                                    line.product.unit,
                                    fontSize = TazType.unitSize, color = TazColors.TextTertiary,
                                    maxLines = 1, overflow = TextOverflow.Ellipsis
                                )
                                Spacer(Modifier.height(TazSpace.xxs))
                                Row(verticalAlignment = Alignment.Bottom) {
                                    Text(
                                        "₹${line.lineTotal}",
                                        fontSize = TazType.priceSize,
                                        fontWeight = TazType.priceWeight,
                                        color = TazColors.TextPrimary, maxLines = 1
                                    )
                                    if (line.lineMrp > line.lineTotal) {
                                        Spacer(Modifier.width(TazSpace.xs + TazSpace.xxs))
                                        Text(
                                            "₹${line.lineMrp}",
                                            fontSize = TazType.mrpSize,
                                            color = TazColors.TextTertiary,
                                            textDecoration = TextDecoration.LineThrough,
                                            maxLines = 1, overflow = TextOverflow.Ellipsis
                                        )
                                    }
                                }
                            }
                            Spacer(Modifier.width(TazSpace.md))
                            QuantityStepper(
                                line.product,
                                Modifier.width(TazSize.stepperInlineWidth)
                            )
                        }
                        if (index < lines.lastIndex) {
                            HorizontalDivider(
                                Modifier.padding(horizontal = TazSpace.md),
                                color = TazColors.CardBorder
                            )
                        }
                    }
                }

                // ----- Tazzzo Club: context-aware, never nagging -----
                ClubCartPrompt()

                // ----- Savings strip -----
                if (bill.saved > 0) {
                    Spacer(Modifier.height(TazSpace.md))
                    Row(
                        Modifier.fillMaxWidth().clip(TazRadius.card)
                            .background(TazColors.SuccessSoft)
                            .padding(horizontal = TazSpace.md, vertical = TazSpace.md),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        TazIcon(
                            TazIcons.Offer, null,
                            size = TazSize.iconSm, tint = TazColors.Success
                        )
                        Spacer(Modifier.width(TazSpace.sm))
                        Text(
                            "You save ₹${bill.saved} on this order",
                            fontSize = TazType.savingsSize, fontWeight = TazType.savingsWeight,
                            color = TazColors.Success, maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }

                Spacer(Modifier.height(TazSpace.md))

                // ----- Bill details card -----
                CartCard {
                    Column(Modifier.fillMaxWidth().padding(TazSpace.lg)) {
                        Text(
                            "Bill details",
                            fontSize = TazType.titleSize, fontWeight = TazType.titleWeight,
                            color = TazColors.TextPrimary
                        )
                        Spacer(Modifier.height(TazSpace.md))

                        // Item total
                        BillRow(label = "Item total") {
                            if (bill.itemMrpTotal > bill.itemTotal) {
                                Text(
                                    "₹${bill.itemMrpTotal}",
                                    fontSize = TazType.mrpSize, color = TazColors.TextTertiary,
                                    textDecoration = TextDecoration.LineThrough, maxLines = 1
                                )
                                Spacer(Modifier.width(TazSpace.sm))
                            }
                            BillValue("₹${bill.itemTotal}")
                        }
                        Spacer(Modifier.height(TazSpace.md))

                        // Delivery fee
                        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                            Column(Modifier.weight(1f)) {
                                Text(
                                    "Delivery fee", fontSize = TazType.bodySize,
                                    color = TazColors.TextSecondary
                                )
                                if (bill.deliveryFee > 0) {
                                    Text(
                                        "Free above ₹${AppConfig.charges.freeDeliveryAboveRupees}",
                                        fontSize = TazType.captionSize,
                                        color = TazColors.TextTertiary
                                    )
                                }
                            }
                            if (bill.deliveryFee == 0) {
                                Text(
                                    "FREE", fontSize = TazType.bodySize,
                                    fontWeight = FontWeight.Bold, color = TazColors.Success
                                )
                            } else {
                                BillValue("₹${bill.deliveryFee}")
                            }
                        }
                        Spacer(Modifier.height(TazSpace.md))

                        // Handling charge
                        BillRow(label = "Handling charge") {
                            BillValue("₹${bill.handlingCharge}")
                        }
                        Spacer(Modifier.height(TazSpace.md))

                        // Club savings — only ever shown when a real discount
                        // was applied to THIS bill. Never an estimate, never a
                        // "what you could have saved" figure on a real bill.
                        if (bill.clubDiscount > 0) {
                            BillRow(label = "Tazzzo Club savings") {
                                Text(
                                    "−₹${bill.clubDiscount}",
                                    fontSize = TazType.bodySize,
                                    fontWeight = FontWeight.SemiBold,
                                    color = TazColors.Green
                                )
                            }
                            Spacer(Modifier.height(TazSpace.md))
                        }

                        // Coins earned
                        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                            TazIcon(
                                TazIcons.Coin, null,
                                size = TazSize.iconXs, tint = TazColors.CoinInk
                            )
                            Spacer(Modifier.width(TazSpace.xs))
                            Text(
                                "Coins you'll earn", fontSize = TazType.bodySize,
                                color = TazColors.TextSecondary, modifier = Modifier.weight(1f)
                            )
                            Text(
                                "+${bill.coinsEarned}", fontSize = TazType.bodySize,
                                fontWeight = FontWeight.Bold, color = TazColors.Success
                            )
                        }

                        Spacer(Modifier.height(TazSpace.md))
                        HorizontalDivider(color = TazColors.CardBorder)
                        Spacer(Modifier.height(TazSpace.md))

                        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                "Grand total", fontSize = TazType.titleSize,
                                fontWeight = FontWeight.Bold, color = TazColors.TextPrimary,
                                modifier = Modifier.weight(1f)
                            )
                            Text(
                                "₹${bill.grandTotal}", fontSize = TazType.titleSize,
                                fontWeight = FontWeight.Bold, color = TazColors.TextPrimary
                            )
                        }
                    }
                }

                Spacer(Modifier.height(TazSpace.md))

                // ----- Address card -----
                InfoStripCard(
                    TazIcons.Location,
                    trailing = {
                        Box(
                            Modifier.clip(TazRadius.pill)
                                .tazPressable(onClick = { showAddressDialog = true }, pressScale = TazPress.compact)
                                .defaultMinSize(minHeight = TazSize.touchTarget)
                                .padding(horizontal = TazSpace.sm),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                "Change", fontSize = TazType.bodySize,
                                fontWeight = FontWeight.SemiBold, color = TazColors.Green,
                                maxLines = 1
                            )
                        }
                    }
                ) {
                    Text(
                        "Delivering to", fontSize = TazType.captionSize,
                        color = TazColors.TextSecondary
                    )
                    Spacer(Modifier.height(TazSpace.xxs))
                    Text(
                        app.user.address,
                        fontSize = TazType.bodySize, fontWeight = FontWeight.SemiBold,
                        color = TazColors.TextPrimary,
                        maxLines = 2, overflow = TextOverflow.Ellipsis
                    )
                }

                Spacer(Modifier.height(TazSpace.lg))
            }

            // ----- Sticky checkout bar (placement lives in the checkout flow) -----
            Column(
                Modifier.fillMaxWidth()
                    .shadow(12.dp, spotColor = Color.Black.copy(alpha = 0.30f))
                    .background(TazColors.Surface)
            ) {
                Box(Modifier.fillMaxWidth().height(1.dp).background(TazColors.CardBorder))
                Column(
                    Modifier.fillMaxWidth().navigationBarsPadding().padding(TazSpace.gutter)
                ) {
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            "To pay", fontSize = TazType.captionSize,
                            color = TazColors.TextSecondary
                        )
                        Spacer(Modifier.weight(1f))
                        Text(
                            "₹${bill.grandTotal}", fontSize = TazType.titleSize,
                            fontWeight = FontWeight.Bold, color = TazColors.TextPrimary
                        )
                    }
                    Spacer(Modifier.height(TazSpace.md))
                    PillButton(
                        text = "Proceed to checkout",
                        onClick = {
                            app.checkout = null
                            app.navigate(Screen.Checkout)
                        },
                        modifier = Modifier.fillMaxWidth().heightIn(min = TazSize.buttonHeight)
                    )
                }
            }
        }
    }

    if (showAddressDialog) {
        AlertDialog(
            onDismissRequest = { showAddressDialog = false },
            containerColor = TazColors.Surface,
            shape = TazRadius.card,
            title = {
                Text(
                    "Address book",
                    fontSize = TazType.titleSize, fontWeight = FontWeight.Bold,
                    color = TazColors.TextPrimary
                )
            },
            text = {
                Text(
                    "Address book coming soon", fontSize = TazType.bodySize,
                    lineHeight = TazType.bodyLine, color = TazColors.TextSecondary
                )
            },
            confirmButton = {
                TextButton(onClick = { showAddressDialog = false }) {
                    Text(
                        "OK", fontSize = TazType.buttonSize,
                        fontWeight = TazType.buttonWeight, color = TazColors.Green
                    )
                }
            }
        )
    }
}

// ---------------------------------------------------------------------------
// Cart building blocks — one card silhouette for every group on the screen.
// ---------------------------------------------------------------------------

@Composable
private fun CartCard(content: @Composable ColumnScope.() -> Unit) {
    Column(
        Modifier.fillMaxWidth()
            .clip(TazRadius.card)
            .background(TazColors.Surface)
            .border(BorderStroke(1.dp, TazColors.CardBorder), TazRadius.card),
        content = content
    )
}

/** Icon + copy strip (delivery, address) with an optional trailing action. */
@Composable
private fun InfoStripCard(
    icon: ImageVector,
    trailing: (@Composable () -> Unit)? = null,
    content: @Composable ColumnScope.() -> Unit
) {
    CartCard {
        Row(
            Modifier.fillMaxWidth().padding(TazSpace.lg),
            verticalAlignment = Alignment.CenterVertically
        ) {
            TazIcon(icon, null, size = TazSize.iconSm, tint = TazColors.Green)
            Spacer(Modifier.width(TazSpace.md))
            Column(Modifier.weight(1f), content = content)
            if (trailing != null) {
                Spacer(Modifier.width(TazSpace.sm))
                trailing()
            }
        }
    }
}

@Composable
private fun BillRow(label: String, value: @Composable RowScope.() -> Unit) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(
            label, fontSize = TazType.bodySize, color = TazColors.TextSecondary,
            modifier = Modifier.weight(1f)
        )
        value()
    }
}

@Composable
private fun BillValue(text: String) {
    Text(
        text, fontSize = TazType.bodySize, fontWeight = FontWeight.SemiBold,
        color = TazColors.TextPrimary, maxLines = 1
    )
}

/**
 * The one Club surface in the cart.
 *
 * Four different customers get four different messages, because a single
 * generic "Join Club" banner is noise to three of them:
 *  - member, discount applied  → confirm the saving happened;
 *  - member, cart short        → how much more unlocks it;
 *  - non-member, cart qualifies→ the exact rupees this basket would save;
 *  - non-member, cart short    → the offer, stated honestly, no fake number.
 *
 * It never blocks checkout. Membership is an offer, not a toll gate.
 */
@Composable
private fun ClubCartPrompt() {
    val app = LocalAppState.current
    val eligibility = app.clubEligibility()
    val plan = MembershipConfig.plan

    // Nothing useful to say about an empty cart.
    if (app.cartItemCount == 0) return

    val (title, body) = when {
        eligibility.isMember && eligibility.isEligible ->
            "Club savings applied" to "₹${eligibility.discountRupees} off this order."
        eligibility.isMember ->
            "Add ₹${eligibility.amountToUnlockRupees} more to unlock Club savings" to
                "${plan.discountRule.percent}% off eligible orders ₹${plan.discountRule.minOrderValueRupees}+."
        !eligibility.isMember && eligibility.amountToUnlockRupees == 0 -> {
            val wouldSave = plan.discountRule.discountFor(app.bill(app.cartLines()).itemTotal)
            "Join Club — save ₹$wouldSave on this order" to
                "₹${plan.priceRupees} membership. ${plan.discountRule.percent}% off eligible orders."
        }
        else ->
            "Join Tazzzo Club" to
                "${plan.discountRule.percent}% off eligible orders ₹${plan.discountRule.minOrderValueRupees}+, plus rewards."
    }

    val isMemberBenefit = eligibility.isMember && eligibility.isEligible

    Row(
        Modifier.fillMaxWidth()
            .padding(horizontal = TazSpace.gutter)
            .clip(TazRadius.card)
            .background(if (isMemberBenefit) TazColors.GreenSoft else TazColors.Surface)
            .border(
                BorderStroke(1.dp, if (isMemberBenefit) TazColors.Green else TazColors.CardBorder),
                TazRadius.card
            )
            .then(
                if (eligibility.isMember) Modifier
                else Modifier.tazPressable(
                    onClick = { app.navigate(Screen.Club) },
                    pressScale = TazPress.row,
                    shape = TazRadius.card,
                    haptic = TazHaptic.Tap
                )
            )
            .padding(TazSpace.lg),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(Modifier.weight(1f)) {
            Text(
                title, fontSize = TazType.bodySize, fontWeight = FontWeight.SemiBold,
                lineHeight = TazType.bodyLine, color = TazColors.TextPrimary
            )
            Spacer(Modifier.height(TazSpace.xxs))
            Text(
                body, fontSize = TazType.captionSize, lineHeight = TazType.captionLine,
                color = TazColors.TextSecondary
            )
        }
        if (!eligibility.isMember) {
            Spacer(Modifier.width(TazSpace.sm))
            Text(
                "See Club", fontSize = TazType.captionSize,
                fontWeight = FontWeight.SemiBold, color = TazColors.Green
            )
        }
    }
    Spacer(Modifier.height(TazSpace.md))
}
