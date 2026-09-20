package com.tazzzo.app.ui.interaction

import androidx.compose.animation.AnimatedContentTransitionScope
import androidx.compose.animation.ContentTransform
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.Easing
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.ui.unit.IntOffset
import com.tazzzo.app.NavDirection
import com.tazzzo.app.theme.TazMotion

/**
 * Tazzzo's navigation motion language.
 *
 * The app previously used ONE `Crossfade` for every screen change, so going
 * deeper into the catalogue and coming back out looked identical. Direction is
 * the cheapest spatial cue a phone has, and without it the customer has to read
 * the screen to work out where they are.
 *
 * The model is a horizontal stack:
 *  - **Forward** — the new screen enters from the right and the old one
 *    *parallaxes* a quarter-width to the left. The partial offset is what makes
 *    the outgoing screen read as "still there, underneath" rather than thrown
 *    away, and it costs nothing extra to animate.
 *  - **Backward** — exactly reversed, so the customer sees the previous screen
 *    slide back into place from under the one they are leaving.
 *  - **Replace** — a fade. Used for stack resets (splash → home, order placed →
 *    home) where there is no spatial relationship to express, and a slide would
 *    imply a "back" that does not exist.
 *
 * Easing is a standard decelerate: fast at the start so the screen responds to
 * the finger immediately, settling at the end. Nothing bounces — a commerce app
 * that overshoots reads as a toy.
 */
private val Decelerate: Easing = CubicBezierEasing(0.05f, 0.7f, 0.1f, 1f)

/** Fraction of the width the outgoing screen travels. */
private const val PARALLAX = 4

fun AnimatedContentTransitionScope<*>.tazNavTransition(
    direction: NavDirection
): ContentTransform {
    val spec = tween<IntOffset>(TazMotion.nav, easing = Decelerate)
    val fade = tween<Float>(TazMotion.nav, easing = Decelerate)

    return when (direction) {
        NavDirection.Forward ->
            (slideInHorizontally(spec) { full -> full } + fadeIn(fade)) togetherWith
                (slideOutHorizontally(spec) { full -> -full / PARALLAX } + fadeOut(fade))

        NavDirection.Backward ->
            (slideInHorizontally(spec) { full -> -full / PARALLAX } + fadeIn(fade)) togetherWith
                (slideOutHorizontally(spec) { full -> full } + fadeOut(fade))

        // No spatial story to tell; do not invent one.
        NavDirection.Replace ->
            fadeIn(tween(TazMotion.normal)) togetherWith fadeOut(tween(TazMotion.fast))
    }
}
