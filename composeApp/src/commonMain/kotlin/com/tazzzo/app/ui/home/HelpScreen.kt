package com.tazzzo.app.ui.home

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.tazzzo.app.LocalAppState
import com.tazzzo.app.Screen
import com.tazzzo.app.config.BrandCopy
import com.tazzzo.app.data.model.FaqItem
import com.tazzzo.app.ui.interaction.TazPress
import com.tazzzo.app.ui.interaction.tazPressable
import com.tazzzo.app.theme.TazColors
import com.tazzzo.app.theme.TazIcons
import com.tazzzo.app.theme.TazMotion
import com.tazzzo.app.theme.TazRadius
import com.tazzzo.app.theme.TazSize
import com.tazzzo.app.theme.TazSpace
import com.tazzzo.app.theme.TazType
import com.tazzzo.app.ui.common.CartBar
import com.tazzzo.app.ui.common.EmptyState
import com.tazzzo.app.ui.state.rememberLoad
import com.tazzzo.app.ui.state.UiState
import com.tazzzo.app.ui.state.LoadHandle
import com.tazzzo.app.ui.common.SkeletonBlock
import com.tazzzo.app.ui.common.ErrorState
import com.tazzzo.app.data.repository.ServiceLocator
import com.tazzzo.app.ui.common.PillButton
import com.tazzzo.app.ui.common.SectionHeader
import com.tazzzo.app.ui.common.TazIcon
import com.tazzzo.app.ui.common.TazTopBar
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.widthIn
import com.tazzzo.app.data.model.Order
import com.tazzzo.app.data.model.OrderStatus
import com.tazzzo.app.ui.common.ChipTone
import com.tazzzo.app.ui.common.TazChip
import com.tazzzo.app.ui.common.ProductImage
import com.tazzzo.app.ui.common.TazRowDivider
import com.tazzzo.app.ui.common.TazListRow

/**
 * Help.
 *
 * Built around what the app can actually do — search the FAQ, jump to Orders,
 * open the WhatsApp line — instead of a decorative greeting block. Honest-copy
 * rule (spec §9): no reply times, no agent counts, no SLAs. There is no support
 * backend to measure, so nothing on this screen claims one.
 */
@Composable
fun HelpScreen() {
    val app = LocalAppState.current
    // Keyed by question text, not index: filtering reorders the list, and an
    // index-keyed expansion would open the wrong answer after a search.
    var expandedQuestion by remember { mutableStateOf<String?>(null) }
    var query by remember { mutableStateOf("") }
    var dialogText by remember { mutableStateOf<String?>(null) }

    val needle = query.trim().lowercase()
    // FAQ content now comes from SupportRepository, so it follows the backend
    // like every other customer-facing surface. Filtering stays on the client:
    // that is what this screen has always done and no search endpoint exists.
    val faqs = rememberLoad { ServiceLocator.support.getFaqs() }
    val filter: (List<FaqItem>) -> List<FaqItem> = { all ->
        if (needle.isEmpty()) all
        else all.filter {
            it.question.lowercase().contains(needle) || it.answer.lowercase().contains(needle)
        }
    }
    val searching = needle.isNotEmpty()
    val openWhatsApp = { dialogText = "Opening WhatsApp… (demo)" }

    Box(Modifier.fillMaxSize().background(TazColors.Cream)) {
        Column(Modifier.fillMaxSize()) {
            TazTopBar("Help", onBack = { app.back() })
            Column(
                Modifier.fillMaxSize().verticalScroll(rememberScrollState())
                    .padding(bottom = TazSpace.cartBarClearance)
            ) {
                // ---- header: a question and one honest line, no empty block --
                Column(
                    Modifier.fillMaxWidth()
                        .padding(horizontal = TazSpace.gutter)
                        .padding(top = TazSpace.xl, bottom = TazSpace.md)
                ) {
                    Text(
                        "How can we help?", fontSize = TazType.h1Size,
                        fontWeight = TazType.h1Weight, lineHeight = TazType.h1Line,
                        color = TazColors.TextPrimary
                    )
                    Spacer(Modifier.height(TazSpace.xs))
                    Text(
                        "Find an answer, or reach us on WhatsApp",
                        fontSize = TazType.bodySize, lineHeight = TazType.bodyLine,
                        color = TazColors.TextSecondary
                    )
                }

                HelpSearchField(query, { query = it })

                // While searching, the answers sit directly under the field —
                // results a screen away from the query are not search results.
                if (searching) {
                    Spacer(Modifier.height(TazSpace.lg))
                    FaqContent(faqs) { all ->
                        val results = filter(all)
                        if (results.isEmpty()) {
                            EmptyState(
                                emoji = "🔍",
                                title = "No help topics match",
                                body = "Try a different word, or message us on WhatsApp",
                                actionLabel = "Message us on WhatsApp",
                                onAction = openWhatsApp
                            )
                        } else {
                            SectionHeader("Matching topics")
                            FaqCard(results, expandedQuestion) { q ->
                                expandedQuestion = if (expandedQuestion == q) null else q
                            }
                        }
                    }
                }

                // ---- the order this screen is about --------------------------
                // Arriving from an order carries its id, so Help can open on the
                // order itself rather than asking a customer to describe the
                // thing they just tapped.
                app.helpOrderId?.let { OrderContextCard(it) }

                // ---- help with an order: real destinations ------------------
                Spacer(Modifier.height(TazSpace.lg))
                SectionHeader(app.helpOrderId?.let { "Help with order #$it" } ?: "Help with an order")
                Column(
                    Modifier.padding(horizontal = TazSpace.gutter).fillMaxWidth()
                        .clip(TazRadius.card).background(TazColors.Surface)
                        .border(BorderStroke(1.dp, TazColors.CardBorder), TazRadius.card)
                ) {
                    HelpListRow(
                        TazIcons.Receipt, "Track or manage an order",
                        "Open your orders"
                    ) { app.navigate(Screen.Orders) }
                    TazRowDivider()
                    HelpListRow(
                        TazIcons.Inventory, "Item damaged, expired or poor quality",
                        "Message us about the item"
                    ) { openWhatsApp() }
                    TazRowDivider()
                    HelpListRow(
                        TazIcons.Bag, "Order or item never arrived",
                        "Message us about a missing delivery"
                    ) { openWhatsApp() }
                    TazRowDivider()
                    HelpListRow(
                        TazIcons.Delivery, "Report a delivery partner",
                        "Tell us what happened"
                    ) { openWhatsApp() }
                    TazRowDivider()
                    HelpListRow(
                        TazIcons.Payment, "Payment, billing or refund",
                        "Message us about a payment"
                    ) { openWhatsApp() }
                }

                // ---- contact -------------------------------------------------
                Spacer(Modifier.height(TazSpace.lg))
                SectionHeader("Talk to us")
                Row(
                    Modifier.padding(horizontal = TazSpace.gutter).fillMaxWidth()
                        .clip(TazRadius.card).background(TazColors.Surface)
                        .border(BorderStroke(1.dp, TazColors.CardBorder), TazRadius.card)
                        .padding(TazSpace.lg),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        Modifier.size(TazSize.buttonHeightSm).clip(CircleShape)
                            .background(TazColors.GreenSoft),
                        contentAlignment = Alignment.Center
                    ) {
                        TazIcon(
                            TazIcons.Chat, null,
                            size = TazSize.iconSm, tint = TazColors.Green
                        )
                    }
                    Spacer(Modifier.width(TazSpace.md))
                    Column(Modifier.weight(1f)) {
                        Text(
                            "Chat with us on WhatsApp", fontSize = TazType.bodySize,
                            lineHeight = TazType.bodyLine, fontWeight = FontWeight.Medium,
                            color = TazColors.TextPrimary
                        )
                        Spacer(Modifier.height(TazSpace.xxs))
                        Text(
                            "Say \"HI\" to ${BrandCopy.whatsappNumber}",
                            fontSize = TazType.captionSize, lineHeight = TazType.captionLine,
                            color = TazColors.TextTertiary
                        )
                    }
                    Spacer(Modifier.width(TazSpace.sm))
                    Box(
                        Modifier
                            .defaultMinSize(minHeight = TazSize.touchTarget)
                            .clip(TazRadius.pill).background(TazColors.Green)
                            .tazPressable(onClick = { openWhatsApp() }, pressScale = TazPress.compact)
                            .padding(horizontal = TazSpace.lg),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            "Open chat", color = TazColors.White,
                            fontSize = TazType.buttonSize, fontWeight = TazType.buttonWeight,
                            maxLines = 1
                        )
                    }
                }

                Spacer(Modifier.height(TazSpace.md))
                PillButton(
                    text = "Request a callback",
                    onClick = { dialogText = "Callback requested (demo)" },
                    modifier = Modifier.padding(horizontal = TazSpace.gutter).fillMaxWidth(),
                    filled = false
                )

                // ---- the full FAQ, only when the list is not already filtered -
                if (!searching) {
                    Spacer(Modifier.height(TazSpace.lg))
                    SectionHeader("Frequently asked")
                    FaqContent(faqs) { all ->
                        FaqCard(all, expandedQuestion) { q ->
                            expandedQuestion = if (expandedQuestion == q) null else q
                        }
                    }
                }

                Spacer(Modifier.height(TazSpace.xxl))
            }
        }
        CartBar()
    }

    if (dialogText != null) {
        AlertDialog(
            onDismissRequest = { dialogText = null },
            containerColor = TazColors.Surface,
            shape = TazRadius.card,
            title = {
                Text(
                    "Tazzzo", fontSize = TazType.titleSize,
                    fontWeight = TazType.titleWeight, color = TazColors.Green
                )
            },
            text = {
                Text(
                    dialogText ?: "", fontSize = TazType.bodySize,
                    lineHeight = TazType.bodyLine, color = TazColors.TextPrimary
                )
            },
            confirmButton = {
                TextButton(onClick = { dialogText = null }) {
                    Text(
                        "OK", color = TazColors.Green, fontSize = TazType.buttonSize,
                        fontWeight = TazType.buttonWeight
                    )
                }
            }
        )
    }
}

// ---------------------------------------------------------------------------
// Pieces
// ---------------------------------------------------------------------------

/** Same field geometry and focus behaviour as the catalogue search. */
@Composable
private fun HelpSearchField(query: String, onQueryChange: (String) -> Unit) {
    var focused by remember { mutableStateOf(false) }
    Row(
        Modifier
            .padding(horizontal = TazSpace.gutter)
            .fillMaxWidth()
            .height(TazSize.inputHeight)
            .clip(TazRadius.card)
            .background(TazColors.Surface)
            .border(
                BorderStroke(
                    if (focused) 1.5.dp else 1.dp,
                    if (focused) TazColors.Green else TazColors.BorderStrong
                ),
                TazRadius.card
            )
            .padding(start = TazSpace.md, end = TazSpace.xs),
        verticalAlignment = Alignment.CenterVertically
    ) {
        TazIcon(
            TazIcons.Search, null, size = TazSize.iconSm,
            tint = if (focused) TazColors.Green else TazColors.TextTertiary
        )
        Spacer(Modifier.width(TazSpace.sm))
        Box(Modifier.weight(1f), contentAlignment = Alignment.CenterStart) {
            if (query.isEmpty()) {
                Text(
                    "Search help topics", fontSize = TazType.bodySize,
                    color = TazColors.TextTertiary, maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            BasicTextField(
                value = query,
                onValueChange = onQueryChange,
                modifier = Modifier.fillMaxWidth()
                    .onFocusChanged { focused = it.isFocused },
                singleLine = true,
                textStyle = LocalTextStyle.current.copy(
                    fontSize = TazType.bodySize,
                    color = TazColors.TextPrimary
                ),
                cursorBrush = SolidColor(TazColors.Green),
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search)
            )
        }
        if (query.isNotEmpty()) {
            Box(
                Modifier.size(TazSize.iconLg).clip(CircleShape)
                    .tazPressable(onClick = { onQueryChange("") }, pressScale = TazPress.compact),
                contentAlignment = Alignment.Center
            ) {
                TazIcon(
                    TazIcons.Close, "Clear search",
                    size = TazSize.iconSm, tint = TazColors.TextTertiary
                )
            }
        }
    }
}

/** One card, hairline dividers — the FAQ reads as a single list. */
@Composable
private fun FaqCard(
    items: List<FaqItem>,
    expandedQuestion: String?,
    onToggle: (String) -> Unit
) {
    Column(
        Modifier.padding(horizontal = TazSpace.gutter).fillMaxWidth()
            .clip(TazRadius.card).background(TazColors.Surface)
            .border(BorderStroke(1.dp, TazColors.CardBorder), TazRadius.card)
    ) {
        items.forEachIndexed { i, faq ->
            val expanded = expandedQuestion == faq.question
            val rotation by animateFloatAsState(
                targetValue = if (expanded) 180f else 0f,
                animationSpec = tween(TazMotion.fast)
            )
            Column(
                Modifier.fillMaxWidth()
                    .tazPressable(onClick = { onToggle(faq.question) }, pressScale = TazPress.compact)
                    .heightIn(min = TazSize.touchTarget)
                    .padding(TazSpace.lg)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        faq.question, fontSize = TazType.bodySize,
                        lineHeight = TazType.bodyLine,
                        fontWeight = FontWeight.Medium,
                        color = TazColors.TextPrimary,
                        modifier = Modifier.weight(1f)
                    )
                    Spacer(Modifier.width(TazSpace.sm))
                    TazIcon(
                        TazIcons.Expand, null,
                        modifier = Modifier.rotate(rotation),
                        size = TazSize.iconSm, tint = TazColors.TextTertiary
                    )
                }
                AnimatedVisibility(visible = expanded) {
                    Text(
                        faq.answer, fontSize = TazType.bodySize,
                        lineHeight = TazType.bodyLine,
                        color = TazColors.TextSecondary,
                        modifier = Modifier.padding(top = TazSpace.sm)
                    )
                }
            }
            if (i < items.lastIndex) {
                Box(
                    Modifier.fillMaxWidth()
                        .padding(horizontal = TazSpace.lg)
                        .height(1.dp)
                        .background(TazColors.CardBorder)
                )
            }
        }
    }
}



/**
 * Loading / error / content for the FAQ regions.
 *
 * Deliberately NOT "treat a failure as an empty list": an empty FAQ list and a
 * support service that is down are different things, and only one of them is
 * the customer's problem to retry.
 */
@Composable
private fun FaqContent(
    handle: LoadHandle<List<FaqItem>>,
    content: @Composable (List<FaqItem>) -> Unit
) {
    when (val state = handle.state) {
        is UiState.Loading -> Column(
            Modifier.fillMaxWidth().padding(horizontal = TazSpace.gutter),
            verticalArrangement = Arrangement.spacedBy(TazSpace.sm)
        ) {
            repeat(3) { SkeletonBlock(height = 44.dp) }
        }
        is UiState.Failure -> ErrorState(state.error, onRetry = { handle.retry() })
        is UiState.Empty -> content(emptyList())
        is UiState.Success -> content(state.data)
    }
}

/**
 * The order this help session is about: its state, what it cost, and what was
 * in it.
 *
 * Why it earns the space at the top: a customer who taps "Need help" from an
 * order already knows which order they mean, and making them say it again is
 * the moment support starts feeling like paperwork. Showing the items also
 * settles most "which one was damaged?" questions before anyone asks.
 *
 * The mockup pairs this with a "Quick Resolution Promise" banner guaranteeing a
 * refund within two minutes. That is a service level nobody has committed to
 * (**D6**), so it is not drawn. When a refund SLA exists, it belongs here.
 */
@Composable
private fun OrderContextCard(orderId: String) {
    val load = rememberLoad<Order?>(orderId, isEmpty = { it == null }) {
        ServiceLocator.orders.getOrders().firstOrNull { it.id == orderId }
    }
    val order = (load.state as? UiState.Success)?.data ?: return

    val (statusLabel, statusTone) = when (order.status) {
        OrderStatus.PLACED -> "Order placed" to ChipTone.Neutral
        OrderStatus.PACKED -> "Packed" to ChipTone.Neutral
        OrderStatus.ON_THE_WAY -> "On the way" to ChipTone.Warning
        OrderStatus.DELIVERED -> "Delivered" to ChipTone.Success
    }

    Column(
        Modifier.padding(horizontal = TazSpace.gutter).fillMaxWidth()
            .clip(TazRadius.card).background(TazColors.Surface)
            .border(BorderStroke(1.dp, TazColors.CardBorder), TazRadius.card)
            .padding(TazSpace.lg)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            TazChip(statusLabel, statusTone, standalone = true)
            Spacer(Modifier.width(TazSpace.sm))
            Text(
                order.placedAtLabel, fontSize = TazType.captionSize,
                color = TazColors.TextTertiary, maxLines = 1,
                overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f)
            )
            Text(
                "${order.bill.grandTotal}", fontSize = TazType.priceSize,
                fontWeight = TazType.priceWeight, color = TazColors.TextPrimary, maxLines = 1
            )
        }
        Spacer(Modifier.height(TazSpace.sm))
        Text(
            "Order #${order.id}", fontSize = TazType.bodySize,
            fontWeight = FontWeight.SemiBold, color = TazColors.TextPrimary, maxLines = 1
        )
        Text(
            if (order.lines.size == 1) "1 item" else "${order.lines.size} items",
            fontSize = TazType.captionSize, color = TazColors.TextTertiary
        )
        Spacer(Modifier.height(TazSpace.md))
        Row(
            Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(TazSpace.sm)
        ) {
            order.lines.forEach { line ->
                Row(
                    Modifier.clip(TazRadius.chip).background(TazColors.SurfaceSunken)
                        .padding(TazSpace.xs),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    ProductImage(
                        line.product,
                        Modifier.size(36.dp).clip(TazRadius.chip),
                        glyphSize = TazType.bodySize, contentPadding = 4.dp
                    )
                    Spacer(Modifier.width(TazSpace.sm))
                    Column(Modifier.widthIn(max = 96.dp)) {
                        Text(
                            line.product.name, fontSize = TazType.microSize,
                            fontWeight = FontWeight.SemiBold, color = TazColors.TextPrimary,
                            maxLines = 1, overflow = TextOverflow.Ellipsis
                        )
                        Text(
                            line.product.unit, fontSize = TazType.microSize,
                            color = TazColors.TextTertiary, maxLines = 1
                        )
                    }
                }
            }
        }
    }
}

/**
 * Thin adapter onto the shared [TazListRow] so Help and Account cannot drift.
 *
 * Help previously carried a byte-identical private copy of the row and its
 * divider; two implementations of one control is how two screens stop matching.
 */
@Composable
private fun HelpListRow(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    title: String,
    subtitle: String?,
    onClick: () -> Unit,
) = TazListRow(icon = icon, title = title, subtitle = subtitle, onClick = onClick)
