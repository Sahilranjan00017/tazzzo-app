package com.tazzzo.app.ui.club

import com.tazzzo.app.data.model.Money

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
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.material3.Text
import com.tazzzo.app.LocalAppState
import com.tazzzo.app.Screen
import com.tazzzo.app.analytics.Analytics
import com.tazzzo.app.analytics.AnalyticsEvents
import com.tazzzo.app.config.MembershipCalculator
import com.tazzzo.app.config.MembershipConfig
import com.tazzzo.app.data.model.MembershipBenefit
import com.tazzzo.app.theme.TazColors
import com.tazzzo.app.theme.TazRadius
import com.tazzzo.app.theme.TazSize
import com.tazzzo.app.theme.TazSpace
import com.tazzzo.app.theme.TazType
import com.tazzzo.app.ui.common.PillButton
import com.tazzzo.app.ui.common.TazTopBar
import com.tazzzo.app.ui.interaction.TazHaptic

/**
 * Tazzzo Club landing page.
 *
 * Deliberate ordering: the customer sees what the membership DOES, then a
 * worked example of what it saves, and only then the price and the CTA. Asking
 * for ₹99 above the fold, before any of that, is how a membership reads as a
 * toll rather than a deal.
 *
 * Every number on this screen — price, percent, thresholds, benefit copy — is
 * read from [MembershipConfig.plan]. Nothing here is a literal.
 */
@Composable
fun ClubScreen() {
    val app = LocalAppState.current
    val plan = MembershipConfig.plan
    val alreadyMember = app.isClubMember

    LaunchedEffect(Unit) {
        Analytics.track(AnalyticsEvents.MEMBERSHIP_VIEW, mapOf("plan" to plan.id))
    }

    Box(Modifier.fillMaxSize().background(TazColors.Cream)) {
        Column(Modifier.fillMaxSize()) {
            TazTopBar(plan.name, onBack = { app.back() })

            Column(
                Modifier.weight(1f).fillMaxWidth().verticalScroll(rememberScrollState())
            ) {
                ClubHero(plan.name, plan.tagline, plan.price, alreadyMember)

                Spacer(Modifier.height(TazSpace.xl))
                SectionLabel("What you get")
                Spacer(Modifier.height(TazSpace.md))
                plan.benefits.forEach { benefit ->
                    BenefitRow(benefit)
                    Spacer(Modifier.height(TazSpace.sm))
                }

                if (!alreadyMember) {
                    Spacer(Modifier.height(TazSpace.lg))
                    WhyJoinCard()
                }

                Spacer(Modifier.height(TazSpace.xxl))
            }

            // Sticky footer: the price and the ask, always visible.
            if (!alreadyMember) {
                JoinFooter(
                    price = plan.price,
                    onJoin = {
                        Analytics.track(AnalyticsEvents.MEMBERSHIP_JOIN_TAP, mapOf("plan" to plan.id))
                        app.navigate(Screen.ClubCheckout)
                    }
                )
            }
        }
    }
}

@Composable
private fun ClubHero(name: String, tagline: String, price: Money, alreadyMember: Boolean) {
    Column(
        Modifier.fillMaxWidth()
            .padding(horizontal = TazSpace.gutter)
            .padding(top = TazSpace.lg)
            .clip(TazRadius.tile)
            .background(TazColors.GreenDark)
            .padding(TazSpace.xxl),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            name,
            fontSize = TazType.h1Size, fontWeight = TazType.h1Weight,
            lineHeight = TazType.h1Line, color = TazColors.White,
            textAlign = TextAlign.Center
        )
        Spacer(Modifier.height(TazSpace.sm))
        Text(
            tagline,
            fontSize = TazType.bodySize, lineHeight = TazType.bodyLine,
            color = TazColors.White.copy(alpha = 0.82f), textAlign = TextAlign.Center
        )
        Spacer(Modifier.height(TazSpace.lg))
        if (alreadyMember) {
            Box(
                Modifier.clip(TazRadius.pill).background(TazColors.White)
                    .padding(horizontal = TazSpace.lg, vertical = TazSpace.sm)
            ) {
                Text(
                    "Member ✓", color = TazColors.GreenDark,
                    fontSize = TazType.buttonSize, fontWeight = TazType.buttonWeight
                )
            }
        } else {
            Text(
                "$price",
                fontSize = TazType.displaySize, fontWeight = TazType.displayWeight,
                lineHeight = TazType.displayLine, color = TazColors.White
            )
        }
    }
}

@Composable
private fun SectionLabel(text: String) {
    Text(
        text.uppercase(),
        fontSize = TazType.microSize, fontWeight = TazType.microWeight,
        letterSpacing = TazType.labelTracking, color = TazColors.TextTertiary,
        modifier = Modifier.padding(horizontal = TazSpace.gutter)
    )
}

@Composable
private fun BenefitRow(benefit: MembershipBenefit) {
    Row(
        Modifier.fillMaxWidth()
            .padding(horizontal = TazSpace.gutter)
            .clip(TazRadius.card)
            .background(TazColors.Surface)
            .border(BorderStroke(1.dp, TazColors.CardBorder), TazRadius.card)
            .padding(TazSpace.lg),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            Modifier.size(TazSize.buttonHeightSm).clip(CircleShape)
                .background(TazColors.GreenSoft),
            contentAlignment = Alignment.Center
        ) {
            // Emoji as CONTENT (the benefit's own mark), never as a control.
            Text(benefit.emoji, fontSize = TazType.titleSize)
        }
        Spacer(Modifier.width(TazSpace.md))
        Column(Modifier.weight(1f)) {
            Text(
                benefit.title,
                fontSize = TazType.titleSize, fontWeight = TazType.titleWeight,
                lineHeight = TazType.titleLine, color = TazColors.TextPrimary
            )
            Spacer(Modifier.height(TazSpace.xxs))
            Text(
                benefit.description,
                fontSize = TazType.captionSize, lineHeight = TazType.captionLine,
                color = TazColors.TextSecondary
            )
        }
    }
}

/**
 * The "why join" worked example.
 *
 * Labelled as an EXAMPLE in the copy, and the figure is computed from the live
 * plan rather than written down, so it can never drift from what the customer
 * would actually be charged. It deliberately does NOT promise that the ₹99 is
 * recovered — the business rules do not guarantee that, so the app must not
 * imply it. See BLOCKERS.md D6 on unsubstantiated claims.
 */
@Composable
private fun WhyJoinCard() {
    val plan = MembershipConfig.plan
    val exampleOrder = plan.discountRule.minOrderValue + Money.ofRupees(200)   // a realistic eligible basket
    val saving = MembershipCalculator.exampleSavings(plan, exampleOrder)

    Column(
        Modifier.fillMaxWidth()
            .padding(horizontal = TazSpace.gutter)
            .clip(TazRadius.card)
            .background(TazColors.GreenSoft)
            .padding(TazSpace.lg)
    ) {
        Text(
            "Why join?",
            fontSize = TazType.titleSize, fontWeight = TazType.titleWeight,
            color = TazColors.TextPrimary
        )
        Spacer(Modifier.height(TazSpace.sm))
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(
                    "Example: a $exampleOrder eligible order",
                    fontSize = TazType.captionSize, lineHeight = TazType.captionLine,
                    color = TazColors.TextSecondary
                )
                Spacer(Modifier.height(TazSpace.xxs))
                Text(
                    "Club savings",
                    fontSize = TazType.bodySize, fontWeight = FontWeight.SemiBold,
                    color = TazColors.TextPrimary
                )
            }
            Text(
                "$saving",
                fontSize = TazType.priceHeroSize, fontWeight = TazType.priceWeight,
                color = TazColors.Green
            )
        }
        Spacer(Modifier.height(TazSpace.sm))
        Text(
            "An illustration, not a guarantee — your savings depend on what you buy.",
            fontSize = TazType.microSize, lineHeight = TazType.microLine,
            color = TazColors.TextTertiary
        )
    }
}

@Composable
private fun JoinFooter(price: Money, onJoin: () -> Unit) {
    Column(
        Modifier.fillMaxWidth()
            .background(TazColors.Surface)
            .navigationBarsPadding()
            .padding(TazSpace.gutter)
    ) {
        // The CTA states the amount. "Join now" while charging ₹99 is the kind
        // of vagueness that turns a good deal into a complaint.
        PillButton(
            text = "Join Tazzzo Club — $price",
            onClick = onJoin,
            haptic = TazHaptic.Tap,
            modifier = Modifier.fillMaxWidth()
        )
        Spacer(Modifier.height(TazSpace.xs))
        Text(
            "Secure payment",
            fontSize = TazType.microSize, color = TazColors.TextTertiary,
            textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth()
        )
    }
}
