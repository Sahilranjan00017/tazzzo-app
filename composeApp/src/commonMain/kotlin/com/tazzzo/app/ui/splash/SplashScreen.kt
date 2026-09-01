package com.tazzzo.app.ui.splash

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.tazzzo.app.LocalAppState
import com.tazzzo.app.Screen
import com.tazzzo.app.config.BrandCopy
import com.tazzzo.app.theme.TazColors
import com.tazzzo.app.theme.TazRadius
import com.tazzzo.app.theme.TazSpace
import com.tazzzo.app.theme.TazType
import com.tazzzo.app.ui.common.LogoImage
import kotlinx.coroutines.delay

/**
 * Brand moment, not a technical launch screen.
 *
 * Composition: a cream ground that warms into the softest brand tint at the
 * bottom, one very-low-opacity green disc bleeding off the top-right for depth,
 * a choreographed logo → tagline entrance, and a determinate progress track
 * that tells the customer the wait is finite. The cream ground and the logo
 * scale relationship are deliberately shared with the login badge so the two
 * screens read as one continuous moment.
 */
@Composable
fun SplashScreen() {
    val app = LocalAppState.current

    // Returning customers have seen the brand moment — get them in faster.
    val dwellMillis = if (app.isOnboarded) 900 else 1800

    // Choreography: the mark lands first, the words follow ~180ms later.
    val logoIn = remember { Animatable(0f) }
    val copyIn = remember { Animatable(0f) }
    var started by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        logoIn.animateTo(1f, tween(durationMillis = 600, easing = FastOutSlowInEasing))
    }
    LaunchedEffect(Unit) {
        delay(180)
        copyIn.animateTo(1f, tween(durationMillis = 420, easing = FastOutSlowInEasing))
    }
    LaunchedEffect(Unit) { started = true }

    // The track fills across exactly the time the customer actually waits.
    val progress by animateFloatAsState(
        targetValue = if (started) 1f else 0f,
        animationSpec = tween(durationMillis = dwellMillis, easing = LinearEasing),
        label = "splashProgress"
    )

    LaunchedEffect(Unit) {
        delay(if (app.isOnboarded) 900 else 1800)
        app.resetTo(if (app.isOnboarded) Screen.Home else Screen.Onboarding)
    }

    Box(
        Modifier
            .fillMaxSize()
            .background(
                // Brand-tinted, never a green wash: cream holds the top two
                // thirds, the tint only breathes in at the very bottom.
                Brush.verticalGradient(
                    0f to TazColors.Cream,
                    0.55f to TazColors.Cream,
                    1f to TazColors.GreenSoft
                )
            )
    ) {
        // --- single decorative element: a large disc bleeding off top-right ---
        Box(
            Modifier
                .align(Alignment.TopEnd)
                .offset(x = 112.dp, y = (-124).dp)
                .size(340.dp)
                .clip(CircleShape)
                .background(TazColors.Green.copy(alpha = 0.07f))
        )

        // --- centred brand lockup ---
        Column(
            modifier = Modifier
                .align(Alignment.Center)
                .padding(horizontal = TazSpace.xxxl),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            LogoImage(
                height = 84.dp,
                modifier = Modifier
                    .alpha(logoIn.value)
                    .scale(0.90f + 0.10f * logoIn.value)
            )
            Spacer(Modifier.height(TazSpace.lg))
            Text(
                BrandCopy.tagline,
                fontSize = TazType.titleSize,
                fontWeight = TazType.titleWeight,
                lineHeight = TazType.titleLine,
                letterSpacing = 0.2.sp,
                color = TazColors.TextSecondary,
                textAlign = TextAlign.Center,
                modifier = Modifier.alpha(copyIn.value)
            )
        }

        // --- intentional, determinate progress + config-sourced caption ---
        Column(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .navigationBarsPadding()
                .padding(bottom = TazSpace.xxxl, start = TazSpace.xxl, end = TazSpace.xxl)
                .alpha(copyIn.value),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Box(
                Modifier
                    .width(64.dp)
                    .height(3.dp)
                    .clip(TazRadius.pill)
                    .background(TazColors.SurfaceSunken)
            ) {
                Box(
                    Modifier
                        .fillMaxHeight()
                        .fillMaxWidth(progress.coerceIn(0.02f, 1f))
                        .clip(TazRadius.pill)
                        .background(TazColors.Green)
                )
            }
            Spacer(Modifier.height(TazSpace.md))
            Text(
                // Config-sourced positioning line — never hard-coded (BrandCopy, D6).
                BrandCopy.voiceTeaser + " · " + BrandCopy.voiceStatus,
                fontSize = TazType.captionSize,
                lineHeight = TazType.captionLine,
                color = TazColors.TextTertiary,
                textAlign = TextAlign.Center
            )
        }
    }
}
