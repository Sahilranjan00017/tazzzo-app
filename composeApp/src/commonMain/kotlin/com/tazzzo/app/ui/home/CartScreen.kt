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
                            QuantityStepper(line.product)
                        }
                        if (index < lines.lastIndex) {
                            HorizontalDivider(
                                Modifier.padding(horizontal = TazSpace.md),
                                color = TazColors.CardBorder
                            )
                        }
                    }
                }

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
                                .clickable { showAddressDialog = true }
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
