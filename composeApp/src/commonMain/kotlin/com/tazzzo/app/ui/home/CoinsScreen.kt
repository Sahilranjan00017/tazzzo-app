package com.tazzzo.app.ui.home

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.tazzzo.app.LocalAppState
import com.tazzzo.app.analytics.Analytics
import com.tazzzo.app.analytics.AnalyticsEvents
import com.tazzzo.app.config.AppConfig
import com.tazzzo.app.data.model.CoinTransaction
import com.tazzzo.app.data.repository.ServiceLocator
import com.tazzzo.app.theme.TazColors
import com.tazzzo.app.theme.TazIcons
import com.tazzzo.app.theme.TazRadius
import com.tazzzo.app.theme.TazSize
import com.tazzzo.app.theme.TazSpace
import com.tazzzo.app.theme.TazType
import com.tazzzo.app.ui.common.CartBar
import com.tazzzo.app.ui.common.SectionHeader
import com.tazzzo.app.ui.common.TazIcon
import com.tazzzo.app.ui.common.TazTopBar
import com.tazzzo.app.ui.state.rememberLoad
import com.tazzzo.app.ui.common.StateHost

@Composable
fun CoinsScreen() {
    LaunchedEffect(Unit) {
        Analytics.track(AnalyticsEvents.COIN_VIEW)
    }
    val app = LocalAppState.current
    // Balance and ledger are one screen and must agree, so they load together:
    // a balance rendered beside a ledger that failed to load is worse than an
    // honest error. An empty ledger is NOT an empty screen — a customer with a
    // balance and no history still has a balance — so emptiness is decided by
    // the pair never being present, not by the list being short.
    val coins = rememberLoad<Pair<Int, List<CoinTransaction>>>(
        isEmpty = { false }
    ) {
        ServiceLocator.coins.getBalance() to ServiceLocator.coins.getLedger()
    }

    Box(Modifier.fillMaxSize().background(TazColors.Cream)) {
        Column(Modifier.fillMaxSize()) {
            TazTopBar("Tazzzo Coins", onBack = { app.back() })
            StateHost(
                handle = coins,
                modifier = Modifier.fillMaxSize(),
                loading = {
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator(color = TazColors.Green)
                    }
                }
            ) { (balance, ledger) ->
                Column(
                    Modifier.fillMaxSize().verticalScroll(rememberScrollState())
                        .padding(bottom = TazSpace.cartBarClearance)
                ) {
                    CoinsHeroCard(balance)
                    SectionHeader("How it works")
                    Column(
                        Modifier.padding(horizontal = TazSpace.gutter).fillMaxWidth()
                            .clip(TazRadius.card).background(TazColors.Surface)
                            .border(BorderStroke(1.dp, TazColors.CardBorder), TazRadius.card)
                            .padding(vertical = TazSpace.xs)
                    ) {
                        CoinStepRow(TazIcons.Bag, "Shop", "order anything you need")
                        CoinStepRow(
                            TazIcons.Coin, "Earn",
                            "${AppConfig.coins.earnPercent}% back as coins"
                        )
                        // Redemption step intentionally absent: the "Save" row
                        // (and any redeem-at-checkout UI) returns when D5 approves
                        // the redemption rules.
                    }
                    SectionHeader("Coin history")
                    Column(
                        Modifier.padding(horizontal = TazSpace.gutter).fillMaxWidth()
                            .clip(TazRadius.card).background(TazColors.Surface)
                            .border(BorderStroke(1.dp, TazColors.CardBorder), TazRadius.card)
                    ) {
                        ledger.forEachIndexed { i, tx ->
                            LedgerRow(tx)
                            if (i < ledger.lastIndex) {
                                HorizontalDivider(
                                    modifier = Modifier.padding(horizontal = TazSpace.lg),
                                    thickness = 1.dp, color = TazColors.CardBorder
                                )
                            }
                        }
                    }
                }
            }
        }
        CartBar()
    }
}

@Composable
private fun CoinsHeroCard(balance: Int) {
    Column(
        Modifier.padding(TazSpace.gutter).fillMaxWidth().clip(TazRadius.tile)
            .background(TazColors.GreenDark).padding(TazSpace.xxl),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Box(
            Modifier.size(64.dp).clip(CircleShape)
                .background(TazColors.White.copy(alpha = 0.10f)),
            contentAlignment = Alignment.Center
        ) {
            TazIcon(TazIcons.Coin, null, size = 40.dp, tint = TazColors.CoinGold)
        }
        Spacer(Modifier.height(TazSpace.md))
        Text(
            "$balance", fontSize = 40.sp, fontWeight = FontWeight.ExtraBold,
            color = TazColors.White
        )
        Spacer(Modifier.height(TazSpace.xs))
        // D5 (redemption rules) is not approved yet, so no "redeem" claim here —
        // only the config-derived coin value.
        Text(
            AppConfig.coins.valueCopy, fontSize = TazType.captionSize,
            lineHeight = TazType.captionLine, color = TazColors.White.copy(alpha = 0.7f)
        )
        Spacer(Modifier.height(TazSpace.lg))
        Box(
            Modifier.clip(TazRadius.pill).background(TazColors.White.copy(alpha = 0.12f))
                .padding(horizontal = TazSpace.md, vertical = TazSpace.sm)
        ) {
            Text(
                AppConfig.coins.earnCopy, fontSize = TazType.microSize,
                lineHeight = TazType.microLine, fontWeight = FontWeight.SemiBold,
                color = TazColors.CoinGold
            )
        }
    }
}

@Composable
private fun CoinStepRow(icon: ImageVector, title: String, subtitle: String) {
    Row(
        Modifier.fillMaxWidth().padding(horizontal = TazSpace.lg, vertical = TazSpace.md),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            Modifier.size(38.dp).clip(TazRadius.chip).background(TazColors.GreenSoft),
            contentAlignment = Alignment.Center
        ) {
            TazIcon(icon, null, size = TazSize.iconSm, tint = TazColors.Green)
        }
        Spacer(Modifier.width(TazSpace.md))
        Column {
            Text(
                title, fontSize = TazType.bodySize, lineHeight = TazType.bodyLine,
                fontWeight = FontWeight.SemiBold, color = TazColors.TextPrimary
            )
            Text(
                subtitle, fontSize = TazType.captionSize, lineHeight = TazType.captionLine,
                color = TazColors.TextSecondary
            )
        }
    }
}

@Composable
private fun LedgerRow(tx: CoinTransaction) {
    val earned = tx.amount >= 0
    val tint = if (earned) TazColors.Success else TazColors.Danger
    Row(
        Modifier.fillMaxWidth().padding(horizontal = TazSpace.lg, vertical = TazSpace.md),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(Modifier.weight(1f)) {
            Text(
                tx.title, fontSize = TazType.bodySize, lineHeight = TazType.bodyLine,
                fontWeight = FontWeight.Medium, color = TazColors.TextPrimary
            )
            Text(
                tx.dateLabel, fontSize = TazType.captionSize,
                lineHeight = TazType.captionLine, color = TazColors.TextTertiary
            )
        }
        Spacer(Modifier.width(TazSpace.sm))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                if (earned) "+${tx.amount}" else "${tx.amount}",
                fontSize = TazType.bodySize, lineHeight = TazType.bodyLine,
                fontWeight = FontWeight.SemiBold, color = tint
            )
            Spacer(Modifier.width(TazSpace.xs))
            TazIcon(TazIcons.Coin, null, size = TazSize.iconXs, tint = tint)
        }
    }
}
