package com.tazzzo.app.ui.splash

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.scale
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.tazzzo.app.LocalAppState
import com.tazzzo.app.Screen
import com.tazzzo.app.config.BrandCopy
import com.tazzzo.app.theme.TazColors
import com.tazzzo.app.theme.TazSpace
import com.tazzzo.app.theme.TazType
import com.tazzzo.app.ui.common.PhotoBackdrop
import com.tazzzo.app.ui.common.PhotoPlaceholders
import com.tazzzo.app.ui.common.EditorialText
import com.tazzzo.app.ui.common.TazzzoWordmark
import com.tazzzo.app.ui.common.VSpace
import com.tazzzo.app.ui.common.italic
import kotlinx.coroutines.delay
import tazzzo.resources.Res
import tazzzo.resources.bg_splash_grocery

/**
 * Brand moment (UI Page reference `Splash`): a bright full-bleed produce plate, the vector wordmark centred with the
 * canonical tagline beneath it. No progress track, no feature teaser — the reference has neither.
 *
 * Routing is unchanged: returning (onboarded) customers go straight Home after a shorter dwell; a new device goes
 * to the Showcase carousel. Showing Splash never marks the device onboarded.
 */
@Composable
fun SplashScreen() {
    val app = LocalAppState.current
    val dwellMillis = if (app.isOnboarded) 900 else 1800

    val logoIn = remember { Animatable(0f) }
    val copyIn = remember { Animatable(0f) }
    LaunchedEffect(Unit) { logoIn.animateTo(1f, tween(durationMillis = 600, easing = FastOutSlowInEasing)) }
    LaunchedEffect(Unit) { delay(180); copyIn.animateTo(1f, tween(durationMillis = 420, easing = FastOutSlowInEasing)) }
    LaunchedEffect(Unit) {
        delay(dwellMillis.toLong())
        app.resetTo(if (app.isOnboarded) Screen.Home else Screen.Onboarding)
    }

    BoxWithConstraints(Modifier.fillMaxSize()) {
        PhotoBackdrop(photo = Res.drawable.bg_splash_grocery, placeholder = PhotoPlaceholders.studio, modifier = Modifier.fillMaxSize())
        // The wordmark spans ~49% of the frame width in the reference (192/390dp), capped so tablets don't shout.
        val wordmarkWidth = (maxWidth * 0.49f).coerceAtMost(240.dp)
        // The lockup sits a little above true centre (reference: wordmark centre at ~43% of the height).
        Column(
            Modifier.align(Alignment.Center).padding(horizontal = TazSpace.xxxl).offset(y = -(maxHeight * 0.06f)),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            TazzzoWordmark(width = wordmarkWidth, modifier = Modifier.alpha(logoIn.value).scale(0.94f + 0.06f * logoIn.value))
            VSpace(TazSpace.md)
            EditorialText(
                listOf(italic(BrandCopy.tagline)), size = TazType.taglineSize, lineHeight = 22.sp,
                letterSpacing = TazType.taglineTracking, color = TazColors.TextSecondary,
                modifier = Modifier.alpha(copyIn.value)
            )
        }
    }
}
