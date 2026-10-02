package com.tazzzo.app.ui.onboarding

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.tazzzo.app.LocalAppState
import com.tazzzo.app.theme.TazColors
import com.tazzzo.app.theme.TazIcons
import com.tazzzo.app.theme.TazRadius
import com.tazzzo.app.theme.TazSize
import com.tazzzo.app.theme.TazSpace
import com.tazzzo.app.theme.TazType
import com.tazzzo.app.ui.common.EditorialText
import com.tazzzo.app.ui.common.PhotoAnchor
import com.tazzzo.app.ui.common.PhotoBackdrop
import com.tazzzo.app.ui.common.PhotoPlaceholders
import com.tazzzo.app.ui.common.TextAction
import com.tazzzo.app.ui.common.VSpace
import com.tazzzo.app.ui.common.italic
import com.tazzzo.app.ui.common.plain
import com.tazzzo.app.ui.interaction.TazPress
import com.tazzzo.app.ui.interaction.tazPressable
import tazzzo.resources.Res
import tazzzo.resources.bg_auth_otp
import tazzzo.resources.bg_auth_phone

/** Customer copy for the two auth steps (UI Page references `Mobile Number` and `Auth Page`). */
object LoginCopy {
    const val PHONE_SUPPORT = "We’ll send you a 6-digit OTP\nto get started."
    const val OTP_SUPPORT = "We’ve sent a code to"
    const val LEGAL = "By continuing, you agree to our\nTerms of Service and Privacy Policy."
    const val SKIP = "Skip for now"
    const val DIAL_PREFIX = "+91"
}

/**
 * Login = the phone step and the OTP step on one editorial surface (a warm cream photo slot, headline in the plain
 * upper region, the form beneath). Auth is the unchanged shared [AuthFlow]: real phone + 6-digit OTP. Guest entry
 * ("Skip for now") stays supported but is a small tertiary action under the form.
 */
@Composable
fun LoginScreen() {
    val app = LocalAppState.current
    val flow = rememberAuthFlow(onDone = {
        app.requestGuidedTourIfFirstTime()
        app.goHome()
    })
    val guest = { app.requestGuidedTourIfFirstTime(); app.goHome() }

    BoxWithConstraints(Modifier.fillMaxSize()) {
        // Short screens (≈570dp tall) get a tighter headline block so the form starts above the fold.
        val compact = maxHeight < 700.dp
        // Reference: the headline starts ~25% down the frame; the top bar (back affordance) is part of that distance.
        val headlineTop = if (compact) HEADLINE_TOP_COMPACT else (maxHeight * HEADLINE_TOP_FRACTION - TazSize.topBarHeight).coerceAtLeast(HEADLINE_TOP_COMPACT)
        val headlineSize = if (compact) TazType.editorialTitleSize else TazType.editorialHeadlineSize
        val headlineLine = if (compact) TazType.editorialTitleLine else TazType.editorialHeadlineLine
        PhotoBackdrop(
            photo = if (flow.step == AuthStep.Otp) Res.drawable.bg_auth_otp else Res.drawable.bg_auth_phone,
            placeholder = PhotoPlaceholders.creamWall, anchor = PhotoAnchor.Bottom, modifier = Modifier.fillMaxSize(),
            // Short/wide frames crop the plate so the counter objects rise under the legal line and "Skip for now"; a soft
            // cream wash behind that band keeps them legible. Taller frames match the reference and need none.
            scrim = if (compact) Brush.verticalGradient(
                0.50f to Color.Transparent, 0.62f to TazColors.Cream.copy(alpha = 0.78f),
                0.86f to TazColors.Cream.copy(alpha = 0.78f), 1f to Color.Transparent
            ) else null
        )

        Column(
            Modifier.fillMaxSize().statusBarsPadding().imePadding().verticalScroll(rememberScrollState()),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Back (OTP step only): returns to the phone step, exactly what "Change number" did.
            Box(Modifier.fillMaxWidth().height(TazSize.topBarHeight)) {
                if (flow.step == AuthStep.Otp) {
                    Box(
                        Modifier.align(Alignment.CenterStart).padding(start = TazSpace.sm).size(TazSize.touchTarget)
                            .clip(TazRadius.pill)
                            .tazPressable(onClick = flow::changeNumber, pressScale = TazPress.compact)
                            .semantics { contentDescription = "Change number" },
                        contentAlignment = Alignment.Center
                    ) { Icon(TazIcons.Back, contentDescription = null, tint = TazColors.BrandEditorial) }
                }
            }
            VSpace(headlineTop)

            when (flow.step) {
                AuthStep.Phone -> {
                    EditorialText(listOf(plain("What’s\nyour "), italic("number?")), size = headlineSize, lineHeight = headlineLine, color = TazColors.BrandEditorial)
                    VSpace(TazSpace.lg)
                    EditorialText(listOf(plain(LoginCopy.PHONE_SUPPORT)), size = TazType.editorialSubSize, lineHeight = TazType.editorialSubLine, color = TazColors.TextSecondary)
                }
                AuthStep.Otp -> {
                    EditorialText(listOf(plain("Enter the\n"), italic("6-digit code")), size = headlineSize, lineHeight = headlineLine, color = TazColors.BrandEditorial)
                    VSpace(TazSpace.lg)
                    EditorialText(listOf(plain(LoginCopy.OTP_SUPPORT)), size = TazType.editorialSubSize, lineHeight = TazType.editorialSubLine, color = TazColors.TextSecondary)
                    EditorialText(listOf(plain(flow.phoneDisplay)), size = TazType.editorialSubSize, lineHeight = TazType.editorialSubLine, color = TazColors.BrandEditorial)
                }
            }
            VSpace(TazSpace.xxl)

            AuthEntry(flow = flow, phoneCta = "Continue", otpCta = "Verify", modifier = Modifier.widthIn(max = FORM_MAX_WIDTH).padding(horizontal = TazSpace.xxl))

            VSpace(TazSpace.md)
            Text(
                LoginCopy.LEGAL, fontSize = TazType.captionSize, lineHeight = TazType.captionLine, color = TazColors.TextTertiary,
                textAlign = TextAlign.Center, modifier = Modifier.padding(horizontal = TazSpace.xxxl)
            )
            // Guest entry: supported, deliberately quiet, never competing with Continue.
            if (flow.step == AuthStep.Phone) {
                VSpace(TazSpace.sm)
                TextAction(LoginCopy.SKIP, onClick = guest, color = TazColors.TextSecondary)
            }
            Spacer(Modifier.height(TazSpace.xxl).navigationBarsPadding())
        }
    }
}

/** The headline starts ~26% down the frame in the references (≈ 165/640 after the top bar). */
private const val HEADLINE_TOP_FRACTION = 0.21f
private val HEADLINE_TOP_COMPACT = 24.dp
private val FORM_MAX_WIDTH = 420.dp
