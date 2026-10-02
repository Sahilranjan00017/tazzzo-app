package com.tazzzo.app.ui.home

import androidx.compose.foundation.background
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
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.tazzzo.app.LocalAppState
import kotlinx.coroutines.launch
import com.tazzzo.app.Screen
import com.tazzzo.app.config.MembershipConfig
import com.tazzzo.app.data.model.OrderStatus
import com.tazzzo.app.data.repository.ServiceLocator
import com.tazzzo.app.theme.TazColors
import com.tazzzo.app.theme.TazIcons
import com.tazzzo.app.theme.TazRadius
import com.tazzzo.app.theme.TazSize
import com.tazzzo.app.theme.TazSpace
import com.tazzzo.app.theme.TazType
import com.tazzzo.app.ui.common.ChipTone
import com.tazzzo.app.ui.common.TazChip
import com.tazzzo.app.ui.common.TazGroupedCard
import com.tazzzo.app.ui.common.TazIcon
import com.tazzzo.app.ui.common.TazListRow
import com.tazzzo.app.ui.common.TazRowDivider
import com.tazzzo.app.ui.interaction.TazPress
import com.tazzzo.app.ui.interaction.tazPressable
import com.tazzzo.app.ui.interaction.tazPressableCard
import com.tazzzo.app.ui.state.UiState
import com.tazzzo.app.ui.state.rememberLoad

/**
 * "Account" bottom tab.
 *
 * Follows the supplied redesign: profile card, three quick actions, a balance
 * card, then grouped setting sections and a log-out.
 *
 * Two departures from the mockup, both deliberate and both recorded in
 * `TAZZZO_UI_REDESIGN_SPEC.md`:
 *
 *  - The mockup's balance card is a **rupee wallet** with "Add Balance" and a
 *    cashback rate. Stored value is a regulated prepaid-instrument product in
 *    India, not a screen. The card renders **Tazzzo Coins**, the currency that
 *    actually exists, and links to its real ledger.
 *  - Its "Your Refunds", "E-Gift Cards" and "Payment Management" rows have no
 *    data and no destination behind them. A row that leads nowhere is worse
 *    than an absent row, so they are not drawn until something backs them.
 *
 * Every count here — active orders, saved addresses, coins, Club standing — is
 * read from real state. The mockup's "2 Active" and "12 Items" were comp values.
 */
@Composable
fun AccountTabContent() {
    val app = LocalAppState.current

    // Secondary data: each drives one chip. If a load fails the chip is simply
    // absent — a count nobody can verify must never be guessed at.
    // REMOTE: orders, coins and Club standing are NOT shown from mock data (there is no history/coins/Club contract yet);
    // the address count comes from the real AddressBook (in memory). The mock lists are never read.
    val remote = ServiceLocator.catalogMode == com.tazzzo.app.data.catalog.CatalogMode.REMOTE
    val orders = if (remote) null else rememberLoad { ServiceLocator.orders.getOrders() }
    val mockAddresses = if (remote) null else rememberLoad { ServiceLocator.addresses.getAddresses() }
    val bookState = if (remote) ServiceLocator.addressBook.state.collectAsState().value else null
    val orderList = (orders?.state as? UiState.Success)?.data
    // Null means "say nothing": either still loading, or there are no orders at
    // all. "All delivered" to somebody who has never ordered is wrong copy, not
    // a reassuring one.
    val orderStatus = orderList?.takeIf { it.isNotEmpty() }?.let { list ->
        val active = list.count { it.status != OrderStatus.DELIVERED }
        if (active > 0) "$active active" else "All delivered"
    }
    val addressCount = if (remote) (bookState as? com.tazzzo.app.data.address.BookState.Loaded)?.addresses?.size
    else (mockAddresses?.state as? UiState.Success)?.data?.size

    Column(
        Modifier.fillMaxSize().background(TazColors.Cream)
            .statusBarsPadding()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = TazSpace.gutter)
            .padding(top = TazSpace.lg, bottom = TazSpace.cartBarClearance)
    ) {
        ProfileHeaderCard()
        Spacer(Modifier.height(TazSpace.md))

        // ---- three quick actions -----------------------------------------
        Row(
            Modifier.fillMaxWidth().height(IntrinsicSize.Min),
            horizontalArrangement = Arrangement.spacedBy(TazSpace.md)
        ) {
            QuickActionTile(
                icon = TazIcons.Receipt,
                label = "Orders",
                status = orderStatus,
                statusTone = if (orderStatus?.endsWith("active") == true) ChipTone.Brand else ChipTone.Neutral,
                onClick = { app.navigate(Screen.Orders) }
            )
            QuickActionTile(
                icon = TazIcons.Help,
                label = "Help & care",
                // No chip. A tile chip is for a COUNT or a STATE; a description
                // at this width truncates ("FAQs and sup…") and leaves one tile
                // taller than the two beside it.
                status = null,
                onClick = { app.navigate(Screen.Help) }
            )
            QuickActionTile(
                icon = TazIcons.Location,
                label = "Addresses",
                status = addressCount?.let { if (it == 1) "1 saved" else "$it saved" },
                onClick = { app.navigate(Screen.Addresses) }
            )
        }
        Spacer(Modifier.height(TazSpace.md))

        if (!remote) {
            CoinBalanceCard()
            Spacer(Modifier.height(TazSpace.md))
        }

        // Club standing, for members only: what they saved and how far the next
        // milestone is. The strongest thing this screen can say to someone who
        // paid ₹99 — and meaningless to anyone who has not.
        if (!remote && app.isClubMember) {
            com.tazzzo.app.ui.club.ClubProgressCard(
                app.membership,
                Modifier.tazPressableCard(onClick = { app.navigate(Screen.Club) }, shape = TazRadius.card)
            )
            Spacer(Modifier.height(TazSpace.md))
        }

        // ---- preferences & perks -----------------------------------------
        SectionLabel("Preferences & perks")
        TazGroupedCard {
            if (!remote) {
            TazListRow(
                icon = TazIcons.Coin,
                title = MembershipConfig.plan.name,
                subtitle = if (app.isClubMember)
                    "${app.membership.cumulativeSavings} saved · ${app.membership.eligibleOrderCount} eligible orders"
                else "${MembershipConfig.plan.price} · ${MembershipConfig.plan.discountRule.percent}% off eligible orders",
                titleChip = if (app.isClubMember) "Active" else null,
                titleChipTone = ChipTone.Success,
                onClick = { app.navigate(Screen.Club) }
            )
            TazRowDivider()
            }
            TazListRow(
                icon = TazIcons.Bell,
                title = "Notifications",
                subtitle = "Order updates and offers",
                checked = app.notificationsEnabled,
                onCheckedChange = { app.notificationsEnabled = it }
            )
            TazRowDivider()
            // Voice is deliberately unchanged — same row, same copy, same sheet.
            TazListRow(
                icon = TazIcons.Mic,
                title = "Voice shopping",
                subtitle = "Coming soon",
                onClick = { app.showVoiceSheet = true }
            )
            TazRowDivider()
            // The mockup lists Hindi and Kannada as if selectable. Nothing is
            // translated yet, so offering them would be a promise the app
            // cannot keep the moment somebody taps one.
            TazListRow(
                icon = TazIcons.Globe,
                title = "Language",
                subtitle = "English · more coming soon",
                trailingText = "English"
            )
        }

        Spacer(Modifier.height(TazSpace.lg))
        SectionLabel("About")
        TazGroupedCard {
            TazListRow(
                icon = TazIcons.Info,
                title = "About Tazzzo",
                subtitle = "Who we are and how we source",
                onClick = { app.navigate(Screen.About) }
            )
        }

        Spacer(Modifier.height(TazSpace.lg))

        if (app.isAuthenticated) {
            val logoutScope = rememberCoroutineScope()
            Row(
                Modifier.fillMaxWidth()
                    .clip(TazRadius.card)
                    .background(TazColors.DangerSoft)
                    .tazPressable(onClick = { logoutScope.launch { app.logout() } }, pressScale = TazPress.compact)
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
private fun SectionLabel(text: String) {
    Text(
        text, fontSize = TazType.captionSize, fontWeight = FontWeight.SemiBold,
        color = TazColors.TextSecondary,
        modifier = Modifier.padding(start = TazSpace.xs, bottom = TazSpace.sm)
    )
}

/**
 * Profile card: who is signed in, their standing, and one way to edit it.
 *
 * The Club chip and the priority line render only for members. Showing a "VIP"
 * badge to someone who has not joined — as the mockup does — sells the tier and
 * devalues it in the same breath.
 */
@Composable
private fun ProfileHeaderCard() {
    val app = LocalAppState.current
    Row(
        Modifier.fillMaxWidth().clip(TazRadius.card).background(TazColors.Surface)
            .padding(TazSpace.lg),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(contentAlignment = Alignment.BottomEnd) {
            Box(
                Modifier.size(56.dp).clip(CircleShape).background(TazColors.GreenSoft),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    app.user.name.trim().take(1).uppercase().ifBlank { "T" },
                    fontSize = TazType.h2Size, fontWeight = TazType.h2Weight,
                    color = TazColors.Green
                )
            }
            if (app.isClubMember) {
                Box(
                    Modifier.size(20.dp).clip(CircleShape).background(TazColors.Green),
                    contentAlignment = Alignment.Center
                ) { TazIcon(TazIcons.Check, null, size = 12.dp, tint = TazColors.White) }
            }
        }
        Spacer(Modifier.width(TazSpace.md))
        Column(Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    app.user.name.ifBlank { "Your account" }, fontSize = TazType.titleSize, fontWeight = TazType.titleWeight,
                    color = TazColors.TextPrimary, maxLines = 1, overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f, fill = false)
                )
                if (app.isClubMember) {
                    Spacer(Modifier.width(TazSpace.sm))
                    TazChip(MembershipConfig.plan.name, ChipTone.Brand)
                }
            }
            if (app.user.phone.isNotBlank()) {
                Text(
                    "+91 ${app.user.phone}", fontSize = TazType.captionSize,
                    lineHeight = TazType.captionLine, color = TazColors.TextTertiary, maxLines = 1
                )
            }
            if (app.isClubMember) {
                Text(
                    "${MembershipConfig.plan.discountRule.percent}% off every eligible order",
                    fontSize = TazType.captionSize, lineHeight = TazType.captionLine,
                    color = TazColors.Green, maxLines = 1, overflow = TextOverflow.Ellipsis
                )
            }
        }
        Spacer(Modifier.width(TazSpace.sm))
        Box(
            Modifier.size(36.dp).clip(CircleShape).background(TazColors.SurfaceSunken)
                .semantics { contentDescription = "Edit profile" }
                .tazPressable(onClick = { app.navigate(Screen.Addresses) }, pressScale = TazPress.compact),
            contentAlignment = Alignment.Center
        ) { TazIcon(TazIcons.Edit, null, size = TazSize.iconSm, tint = TazColors.TextSecondary) }
    }
}

/**
 * The mockup's balance card, mapped onto the currency Tazzzo actually issues.
 *
 * Coins are earned on orders and spent at checkout, so "balance" is honest here
 * in a way a rupee wallet would not be.
 */
@Composable
private fun CoinBalanceCard() {
    val app = LocalAppState.current
    Column(
        Modifier.fillMaxWidth().clip(TazRadius.card).background(TazColors.Surface)
            .tazPressableCard(onClick = { app.navigate(Screen.Coins) }, shape = TazRadius.card)
            .padding(TazSpace.lg)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                Modifier.size(36.dp).clip(TazRadius.chip).background(TazColors.CoinSoft),
                contentAlignment = Alignment.Center
            ) { TazIcon(TazIcons.Coin, null, size = TazSize.iconSm, tint = TazColors.CoinInk) }
            Spacer(Modifier.width(TazSpace.md))
            Column(Modifier.weight(1f)) {
                Text(
                    "Tazzzo Coins", fontSize = TazType.bodySize, fontWeight = FontWeight.Medium,
                    color = TazColors.TextPrimary
                )
                Text(
                    "Earned on orders, spent at checkout", fontSize = TazType.captionSize,
                    lineHeight = TazType.captionLine, color = TazColors.TextTertiary,
                    maxLines = 1, overflow = TextOverflow.Ellipsis
                )
            }
            TazIcon(TazIcons.Chevron, null, size = TazSize.iconXs, tint = TazColors.TextTertiary)
        }
        Spacer(Modifier.height(TazSpace.md))
        Box(Modifier.fillMaxWidth().height(1.dp).background(TazColors.CardBorder))
        Spacer(Modifier.height(TazSpace.md))
        Text(
            "Balance", fontSize = TazType.captionSize, color = TazColors.TextTertiary
        )
        Text(
            "${app.user.coinBalance} coins", fontSize = TazType.h1Size,
            fontWeight = TazType.h1Weight, lineHeight = TazType.h1Line, color = TazColors.CoinInk
        )
    }
}

/**
 * One of the three quick actions.
 *
 * [status] is nullable on purpose: while its count is still loading, or if the
 * load failed, the tile shows its label alone rather than a placeholder number.
 */
@Composable
private fun RowScope.QuickActionTile(
    icon: ImageVector,
    label: String,
    status: String?,
    statusTone: ChipTone = ChipTone.Neutral,
    onClick: () -> Unit,
) {
    Column(
        Modifier.weight(1f).fillMaxHeight().clip(TazRadius.card).background(TazColors.Surface)
            // Merged before the press so the tile is ONE node carrying its
            // label — a tile whose icon, title and chip each announce
            // separately reads as three controls to a screen reader.
            // The merge collapses the chip into this node, so the status has
            // to be part of the label or it is announced nowhere.
            .semantics(mergeDescendants = true) {
                contentDescription = if (status != null) "$label, $status" else label
            }
            .tazPressableCard(onClick = onClick, shape = TazRadius.card)
            .padding(vertical = TazSpace.md, horizontal = TazSpace.sm),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Box(
            Modifier.size(40.dp).clip(CircleShape).background(TazColors.SurfaceSunken),
            contentAlignment = Alignment.Center
        ) { TazIcon(icon, null, size = TazSize.iconMd, tint = TazColors.Green) }
        Spacer(Modifier.height(TazSpace.sm))
        Text(
            label, fontSize = TazType.captionSize, fontWeight = FontWeight.SemiBold,
            color = TazColors.TextPrimary, textAlign = TextAlign.Center,
            maxLines = 1, overflow = TextOverflow.Ellipsis
        )
        if (status != null) {
            Spacer(Modifier.height(TazSpace.xxs))
            TazChip(status, statusTone)
        }
    }
}
