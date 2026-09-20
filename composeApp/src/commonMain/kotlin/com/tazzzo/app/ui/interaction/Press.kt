package com.tazzzo.app.ui.interaction

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.semantics.Role
import com.tazzzo.app.theme.TazMotion

/**
 * Tazzzo's press language.
 *
 * Why this exists: the app had 65 `clickable` call sites and ZERO designed
 * pressed states. Feedback was whatever Material's default ripple happened to
 * do, and on the most-tapped surfaces of all — the product card and the home
 * banner — it was explicitly switched off with `indication = null`. Tapping a
 * product produced no response at all before the screen changed.
 *
 * The fix is deliberately NOT "turn the ripple back on". A rectangular ripple
 * bleeding past a 14dp card corner is why it was disabled in the first place.
 * Instead every interactive surface uses the same two-part response:
 *
 *   1. an immediate, shape-respecting **scale** — the element yields under the
 *      finger and springs back, so the acknowledgement is instant and cannot
 *      leak outside the component's own geometry;
 *   2. an optional **press tint** clipped to the component's own shape, for
 *      large surfaces where scale alone is too subtle to read.
 *
 * Timing is [TazMotion.fast] (150 ms) in and out. Press feedback must be
 * faster than thought; anything slower reads as lag, not as response.
 *
 * Haptics fire on *press*, not on click, because the hand should be answered
 * at the moment of contact — not after the handler has run.
 */

/** How far a surface yields under a finger. Smaller elements move less. */
object TazPress {
    /** Cards, tiles, banners — large surfaces. */
    const val card = 0.975f
    /** Buttons and pills. */
    const val control = 0.96f
    /** Icon buttons and small chips, where a big scale looks nervous. */
    const val compact = 0.92f
    /** Rows in a list — motion here is distracting, so tint carries it. */
    const val row = 1f
}

/**
 * The standard interactive surface.
 *
 * Replaces bare `clickable`. Gives the element a pressed state, an optional
 * haptic, and correct accessibility [role] semantics.
 *
 * @param pressScale how far the surface yields; see [TazPress].
 * @param shape when set, a press tint is drawn clipped to this shape. Use for
 *   large surfaces (cards, rows) where scale alone under-reads.
 * @param haptic fires on press. Null for surfaces tapped constantly, where
 *   feedback would become noise.
 */
@Composable
fun Modifier.tazPressable(
    onClick: () -> Unit,
    enabled: Boolean = true,
    pressScale: Float = TazPress.control,
    shape: Shape? = null,
    haptic: TazHaptic? = null,
    role: Role? = Role.Button,
    /**
     * When non-null the surface is a SELECTION, not a plain action: the state
     * is published to the accessibility tree so a screen reader can say which
     * tab, slot, payment method or filter is currently chosen.
     */
    selected: Boolean? = null,
    pressTint: Color = Color.Black.copy(alpha = 0.055f)
): Modifier {
    val haptics = rememberHaptics()
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()

    // Answer the finger at the moment of contact, not after onClick runs.
    androidx.compose.runtime.LaunchedEffect(pressed) {
        if (pressed && enabled && haptic != null) haptics.perform(haptic)
    }

    val scale by animateFloatAsState(
        targetValue = if (pressed && enabled && pressScale != 1f) pressScale else 1f,
        animationSpec = tween(TazMotion.fast),
        label = "pressScale"
    )
    val tintAlpha by animateFloatAsState(
        targetValue = if (pressed && enabled) 1f else 0f,
        animationSpec = tween(TazMotion.fast),
        label = "pressTint"
    )

    var m = this
    if (pressScale != 1f) {
        m = m.graphicsLayer { scaleX = scale; scaleY = scale }
    }
    // A selectable thing must use `selectable`, not `clickable` + a separate
    // semantics block. Verified on device: the old shape produced TWO nodes —
    // an outer clickable one and an inner merged one carrying the label — so
    // `selected` never reached the accessibility node and every tab reported
    // selected=false to the platform. `selectable` puts the state, the role and
    // the action on one node.
    m = if (selected != null) {
        m.selectable(
            selected = selected,
            interactionSource = interaction,
            indication = null,      // replaced by the response above, not removed
            enabled = enabled,
            role = role,
            onClick = onClick
        )
    } else {
        m.clickable(
            interactionSource = interaction,
            indication = null,      // replaced by the response above, not removed
            enabled = enabled,
            role = role,
            onClick = onClick
        )
    }
    if (shape != null && tintAlpha > 0f) {
        m = m.clip(shape).background(pressTint.copy(alpha = pressTint.alpha * tintAlpha))
    }
    return m
}

/**
 * Press response for a card: gentle scale plus a shape-clipped tint.
 * The most repeated interaction in the app, so it gets its own entry point.
 */
@Composable
fun Modifier.tazPressableCard(
    onClick: () -> Unit,
    shape: Shape,
    enabled: Boolean = true,
    haptic: TazHaptic? = null
): Modifier = tazPressable(
    onClick = onClick,
    enabled = enabled,
    pressScale = TazPress.card,
    shape = shape,
    haptic = haptic
)

/**
 * Press response for an icon button or small control.
 *
 * [minTouchTarget] is applied by the CALLER via `defaultMinSize`; this helper
 * does not silently change layout. The 44dp minimum is a design-spec rule and
 * belongs where the size is declared, not hidden inside a press modifier.
 */
@Composable
fun Modifier.tazPressableIcon(
    onClick: () -> Unit,
    enabled: Boolean = true,
    haptic: TazHaptic? = null,
    role: Role? = Role.Button
): Modifier = tazPressable(
    onClick = onClick,
    enabled = enabled,
    pressScale = TazPress.compact,
    haptic = haptic,
    role = role
)
