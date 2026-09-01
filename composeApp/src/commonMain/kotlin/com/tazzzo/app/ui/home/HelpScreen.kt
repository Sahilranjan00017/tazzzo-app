package com.tazzzo.app.ui.home

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
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
import com.tazzzo.app.data.MockCatalog
import com.tazzzo.app.data.model.FaqItem
import com.tazzzo.app.theme.TazColors
import com.tazzzo.app.theme.TazIcons
import com.tazzzo.app.theme.TazMotion
import com.tazzzo.app.theme.TazRadius
import com.tazzzo.app.theme.TazSize
import com.tazzzo.app.theme.TazSpace
import com.tazzzo.app.theme.TazType
import com.tazzzo.app.ui.common.CartBar
import com.tazzzo.app.ui.common.EmptyState
import com.tazzzo.app.ui.common.PillButton
import com.tazzzo.app.ui.common.SectionHeader
import com.tazzzo.app.ui.common.TazIcon
import com.tazzzo.app.ui.common.TazTopBar

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
    val results = remember(needle) {
        if (needle.isEmpty()) MockCatalog.faqs
        else MockCatalog.faqs.filter {
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

                // ---- help with an order: real destinations ------------------
                Spacer(Modifier.height(TazSpace.lg))
                SectionHeader("Help with an order")
                Column(
                    Modifier.padding(horizontal = TazSpace.gutter).fillMaxWidth()
                        .clip(TazRadius.card).background(TazColors.Surface)
                        .border(BorderStroke(1.dp, TazColors.CardBorder), TazRadius.card)
                ) {
                    HelpMenuRow(
                        TazIcons.Receipt, "Track or manage an order",
                        "Open your orders"
                    ) { app.navigate(Screen.Orders) }
                    HelpMenuDivider()
                    HelpMenuRow(
                        TazIcons.Inventory, "Item missing or damaged",
                        "Message us about the order"
                    ) { openWhatsApp() }
                    HelpMenuDivider()
                    HelpMenuRow(
                        TazIcons.Payment, "Payment or refund",
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
                            .clickable { openWhatsApp() }
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
                    FaqCard(results, expandedQuestion) { q ->
                        expandedQuestion = if (expandedQuestion == q) null else q
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
                    .clickable { onQueryChange("") },
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
                    .clickable { onToggle(faq.question) }
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

/** Menu row in the Account tab's visual language, so help feels like one app. */
@Composable
private fun HelpMenuRow(
    icon: ImageVector,
    title: String,
    subtitle: String?,
    onClick: () -> Unit
) {
    Row(
        Modifier.fillMaxWidth()
            .clickable { onClick() }
            .heightIn(min = 56.dp)
            .padding(horizontal = TazSpace.lg, vertical = TazSpace.sm),
        verticalAlignment = Alignment.CenterVertically
    ) {
        TazIcon(icon, null, size = TazSize.iconSm, tint = TazColors.TextSecondary)
        Spacer(Modifier.width(TazSpace.md))
        Column(Modifier.weight(1f)) {
            Text(
                title, fontSize = TazType.bodySize, lineHeight = TazType.bodyLine,
                fontWeight = FontWeight.Medium, color = TazColors.TextPrimary,
                maxLines = 1, overflow = TextOverflow.Ellipsis
            )
            if (subtitle != null) {
                Text(
                    subtitle, fontSize = TazType.captionSize,
                    lineHeight = TazType.captionLine, color = TazColors.TextTertiary,
                    maxLines = 1, overflow = TextOverflow.Ellipsis
                )
            }
        }
        Spacer(Modifier.width(TazSpace.sm))
        TazIcon(TazIcons.Chevron, null, size = TazSize.iconXs, tint = TazColors.TextTertiary)
    }
}

/** Hairline inset past the icon rail, matching the Account menu. */
@Composable
private fun HelpMenuDivider() {
    Box(
        Modifier.fillMaxWidth()
            .padding(start = TazSpace.lg + TazSize.iconSm + TazSpace.md)
            .height(1.dp)
            .background(TazColors.CardBorder)
    )
}
