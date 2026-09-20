package com.tazzzo.app.ui.club

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.tazzzo.app.config.MembershipCalculator
import com.tazzzo.app.config.MembershipConfig
import com.tazzzo.app.data.model.MembershipState
import com.tazzzo.app.theme.TazColors
import com.tazzzo.app.theme.TazMotion
import com.tazzzo.app.theme.TazRadius
import com.tazzzo.app.theme.TazSpace
import com.tazzzo.app.theme.TazType

/**
 * The member's standing, in one card: what they have saved, how far the next
 * spend milestone is, and how many eligible orders until the next reward.
 *
 * Every figure comes from MembershipState + MembershipCalculator against the
 * live plan. Nothing is estimated. The bar animates once on arrival (a
 * restrained fill, not a game), and respects the same motion tokens as the
 * rest of the app. Used on order confirmation and on Account.
 */
@Composable
fun ClubProgressCard(state: MembershipState, modifier: Modifier = Modifier) {
    val plan = MembershipConfig.plan
    val nextSpend = MembershipCalculator.nextSpendMilestone(plan, state.cumulativeSpendRupees)
    val nextOrder = MembershipCalculator.nextOrderMilestone(plan, state)

    Column(
        modifier.fillMaxWidth().clip(TazRadius.card).background(TazColors.Surface)
            .border(BorderStroke(1.dp, TazColors.CardBorder), TazRadius.card)
            .padding(TazSpace.lg)
    ) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text(
                "${plan.name} ✓", fontSize = TazType.titleSize, fontWeight = TazType.titleWeight,
                color = TazColors.TextPrimary, modifier = Modifier.weight(1f)
            )
            Text(
                "₹${state.cumulativeSavingsRupees} saved", fontSize = TazType.captionSize,
                fontWeight = FontWeight.SemiBold, color = TazColors.Green
            )
        }

        if (nextSpend != null) {
            val (milestone, remaining) = nextSpend
            val fraction = (state.cumulativeSpendRupees.toFloat() / milestone.thresholdRupees).coerceIn(0f, 1f)
            val animated by animateFloatAsState(fraction, tween(TazMotion.normal), label = "clubProgress")
            Spacer(Modifier.height(TazSpace.md))
            Row(Modifier.fillMaxWidth()) {
                Text(
                    "₹${state.cumulativeSpendRupees} / ₹${milestone.thresholdRupees}",
                    fontSize = TazType.captionSize, fontWeight = FontWeight.SemiBold,
                    color = TazColors.TextPrimary, modifier = Modifier.weight(1f)
                )
                Text("₹$remaining to go", fontSize = TazType.captionSize, color = TazColors.TextSecondary)
            }
            Spacer(Modifier.height(TazSpace.xs))
            Box(Modifier.fillMaxWidth().height(8.dp).clip(TazRadius.pill).background(TazColors.SurfaceSunken)) {
                Box(Modifier.fillMaxHeight().fillMaxWidth(animated).clip(TazRadius.pill).background(TazColors.Green))
            }
            Spacer(Modifier.height(TazSpace.xxs))
            Text(milestone.title, fontSize = TazType.microSize, color = TazColors.TextTertiary)
        }

        if (nextOrder != null) {
            val (milestone, remaining) = nextOrder
            Spacer(Modifier.height(TazSpace.md))
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(
                        "Next reward: ${milestone.rewardTitle}", fontSize = TazType.bodySize,
                        fontWeight = FontWeight.SemiBold, color = TazColors.TextPrimary
                    )
                    Text(
                        if (remaining == 0) "Unlocked" else "$remaining more eligible order${if (remaining == 1) "" else "s"}",
                        fontSize = TazType.captionSize, color = TazColors.TextSecondary
                    )
                }
                Spacer(Modifier.width(TazSpace.sm))
                Text(
                    "${state.eligibleOrderCount.coerceAtMost(milestone.requiredOrders)} / ${milestone.requiredOrders}",
                    fontSize = TazType.titleSize, fontWeight = TazType.titleWeight, color = TazColors.Green
                )
            }
        }
    }
}
