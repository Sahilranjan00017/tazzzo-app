package com.tazzzo.app.ui.guided

import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateRectAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.clickable
import androidx.compose.foundation.background
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.tazzzo.app.LocalAppState
import com.tazzzo.app.theme.TazColors
import com.tazzzo.app.theme.TazIcons
import com.tazzzo.app.theme.TazRadius
import com.tazzzo.app.theme.TazSize
import com.tazzzo.app.theme.TazSpace
import com.tazzzo.app.theme.TazType
import com.tazzzo.app.ui.common.PillButton
import com.tazzzo.app.ui.common.TazIcon
import com.tazzzo.app.config.AppConfig

// --- coach-mark card geometry ------------------------------------------------
// Derived from the spacing scale so the card stays inside the token system.
private val CardPadding = TazSpace.xxl + TazSpace.xs      // 28
private val CardCorner = 22.dp                            // sheet-scale radius, all four corners
private val BrandRailWidth = TazSpace.huge                // 48
private val BrandRailHeight = TazSpace.xs                 // 4
private val MarkOuter = 64.dp
private val MarkInner = 44.dp
private val DotSize = 6.dp
private val DotActiveWidth = TazSpace.xl                  // 20

/**
 * One step of the guided tour. [key] refers to an entry in app.guidedTargets
 * (null = centered card). [icon] is the card's primary visual — the tour draws
 * from the one UI icon family; [emoji] is retained on the model but is no
 * longer rendered as the card's hero.
 */
private data class GuideStep(
    val key: String?,
    val emoji: String,
    val icon: ImageVector,
    val title: String,
    val body: String
)

private val guideSteps: List<GuideStep> get() = listOf(
    GuideStep(
        key = "search", emoji = "🔍", icon = TazIcons.Search,
        title = "Find anything fast",
        body = "Type or speak — milk, atta, sabun. Hindi works too."
    ),
    GuideStep(
        key = "coins", emoji = "🪙", icon = TazIcons.Coin,
        title = "Tazzzo Coins",
        body = AppConfig.coins.earnCopy + " " + AppConfig.coins.valueCopy + " at checkout."
    ),
    GuideStep(
        key = "bottomnav", emoji = "🛒", icon = TazIcons.Cart,
        title = "Shop your way",
        body = "Browse aisles, reorder in one tap, and track everything from Orders."
    )
)

/**
 * Anchored spotlight coach-mark tour (Blinkit/Zepto style first-run).
 * Each step spotlights a real on-screen element via app.guidedTargets; steps without
 * a target (or with a missing rect) show a centered card with no hole and no arrow.
 * Tapping the scrim, or Next, advances; "Skip tour" ends immediately.
 */
@Composable
fun GuidedJourneyOverlay(onDone: () -> Unit) {
    val app = LocalAppState.current
    var step by remember { mutableStateOf(0) }

    // Demo autopilot: auto-advance the tour so demos/recordings walk every step.
    if (com.tazzzo.app.isDemoTourEnabled()) {
        LaunchedEffect(Unit) {
            while (true) {
                kotlinx.coroutines.delay(2_000)
                if (step < guideSteps.lastIndex) step++ else { onDone(); break }
            }
        }
    }

    fun next() {
        if (step < guideSteps.lastIndex) step++ else onDone()
    }

    BoxWithConstraints(Modifier.fillMaxSize()) {
        val rootHeightPx = constraints.maxHeight.toFloat()
        val arrowSizePx = with(LocalDensity.current) { 38.dp.toPx() }

        // --- resolve the current anchor rect (root/window px coords) ---
        val current = guideSteps[step]
        val targetRect: Rect? = current.key?.let { app.guidedTargets[it] }
        val paddedTarget: Rect? = targetRect?.let {
            Rect(it.left - 10f, it.top - 10f, it.right + 10f, it.bottom + 10f)
        }

        // Remember the last non-null rect so the spotlight animates between anchors.
        var lastPadded by remember { mutableStateOf<Rect?>(null) }
        if (paddedTarget != null && paddedTarget != lastPadded) lastPadded = paddedTarget

        val animatedRect by animateRectAsState(
            targetValue = paddedTarget ?: lastPadded ?: Rect(0f, 0f, 0f, 0f),
            animationSpec = tween(durationMillis = 350),
            label = "spotlightRect"
        )
        val hole: Rect? = if (paddedTarget != null) animatedRect else null
        val holeInTopHalf = hole != null && hole.center.y < rootHeightPx / 2f

        // --- dim scrim with punched-out spotlight + crisp white ring; taps advance ---
        Box(
            Modifier
                .fillMaxSize()
                .graphicsLayer { compositingStrategy = CompositingStrategy.Offscreen }
                .drawBehind {
                    drawRect(TazColors.Scrim)
                    if (hole != null) {
                        drawRoundRect(
                            color = Color.Transparent,
                            topLeft = Offset(hole.left, hole.top),
                            size = Size(hole.width, hole.height),
                            cornerRadius = CornerRadius(22f, 22f),
                            blendMode = BlendMode.Clear
                        )
                        drawRoundRect(
                            color = Color.White.copy(alpha = 0.9f),
                            topLeft = Offset(hole.left, hole.top),
                            size = Size(hole.width, hole.height),
                            cornerRadius = CornerRadius(22f, 22f),
                            style = Stroke(width = 2f)
                        )
                    }
                }
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null
                ) { next() }
        )

        // --- bobbing arrow pointing at the spotlight ---
        if (hole != null) {
            val bob by rememberInfiniteTransition(label = "arrowBob").animateFloat(
                initialValue = 0f,
                targetValue = 14f,
                animationSpec = infiniteRepeatable(tween(500), RepeatMode.Reverse),
                label = "bob"
            )
            Box(
                Modifier
                    .offset {
                        val x = (hole.center.x - arrowSizePx / 2f).toInt()
                        val y = if (holeInTopHalf) {
                            (hole.bottom + 12f + bob).toInt()
                        } else {
                            (hole.top - arrowSizePx - 12f - bob).toInt()
                        }
                        IntOffset(x, y)
                    }
                    .size(38.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(if (holeInTopHalf) "👆" else "👇", fontSize = 30.sp)
            }
        }

        // --- coach-mark card, placed on the opposite half from the hole ---
        val cardAlignment = when {
            hole == null -> Alignment.Center
            holeInTopHalf -> Alignment.BottomCenter
            else -> Alignment.TopCenter
        }
        val cardEdgePadding = when {
            hole == null -> Modifier
            holeInTopHalf -> Modifier.navigationBarsPadding().padding(bottom = 28.dp)
            else -> Modifier.statusBarsPadding().padding(top = 64.dp)
        }
        Column(
            Modifier
                .align(cardAlignment)
                .then(cardEdgePadding)
                .padding(horizontal = TazSpace.xl)
                .fillMaxWidth()
                .shadow(
                    20.dp, RoundedCornerShape(CardCorner),
                    spotColor = Color.Black.copy(alpha = 0.28f)
                )
                .clip(RoundedCornerShape(CardCorner))
                .background(TazColors.Surface)
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null
                ) { /* consume — card taps don't advance */ }
                .padding(CardPadding)
        ) {
            Crossfade(targetState = step, label = "stepCard") { s ->
                val g = guideSteps[s]
                val isLast = s == guideSteps.lastIndex
                Column(
                    Modifier.fillMaxWidth(),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    // Brand rail — a small, deliberate signature so the card reads
                    // as Tazzzo rather than as a generic white tooltip panel.
                    Box(
                        Modifier
                            .size(width = BrandRailWidth, height = BrandRailHeight)
                            .clip(TazRadius.pill)
                            .background(TazColors.Green)
                    )
                    Spacer(Modifier.height(TazSpace.lg))
                    Text(
                        "STEP ${s + 1} OF ${guideSteps.size}",
                        fontSize = TazType.microSize,
                        lineHeight = TazType.microLine,
                        letterSpacing = TazType.labelTracking,
                        fontWeight = TazType.microWeight,
                        color = TazColors.TextTertiary
                    )
                    Spacer(Modifier.height(TazSpace.md))
                    // Layered mark: tinted outer ring, clean inner disc. Reads as
                    // crafted where a single filled circle read as a blob.
                    Box(
                        Modifier.size(MarkOuter).clip(CircleShape).background(TazColors.GreenSoft),
                        contentAlignment = Alignment.Center
                    ) {
                        Box(
                            Modifier.size(MarkInner).clip(CircleShape)
                                .background(TazColors.Surface),
                            contentAlignment = Alignment.Center
                        ) {
                            TazIcon(
                                icon = g.icon,
                                contentDescription = null,
                                size = TazSize.iconLg,
                                tint = TazColors.Green
                            )
                        }
                    }
                    Spacer(Modifier.height(TazSpace.md))
                    Text(
                        g.title,
                        fontSize = TazType.h1Size,
                        fontWeight = TazType.h1Weight,
                        lineHeight = TazType.h1Line,
                        color = TazColors.TextPrimary,
                        textAlign = TextAlign.Center
                    )
                    Spacer(Modifier.height(TazSpace.sm))
                    Text(
                        g.body,
                        fontSize = TazType.bodySize,
                        lineHeight = TazType.bodyLine,
                        color = TazColors.TextSecondary,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.padding(horizontal = TazSpace.sm)
                    )
                    Spacer(Modifier.height(TazSpace.xl))
                    // Footer reads [progress] … [Skip] [Next] — one calm row
                    // instead of a centred dot cluster stacked above the controls.
                    Row(
                        Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(TazSpace.xs),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            guideSteps.indices.forEach { i ->
                                if (i == s) {
                                    Box(
                                        Modifier
                                            .size(width = DotActiveWidth, height = DotSize)
                                            .clip(TazRadius.pill)
                                            .background(TazColors.Green)
                                    )
                                } else {
                                    Box(
                                        Modifier
                                            .size(DotSize)
                                            .clip(CircleShape)
                                            .background(TazColors.CardBorder)
                                    )
                                }
                            }
                        }
                        Spacer(Modifier.weight(1f))
                        // Default ripple kept: skip needs visible press feedback.
                        Text(
                            "Skip tour",
                            fontSize = TazType.bodySize,
                            fontWeight = FontWeight.SemiBold,
                            color = TazColors.TextSecondary,
                            modifier = Modifier
                                .defaultMinSize(minHeight = 48.dp)
                                .clickable { onDone() }
                                .padding(vertical = TazSpace.sm, horizontal = TazSpace.sm)
                        )
                        Spacer(Modifier.width(TazSpace.sm))
                        PillButton(
                            text = if (isLast) "Start Shopping" else "Next",
                            onClick = { next() }
                        )
                    }
                }
            }
        }
    }
}
