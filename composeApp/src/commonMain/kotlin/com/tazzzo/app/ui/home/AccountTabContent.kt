package com.tazzzo.app.ui.home

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.tazzzo.app.LocalAppState
import com.tazzzo.app.Screen
import com.tazzzo.app.ui.interaction.TazPress
import com.tazzzo.app.ui.interaction.tazPressable
import com.tazzzo.app.config.MembershipConfig
import com.tazzzo.app.ui.interaction.tazPressableCard
import com.tazzzo.app.theme.TazColors
import com.tazzzo.app.theme.TazIcons
import com.tazzzo.app.theme.TazRadius
import com.tazzzo.app.theme.TazSize
import com.tazzzo.app.theme.TazSpace
import com.tazzzo.app.theme.TazType
import com.tazzzo.app.ui.common.TazIcon

/** "Account" bottom tab — profile, quick stats and the settings menu. */
@Composable
fun AccountTabContent() {
    val app = LocalAppState.current

    Column(
        Modifier.fillMaxSize().background(TazColors.Cream)
            .statusBarsPadding()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = TazSpace.gutter)
            .padding(top = TazSpace.lg, bottom = TazSpace.cartBarClearance)
    ) {
        ProfileHeaderCard()

        Spacer(Modifier.height(TazSpace.md))

        // ------------------------------------------------------------------
        // Quick stats
        // ------------------------------------------------------------------
        Row(
            Modifier.fillMaxWidth().height(IntrinsicSize.Min),
            horizontalArrangement = Arrangement.spacedBy(TazSpace.md)
        ) {
            QuickStatCard(
                TazIcons.Coin, TazColors.CoinInk,
                "${app.user.coinBalance}", "Coins"
            ) { app.navigate(Screen.Coins) }
            QuickStatCard(
                TazIcons.Receipt, TazColors.Green, null, "Orders"
            ) { app.navigate(Screen.Orders) }
            QuickStatCard(
                TazIcons.Help, TazColors.Green, null, "Help"
            ) { app.navigate(Screen.Help) }
        }

        Spacer(Modifier.height(TazSpace.md))

        // Club standing FIRST for a member: what they saved, how far the next
        // milestone and reward are. This is the strongest thing Account can
        // say to someone who paid ₹99 — and it is shown only to members.
        if (app.isClubMember) {
            com.tazzzo.app.ui.club.ClubProgressCard(
                app.membership,
                Modifier.tazPressableCard(onClick = { app.navigate(Screen.Club) }, shape = TazRadius.card)
            )
            Spacer(Modifier.height(TazSpace.md))
        }

        // ------------------------------------------------------------------
        // Main menu — one card, hairline dividers inset to the text
        // ------------------------------------------------------------------
        Column(
            Modifier.fillMaxWidth()
                .clip(TazRadius.card)
                .background(TazColors.Surface)
                .border(BorderStroke(1.dp, TazColors.CardBorder), TazRadius.card)
        ) {
            // Club sits first: for a member it is the most valuable thing on
            // this screen, and for everyone else it is the offer.
            MenuRow(
                TazIcons.Coin,
                if (app.isClubMember) "${MembershipConfig.plan.name} ✓"
                else MembershipConfig.plan.name,
                if (app.isClubMember)
                    "₹${app.membership.cumulativeSavingsRupees} saved · ${app.membership.eligibleOrderCount} eligible orders"
                else "₹${MembershipConfig.plan.priceRupees} · ${MembershipConfig.plan.discountRule.percent}% off eligible orders"
            ) {
                app.navigate(Screen.Club)
            }
            MenuDivider()
            MenuRow(TazIcons.Receipt, "Your orders", "Track and reorder") {
                app.navigate(Screen.Orders)
            }
            MenuDivider()
            MenuRow(TazIcons.Coin, "Tazzzo Coins", "Balance and rewards ledger") {
                app.navigate(Screen.Coins)
            }
            MenuDivider()
            MenuRow(TazIcons.Location, "Saved addresses", "Your delivery addresses") {
                app.navigate(Screen.Addresses)
            }
            MenuDivider()
            MenuRow(TazIcons.Mic, "Voice shopping", "Coming soon") {
                app.showVoiceSheet = true
            }
            MenuDivider()
            MenuRow(TazIcons.Help, "Need help", "FAQs and support") {
                app.navigate(Screen.Help)
            }
            MenuDivider()
            MenuRow(TazIcons.Info, "About Tazzzo", "Who we are") {
                app.navigate(Screen.About)
            }
        }

        Spacer(Modifier.height(TazSpace.lg))

        // ------------------------------------------------------------------
        // Log out + version
        // ------------------------------------------------------------------
        if (!app.user.isGuest) {
            Row(
                Modifier.fillMaxWidth()
                    .clip(TazRadius.card)
                    .background(TazColors.DangerSoft)
                    .tazPressable(onClick = { app.markLoggedOut() }, pressScale = TazPress.compact)
                    .defaultMinSize(minHeight = TazSize.touchTarget)
                    .padding(TazSpace.lg),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.Center
            ) {
                TazIcon(TazIcons.Logout, null, size = TazSize.iconSm, tint = TazColors.Danger)
                Spacer(Modifier.width(TazSpace.sm))
                Text(
                    "Log out", fontSize = TazType.bodySize, lineHeight = TazType.bodyLine,
                    fontWeight = FontWeight.SemiBold, color = TazColors.Danger
                )
            }
            Spacer(Modifier.height(TazSpace.lg))
        }

        Text(
            "Tazzzo v1.0 • Made in India",
            fontSize = TazType.microSize, lineHeight = TazType.microLine,
            color = TazColors.TextTertiary, textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth().padding(bottom = TazSpace.sm)
        )
    }
}

// ---------------------------------------------------------------------------
// Pieces
// ---------------------------------------------------------------------------

@Composable
private fun ProfileHeaderCard() {
    val app = LocalAppState.current
    Row(
        Modifier.fillMaxWidth()
            .clip(TazRadius.tile)
            .background(TazColors.Surface)
            .border(BorderStroke(1.dp, TazColors.CardBorder), TazRadius.tile)
            .padding(TazSpace.xl),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            Modifier.size(56.dp).clip(CircleShape).background(TazColors.GreenSoft),
            contentAlignment = Alignment.Center
        ) {
            TazIcon(TazIcons.Account, null, size = 26.dp, tint = TazColors.Green)
        }
        Spacer(Modifier.width(TazSpace.lg))
        Column(Modifier.weight(1f)) {
            Text(
                if (app.user.isGuest) "Welcome, Guest" else app.user.name,
                fontSize = TazType.titleSize, fontWeight = TazType.titleWeight,
                lineHeight = TazType.titleLine, color = TazColors.TextPrimary,
                maxLines = 1, overflow = TextOverflow.Ellipsis
            )
            Spacer(Modifier.height(TazSpace.xxs))
            Text(
                if (app.user.isGuest) "Login for a personalised experience"
                else app.user.phone,
                fontSize = TazType.bodySize, lineHeight = TazType.bodyLine,
                color = TazColors.TextSecondary, maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
        }
        if (app.user.isGuest) {
            Spacer(Modifier.width(TazSpace.sm))
            Box(
                Modifier
                    .defaultMinSize(minHeight = TazSize.touchTarget)
                    .clip(TazRadius.pill)
                    .tazPressable(onClick = { app.navigate(Screen.Login) }, pressScale = TazPress.compact)
                    .padding(horizontal = TazSpace.md),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    "Log in", fontSize = TazType.bodySize, lineHeight = TazType.bodyLine,
                    fontWeight = FontWeight.SemiBold, color = TazColors.Green, maxLines = 1
                )
            }
        }
    }
}

@Composable
private fun RowScope.QuickStatCard(
    icon: ImageVector,
    tint: Color,
    value: String?,
    label: String,
    onClick: () -> Unit
) {
    Column(
        Modifier.weight(1f)
            .fillMaxHeight()
            .clip(TazRadius.card)
            .background(TazColors.Surface)
            .border(BorderStroke(1.dp, TazColors.CardBorder), TazRadius.card)
            .tazPressable(onClick = { onClick() }, pressScale = TazPress.compact)
            .defaultMinSize(minHeight = TazSize.touchTarget)
            .padding(vertical = TazSpace.md, horizontal = TazSpace.sm),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        TazIcon(icon, null, size = TazSize.iconSm, tint = tint)
        Spacer(Modifier.height(TazSpace.xs))
        if (value != null) {
            Text(
                value, fontSize = TazType.titleSize, fontWeight = TazType.titleWeight,
                lineHeight = TazType.titleLine, color = TazColors.TextPrimary, maxLines = 1
            )
        }
        Text(
            label, fontSize = TazType.captionSize, lineHeight = TazType.captionLine,
            color = TazColors.TextSecondary, maxLines = 1,
            overflow = TextOverflow.Ellipsis, textAlign = TextAlign.Center
        )
    }
}

@Composable
private fun MenuRow(
    icon: ImageVector,
    title: String,
    subtitle: String?,
    onClick: () -> Unit
) {
    Row(
        Modifier.fillMaxWidth()
            .tazPressable(onClick = { onClick() }, pressScale = TazPress.compact)
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

/** Hairline inset to the text column, so the icon rail reads as one edge. */
@Composable
private fun MenuDivider() {
    Box(
        Modifier.fillMaxWidth()
            .padding(start = TazSpace.lg + TazSize.iconSm + TazSpace.md)
            .height(1.dp)
            .background(TazColors.CardBorder)
    )
}
