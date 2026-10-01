package com.tazzzo.app.ui.club

import com.tazzzo.app.data.model.Money

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.slideInVertically
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
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.tazzzo.app.LocalAppState
import com.tazzzo.app.Screen
import com.tazzzo.app.config.MembershipConfig
import com.tazzzo.app.data.repository.ServiceLocator
import com.tazzzo.app.membership.MembershipPurchase
import com.tazzzo.app.membership.MembershipPurchaseSession
import com.tazzzo.app.theme.TazColors
import com.tazzzo.app.theme.TazMotion
import com.tazzzo.app.theme.TazRadius
import com.tazzzo.app.theme.TazSize
import com.tazzzo.app.theme.TazSpace
import com.tazzzo.app.theme.TazType
import com.tazzzo.app.ui.common.PillButton
import com.tazzzo.app.ui.common.TazTopBar
import com.tazzzo.app.ui.interaction.TazHaptic
import com.tazzzo.app.ui.interaction.rememberHaptics
import kotlinx.coroutines.launch

/**
 * Confirm and pay for Tazzzo Club, then show what was unlocked.
 *
 * One screen drives every payment phase because they are one continuous
 * moment for the customer — bouncing between destinations mid-payment is how
 * an app loses someone's trust at precisely the wrong second.
 *
 * The rule this screen exists to enforce visually: nothing here claims
 * membership is active until the repository says so, and the repository only
 * says so after server-side verification.
 */
@Composable
fun ClubCheckoutScreen() {
    val app = LocalAppState.current
    val scope = rememberCoroutineScope()
    val haptics = rememberHaptics()
    val plan = MembershipConfig.plan

    // One session per visit: its receipt id is the idempotency key, reused on
    // every retry so "Try again" replays one purchase intent.
    val session = remember { MembershipPurchaseSession(plan) }
    val phase = session.phase

    // Mirror activation into app state the moment it becomes real.
    LaunchedEffect(phase) {
        val p = phase
        if (p is MembershipPurchaseSession.Phase.Success) {
            app.membership = p.state
            haptics.perform(TazHaptic.Success)
        }
    }

    fun pay() {
        scope.launch {
            MembershipPurchase.start(
                session = session,
                gateway = ServiceLocator.payments,
                memberships = ServiceLocator.membership,
                placedAtLabel = "Today"
            )
        }
    }

    Box(Modifier.fillMaxSize().background(TazColors.Cream)) {
        Column(Modifier.fillMaxSize()) {
            TazTopBar(
                if (phase is MembershipPurchaseSession.Phase.Success) plan.name else "Confirm membership",
                onBack = {
                    // Back is refused mid-payment: leaving while money is in
                    // flight is how a customer ends up not knowing what they
                    // paid for.
                    if (!session.isBusy) app.back()
                }
            )

            Column(
                Modifier.weight(1f).fillMaxWidth().verticalScroll(rememberScrollState())
                    .padding(TazSpace.gutter)
            ) {
                when (val p = phase) {
                    is MembershipPurchaseSession.Phase.Success -> WelcomeToClub(p)
                    else -> {
                        OrderSummaryCard(plan.name, plan.price, ServiceLocator.payments.isTestMode)
                        Spacer(Modifier.height(TazSpace.lg))
                        PhaseMessage(p)
                    }
                }
            }

            PaymentFooter(
                phase = phase,
                price = plan.price,
                onPay = ::pay,
                onDone = {
                    app.goHome()
                },
                onBackToClub = { app.back() }
            )
        }
    }
}

@Composable
private fun OrderSummaryCard(planName: String, price: Money, isTestMode: Boolean) {
    Column(
        Modifier.fillMaxWidth().clip(TazRadius.card).background(TazColors.Surface)
            .border(BorderStroke(1.dp, TazColors.CardBorder), TazRadius.card)
            .padding(TazSpace.lg)
    ) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(
                    planName, fontSize = TazType.titleSize, fontWeight = TazType.titleWeight,
                    color = TazColors.TextPrimary
                )
                Text(
                    "Membership", fontSize = TazType.captionSize,
                    color = TazColors.TextSecondary
                )
            }
            Text(
                "$price", fontSize = TazType.priceHeroSize,
                fontWeight = TazType.priceWeight, color = TazColors.TextPrimary
            )
        }
        if (isTestMode) {
            Spacer(Modifier.height(TazSpace.md))
            // A test payment must never be mistakable for a real one.
            Box(
                Modifier.clip(TazRadius.chip).background(TazColors.WarningSoft)
                    .padding(horizontal = TazSpace.sm, vertical = TazSpace.xxs)
            ) {
                Text(
                    "TEST PAYMENT — no money will be charged",
                    fontSize = TazType.microSize, fontWeight = TazType.microWeight,
                    color = TazColors.Warning
                )
            }
        }
    }
}

/** Copy for every non-success phase. Wording is the whole job here. */
@Composable
private fun PhaseMessage(phase: MembershipPurchaseSession.Phase) {
    when (phase) {
        is MembershipPurchaseSession.Phase.Pending -> StatusCard(
            tone = Tone.Neutral,
            title = "We're checking your payment",
            body = "Your Tazzzo Club membership hasn't been activated yet. " +
                "Don't pay again — we'll update this as soon as we hear back."
        )
        is MembershipPurchaseSession.Phase.Failed -> StatusCard(
            tone = Tone.Danger,
            title = "Payment didn't go through",
            body = phase.reason
        )
        is MembershipPurchaseSession.Phase.Cancelled -> StatusCard(
            tone = Tone.Neutral,
            title = "Membership purchase cancelled",
            body = "No membership was activated and nothing was charged."
        )
        else -> Unit
    }
}

private enum class Tone { Neutral, Danger }

@Composable
private fun StatusCard(tone: Tone, title: String, body: String) {
    val bg = if (tone == Tone.Danger) TazColors.DangerSoft else TazColors.SurfaceSunken
    val ink = if (tone == Tone.Danger) TazColors.Danger else TazColors.TextPrimary
    Column(
        Modifier.fillMaxWidth().clip(TazRadius.card).background(bg).padding(TazSpace.lg)
    ) {
        Text(title, fontSize = TazType.titleSize, fontWeight = TazType.titleWeight, color = ink)
        Spacer(Modifier.height(TazSpace.xxs))
        Text(
            body, fontSize = TazType.captionSize, lineHeight = TazType.captionLine,
            color = TazColors.TextSecondary
        )
    }
}

/**
 * The payoff moment.
 *
 * Restrained on purpose: a check, the badge, then the benefits revealing in
 * sequence. No confetti storm. Motion is staged through [AnimatedVisibility]
 * so it respects the same reduce-motion gate as the rest of the app.
 */
@Composable
private fun WelcomeToClub(success: MembershipPurchaseSession.Phase.Success) {
    val plan = MembershipConfig.plan
    var revealBenefits by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { revealBenefits = true }

    Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
        Spacer(Modifier.height(TazSpace.xl))
        Box(
            Modifier.size(72.dp).clip(CircleShape).background(TazColors.GreenSoft),
            contentAlignment = Alignment.Center
        ) {
            Text("✓", fontSize = TazType.displaySize, color = TazColors.Green)
        }
        Spacer(Modifier.height(TazSpace.lg))
        Text(
            "Welcome to ${plan.name}",
            fontSize = TazType.h1Size, fontWeight = TazType.h1Weight,
            lineHeight = TazType.h1Line, color = TazColors.TextPrimary,
            textAlign = TextAlign.Center
        )
        Spacer(Modifier.height(TazSpace.xs))
        Text(
            "Your membership is active",
            fontSize = TazType.bodySize, color = TazColors.TextSecondary
        )

        Spacer(Modifier.height(TazSpace.xl))
        AnimatedVisibility(
            visible = revealBenefits,
            enter = slideInVertically(tween(TazMotion.normal)) { it / 3 } + fadeIn(tween(TazMotion.normal))
        ) {
            Column(Modifier.fillMaxWidth()) {
                plan.benefits.forEach { b ->
                    Row(
                        Modifier.fillMaxWidth().padding(vertical = TazSpace.xs),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(b.emoji, fontSize = TazType.titleSize)
                        Spacer(Modifier.width(TazSpace.md))
                        Column(Modifier.weight(1f)) {
                            Text(
                                b.title, fontSize = TazType.bodySize,
                                fontWeight = FontWeight.SemiBold, color = TazColors.TextPrimary
                            )
                            Text(
                                b.description, fontSize = TazType.captionSize,
                                color = TazColors.TextSecondary
                            )
                        }
                        Text("Unlocked", fontSize = TazType.microSize, color = TazColors.Green)
                    }
                }
            }
        }

        Spacer(Modifier.height(TazSpace.xl))
        // Receipt. A paid purchase must always leave a record on screen.
        Column(
            Modifier.fillMaxWidth().clip(TazRadius.card).background(TazColors.Surface)
                .border(BorderStroke(1.dp, TazColors.CardBorder), TazRadius.card)
                .padding(TazSpace.lg)
        ) {
            ReceiptRow("Membership fee", "${success.transaction.amount}")
            ReceiptRow("Payment status", "✓ Paid")
            ReceiptRow("Reference", success.transaction.paymentReference ?: "—")
            if (success.transaction.isTestPayment) {
                Spacer(Modifier.height(TazSpace.xs))
                Text(
                    "TEST PAYMENT — no money was charged",
                    fontSize = TazType.microSize, fontWeight = TazType.microWeight,
                    color = TazColors.Warning
                )
            }
        }
    }
}

@Composable
private fun ReceiptRow(label: String, value: String) {
    Row(Modifier.fillMaxWidth().padding(vertical = TazSpace.xxs)) {
        Text(
            label, fontSize = TazType.captionSize, color = TazColors.TextSecondary,
            modifier = Modifier.weight(1f)
        )
        Text(
            value, fontSize = TazType.captionSize, fontWeight = FontWeight.SemiBold,
            color = TazColors.TextPrimary
        )
    }
}

@Composable
private fun PaymentFooter(
    phase: MembershipPurchaseSession.Phase,
    price: Money,
    onPay: () -> Unit,
    onDone: () -> Unit,
    onBackToClub: () -> Unit
) {
    Column(
        Modifier.fillMaxWidth().background(TazColors.Surface).navigationBarsPadding()
            .padding(TazSpace.gutter)
    ) {
        when (phase) {
            is MembershipPurchaseSession.Phase.Success ->
                PillButton("Start shopping", onDone, Modifier.fillMaxWidth(), haptic = TazHaptic.Tap)

            is MembershipPurchaseSession.Phase.Cancelled ->
                PillButton("Return to Tazzzo Club", onBackToClub, Modifier.fillMaxWidth())

            is MembershipPurchaseSession.Phase.Pending ->
                // No retry offered: a blind retry on a payment that may have
                // succeeded is how customers get charged twice.
                PillButton("Back to Tazzzo Club", onBackToClub, Modifier.fillMaxWidth(), filled = false)

            is MembershipPurchaseSession.Phase.Failed ->
                PillButton(
                    if (phase.retryable) "Try again" else "Back to Tazzzo Club",
                    if (phase.retryable) onPay else onBackToClub,
                    Modifier.fillMaxWidth()
                )

            else -> {
                val busy = phase !is MembershipPurchaseSession.Phase.Idle
                PillButton(
                    text = "Pay $price",
                    onClick = onPay,
                    modifier = Modifier.fillMaxWidth(),
                    loading = busy,
                    loadingText = when (phase) {
                        is MembershipPurchaseSession.Phase.CreatingOrder -> "Preparing secure payment…"
                        is MembershipPurchaseSession.Phase.Verifying -> "Confirming your payment…"
                        else -> "Opening secure checkout…"
                    },
                    haptic = TazHaptic.Tap
                )
                Spacer(Modifier.height(TazSpace.xs))
                Text(
                    "Secure payment", fontSize = TazType.microSize,
                    color = TazColors.TextTertiary, textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        }
    }
}
