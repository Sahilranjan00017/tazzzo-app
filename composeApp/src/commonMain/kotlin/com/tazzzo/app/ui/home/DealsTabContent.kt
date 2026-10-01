package com.tazzzo.app.ui.home

import androidx.compose.foundation.background
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
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.tazzzo.app.LocalAppState
import com.tazzzo.app.Screen
import com.tazzzo.app.config.CampaignConfig
import com.tazzzo.app.config.PromotionConfig
import com.tazzzo.app.data.model.Promotion
import com.tazzzo.app.data.model.PromotionAudience
import com.tazzzo.app.data.repository.ServiceLocator
import com.tazzzo.app.theme.TazColors
import com.tazzzo.app.theme.TazRadius
import com.tazzzo.app.theme.TazSpace
import com.tazzzo.app.theme.TazType
import com.tazzzo.app.ui.common.EmptyState
import com.tazzzo.app.ui.common.PillButton
import com.tazzzo.app.ui.common.ProductCard
import com.tazzzo.app.ui.common.SectionHeader
import com.tazzzo.app.ui.common.StateHost
import com.tazzzo.app.ui.interaction.TazPress
import com.tazzzo.app.ui.interaction.tazPressableCard
import com.tazzzo.app.ui.state.rememberLoad

/**
 * "Deals" bottom tab — savings as a destination.
 *
 * Every benchmark gives this a permanent slot rather than burying discounts in
 * a filter, because "what is cheap today" is one of the three questions people
 * open a grocery app to ask.
 *
 * The honesty rule holds throughout: an item appears here only when its MRP
 * genuinely exceeds its price, the saving is stated in rupees the customer can
 * check against the pack, and offers are listed with the conditions that
 * actually gate them. Nothing is promoted into this screen by a flag, and no
 * urgency, countdown or scarcity is invented.
 */
@Composable
fun DealsTabContent() {
    val app = LocalAppState.current
    val deals = rememberLoad { ServiceLocator.catalog.getDeals() }

    // Members-only offers are hidden from non-members rather than dangled:
    // listing an offer someone cannot use is a broken promise, not a nudge.
    val offers = PromotionConfig.active.filter {
        it.audience != PromotionAudience.MEMBERS_ONLY || app.isClubMember
    }

    Column(Modifier.fillMaxSize().background(TazColors.Cream)) {
        Column(
            Modifier.fillMaxWidth().background(TazColors.Surface).statusBarsPadding()
                .padding(horizontal = TazSpace.gutter, vertical = TazSpace.md)
        ) {
            Text(
                "Deals", fontSize = TazType.h2Size, fontWeight = TazType.h2Weight,
                lineHeight = TazType.h2Line, color = TazColors.TextPrimary
            )
            Text(
                "Everything with money off today", fontSize = TazType.captionSize,
                lineHeight = TazType.captionLine, color = TazColors.TextSecondary
            )
        }
        Box(Modifier.fillMaxWidth().height(1.dp).background(TazColors.CardBorder))

        StateHost(
            handle = deals,
            modifier = Modifier.fillMaxSize(),
            loading = {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(color = TazColors.Green)
                }
            },
            empty = {
                EmptyState(
                    emoji = "🏷️",
                    title = "No deals right now",
                    body = "When prices drop below MRP, everything on offer shows up here."
                )
            }
        ) { list ->
            LazyVerticalGrid(
                columns = GridCells.Fixed(2),
                modifier = Modifier.fillMaxSize().testTag("dealsGrid"),
                contentPadding = PaddingValues(
                    start = TazSpace.gutter, end = TazSpace.gutter,
                    top = TazSpace.md, bottom = TazSpace.cartBarClearance
                ),
                horizontalArrangement = Arrangement.spacedBy(TazSpace.sm),
                verticalArrangement = Arrangement.spacedBy(TazSpace.sm)
            ) {
                CampaignConfig.current?.let { campaign ->
                    item(span = { androidx.compose.foundation.lazy.grid.GridItemSpan(maxLineSpan) }) {
                        CampaignBand(
                            title = campaign.title,
                            subtitle = campaign.subtitle,
                            validity = campaign.validUntilLabel,
                            ctaLabel = campaign.ctaLabel,
                            onClick = {
                                campaign.categoryIds.firstOrNull()?.let {
                                    app.navigate(Screen.CategoryDetail(it))
                                }
                            }
                        )
                    }
                }

                if (offers.isNotEmpty()) {
                    item(span = { androidx.compose.foundation.lazy.grid.GridItemSpan(maxLineSpan) }) {
                        SectionHeader("Offers you can use")
                    }
                    items(offers, key = { "offer-" + it.id },
                        span = { androidx.compose.foundation.lazy.grid.GridItemSpan(maxLineSpan) }) { offer ->
                        OfferCard(offer)
                    }
                }

                item(span = { androidx.compose.foundation.lazy.grid.GridItemSpan(maxLineSpan) }) {
                    SectionHeader("Biggest savings")
                }
                items(list, key = { it.id }) { product ->
                    ProductCard(product, modifier = Modifier.fillMaxWidth())
                }
            }
        }
    }
}

/**
 * A dated merchandising block: what is on, until when, and one way in.
 *
 * The validity line is display copy from configuration. The client does not
 * gate merchandising on its own clock — a device with a wrong date must not be
 * able to show or hide an offer.
 */
@Composable
private fun CampaignBand(
    title: String,
    subtitle: String,
    validity: String?,
    ctaLabel: String,
    onClick: () -> Unit
) {
    Column(
        Modifier.fillMaxWidth().clip(TazRadius.card).background(TazColors.Green)
            .tazPressableCard(onClick = onClick, shape = TazRadius.card)
            .padding(TazSpace.lg)
    ) {
        validity?.let {
            Text(
                it.uppercase(), fontSize = TazType.microSize, fontWeight = TazType.microWeight,
                color = TazColors.White.copy(alpha = 0.75f), maxLines = 1
            )
            Spacer(Modifier.height(TazSpace.xs))
        }
        Text(
            title, fontSize = TazType.h2Size, fontWeight = TazType.h2Weight,
            lineHeight = TazType.h2Line, color = TazColors.White,
            maxLines = 2, overflow = TextOverflow.Ellipsis
        )
        Text(
            subtitle, fontSize = TazType.captionSize, lineHeight = TazType.captionLine,
            color = TazColors.White.copy(alpha = 0.85f),
            maxLines = 2, overflow = TextOverflow.Ellipsis
        )
        Spacer(Modifier.height(TazSpace.md))
        PillButton(text = ctaLabel, onClick = onClick, color = TazColors.White)
    }
}

/**
 * One offer, stated with the condition that actually gates it.
 *
 * A coupon shows its code so it can be typed at the cart; an automatic offer
 * says so. The minimum spend is never hidden, because an offer whose condition
 * only appears at the till reads as a bait.
 */
@Composable
private fun OfferCard(offer: Promotion) {
    Column(
        Modifier.fillMaxWidth().clip(TazRadius.card).background(TazColors.Surface)
            .padding(TazSpace.md)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                offer.title, fontSize = TazType.bodySize, fontWeight = TazType.h2Weight,
                color = TazColors.TextPrimary, maxLines = 2,
                overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f)
            )
            offer.couponCode?.let { code ->
                Spacer(Modifier.width(TazSpace.sm))
                Box(
                    Modifier.clip(TazRadius.chip).background(TazColors.OrangeSoft)
                        .padding(horizontal = TazSpace.sm, vertical = TazSpace.xxs)
                ) {
                    Text(
                        code, fontSize = TazType.microSize, fontWeight = TazType.microWeight,
                        color = TazColors.Orange, maxLines = 1
                    )
                }
            }
        }
        Spacer(Modifier.height(TazSpace.xxs))
        Text(
            offer.description, fontSize = TazType.captionSize, lineHeight = TazType.captionLine,
            color = TazColors.TextSecondary, maxLines = 2, overflow = TextOverflow.Ellipsis
        )
        val conditions = buildList {
            if (offer.minOrder.isPositive) add("On orders above ${offer.minOrder}")
            offer.maxDiscount?.let { add("Up to $it off") }
            if (offer.couponCode != null) add("Enter at cart") else add("Applied automatically")
            offer.validUntilLabel?.let { add("Until $it") }
        }
        if (conditions.isNotEmpty()) {
            Spacer(Modifier.height(TazSpace.xs))
            Text(
                conditions.joinToString("  ·  "), fontSize = TazType.microSize,
                color = TazColors.TextTertiary, maxLines = 2, overflow = TextOverflow.Ellipsis
            )
        }
    }
}
