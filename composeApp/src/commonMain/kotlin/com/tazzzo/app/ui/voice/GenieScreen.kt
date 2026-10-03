package com.tazzzo.app.ui.voice

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.tazzzo.app.HomeTab
import com.tazzzo.app.LocalAppState
import com.tazzzo.app.theme.MotionSettings
import com.tazzzo.app.theme.TazColors
import com.tazzzo.app.theme.TazIcons
import com.tazzzo.app.theme.TazMotion
import com.tazzzo.app.theme.TazRadius
import com.tazzzo.app.theme.TazSpace
import com.tazzzo.app.theme.TazType
import com.tazzzo.app.ui.checkout.Header
import com.tazzzo.app.ui.common.EditorialText
import com.tazzzo.app.ui.common.TazIcon
import com.tazzzo.app.ui.common.TazzzoPrimaryButton
import com.tazzzo.app.ui.common.italic
import com.tazzzo.app.ui.common.plain

/*
 * Tazzzo Genie (UI-08). Capability audit: the app has no speech capture, no transcription, no voice-to-order mapping and
 * no microphone permission on either platform; the only voice UI was a demo "coming soon" sheet on MOCK surfaces. So
 * this is the truthful Genie: an editorial identity, a decorative (non-interactive) mic mark, and the status
 * "Voice ordering is coming soon". There is no tap-to-speak control, because nothing would listen.
 */

object GenieCopy {
    const val TITLE = "Tazzzo Genie"
    const val STATUS = "Voice ordering is coming soon"
    const val HEADLINE_PLAIN = "Say your list. "
    const val HEADLINE_ITALIC = "We'll build the cart."
    const val BODY = "When Genie is ready, you'll speak your groceries and review the cart before anything is ordered. Nothing is placed without you."
    const val CTA = "Start shopping"
    const val MARK_DESCRIPTION = "Microphone, decorative"
}

@Composable
fun GenieScreen() {
    val app = LocalAppState.current
    GenieLayout(onBack = { app.back() }, onShop = { app.homeTab = HomeTab.SHOP; app.goHome() })
}

@Composable
fun GenieLayout(onBack: () -> Unit, onShop: () -> Unit) {
    Column(Modifier.fillMaxSize().background(TazColors.Cream).testTag("genie")) {
        Header(GenieCopy.TITLE, onBack = onBack)
        Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = TazSpace.xl), horizontalAlignment = Alignment.CenterHorizontally) {
            Spacer(Modifier.height(TazSpace.xxxl))
            GenieMark()
            Spacer(Modifier.height(TazSpace.xxl))
            Box(Modifier.clip(TazRadius.pill).background(TazColors.GreenSoft).padding(horizontal = TazSpace.md, vertical = TazSpace.xs)) {
                Text(GenieCopy.STATUS.uppercase(), fontSize = TazType.microSize, fontWeight = FontWeight.SemiBold, letterSpacing = 1.2.sp(), color = TazColors.BrandEditorial)
            }
            Spacer(Modifier.height(TazSpace.lg))
            EditorialText(listOf(plain(GenieCopy.HEADLINE_PLAIN), italic(GenieCopy.HEADLINE_ITALIC)), size = TazType.editorialTitleSize, lineHeight = TazType.editorialTitleLine, color = TazColors.TextPrimary)
            Spacer(Modifier.height(TazSpace.md))
            Text(GenieCopy.BODY, fontSize = TazType.bodySize, lineHeight = TazType.bodyLine, color = TazColors.TextSecondary, textAlign = TextAlign.Center)
            Spacer(Modifier.height(TazSpace.xxxl))
            TazzzoPrimaryButton(GenieCopy.CTA, onClick = onShop, modifier = Modifier.fillMaxWidth().testTag("genieShop"))
            Spacer(Modifier.navigationBarsPadding().height(TazSpace.xxl))
        }
    }
}

/** A calm, breathing mic mark. Decorative only: it is not a button and announces itself as such. Still under reduce-motion. */
@Composable
private fun GenieMark() {
    val scale: Float = if (MotionSettings.ambientEnabled) {
        val t = rememberInfiniteTransition(label = "genie")
        val v by t.animateFloat(1f, 1.06f, infiniteRepeatable(tween(TazMotion.ambient * 2), RepeatMode.Reverse), label = "genieScale")
        v
    } else 1f
    Box(Modifier.size(132.dp).clearAndSetSemantics { }, contentAlignment = Alignment.Center) {
        Box(Modifier.size(132.dp).scale(scale).clip(CircleShape).background(TazColors.GreenSoft))
        Box(Modifier.size(96.dp).clip(CircleShape).background(TazColors.CreamStrong))
        Box(Modifier.size(64.dp).clip(CircleShape).background(TazColors.BrandEditorial), contentAlignment = Alignment.Center) {
            TazIcon(TazIcons.Mic, null, size = 30.dp, tint = TazColors.EditorialOnDark)
        }
    }
}

private fun Double.sp() = androidx.compose.ui.unit.TextUnit(this.toFloat(), androidx.compose.ui.unit.TextUnitType.Sp)
