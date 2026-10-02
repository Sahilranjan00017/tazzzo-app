package com.tazzzo.app.ui.onboarding

import androidx.compose.animation.Crossfade
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.ime
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.relocation.BringIntoViewRequester
import androidx.compose.foundation.relocation.bringIntoViewRequester
import androidx.compose.runtime.getValue
import kotlinx.coroutines.delay
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextFieldColors
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.tazzzo.app.LocalAppState
import com.tazzzo.app.data.auth.AuthFailure
import com.tazzzo.app.data.repository.ServiceLocator
import com.tazzzo.app.theme.TazColors
import com.tazzzo.app.theme.TazRadius
import com.tazzzo.app.theme.TazSpace
import com.tazzzo.app.theme.TazType
import com.tazzzo.app.theme.tazFontFamily
import com.tazzzo.app.ui.common.EDITORIAL_BUTTON_HEIGHT
import com.tazzzo.app.ui.common.TazzzoPrimaryButton
import com.tazzzo.app.ui.common.TextAction
import androidx.compose.runtime.rememberCoroutineScope

/**
 * Creates the shared [AuthFlow] bound to the real [ServiceLocator.auth]. On
 * success it marks the app signed in and runs [onDone] (tour + Home).
 */
@Composable
fun rememberAuthFlow(onDone: () -> Unit): AuthFlow {
    val app = LocalAppState.current
    val scope = rememberCoroutineScope()
    return remember {
        AuthFlow(ServiceLocator.auth, scope) {
            app.onSignedIn()
            onDone()
        }
    }
}

/**
 * The phone and OTP steps — PRESENTATION only (UI Page references). Every behaviour is the unchanged [AuthFlow]:
 * sanitising, submit, verify, resend timer, failures.
 */
@Composable
fun AuthEntry(flow: AuthFlow, phoneCta: String, otpCta: String, modifier: Modifier = Modifier) {
    Crossfade(targetState = flow.step, modifier = modifier.fillMaxWidth()) { step ->
        when (step) {
            AuthStep.Phone -> PhoneStep(flow, phoneCta)
            AuthStep.Otp -> OtpStep(flow, otpCta)
        }
    }
}

/** A static "+91" (no picker exists, so no chevron) separated from the digits by a hairline. */
@Composable
internal fun DialPrefix() {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(LoginCopy.DIAL_PREFIX, fontSize = TazType.titleSize, fontWeight = FontWeight.Medium, color = TazColors.BrandEditorial)
        Spacer(Modifier.width(TazSpace.md))
        Box(Modifier.width(1.dp).height(24.dp).background(TazColors.BorderStrong))
        Spacer(Modifier.width(TazSpace.md))
    }
}

@OptIn(ExperimentalFoundationApi::class, ExperimentalLayoutApi::class)
@Composable
private fun PhoneStep(flow: AuthFlow, cta: String) {
    // When the field takes focus (keyboard up) the CTA is scrolled into view, so Continue is never hidden behind the IME
    // on a short screen (320x569dp class).
    val interaction = remember { MutableInteractionSource() }
    val focused by interaction.collectIsFocusedAsState()
    val imeVisible = WindowInsets.ime.getBottom(LocalDensity.current) > 0        // common on every target (isImeVisible is Android-only)
    val ctaIntoView = remember { BringIntoViewRequester() }
    // Keyed on the IME actually being shown (not just focus), so the scroll range already includes the keyboard inset.
    LaunchedEffect(focused, imeVisible) { if (focused && imeVisible) { delay(120); runCatching { ctaIntoView.bringIntoView() } } }
    Column(Modifier.fillMaxWidth()) {
        // The pill input: cream fill, hairline border, static dial prefix, numeric keyboard.
        Row(
            Modifier.fillMaxWidth().height(EDITORIAL_BUTTON_HEIGHT).clip(TazRadius.pill)
                .background(TazColors.Surface.copy(alpha = 0.72f))
                .border(BorderStroke(1.dp, TazColors.BorderStrong), TazRadius.pill)
                .padding(horizontal = TazSpace.xl),
            verticalAlignment = Alignment.CenterVertically
        ) {
            DialPrefix()
            BasicTextField(
                value = flow.phoneInput,
                onValueChange = flow::onPhoneChanged,
                singleLine = true,
                interactionSource = interaction,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
                cursorBrush = SolidColor(TazColors.BrandEditorial),
                textStyle = TextStyle(fontFamily = tazFontFamily(), fontSize = TazType.titleSize, fontWeight = FontWeight.Medium, color = TazColors.TextPrimary),
                modifier = Modifier.weight(1f).semantics { contentDescription = "Mobile number" },
                decorationBox = { inner ->
                    Box(contentAlignment = Alignment.CenterStart) {
                        if (flow.phoneInput.isEmpty()) Text("98765 43210", fontSize = TazType.titleSize, color = TazColors.TextDisabled)
                        inner()
                    }
                }
            )
        }
        FailureText(flow.failure)
        Spacer(Modifier.height(TazSpace.md))
        TazzzoPrimaryButton(
            text = cta, onClick = flow::submitPhone, modifier = Modifier.fillMaxWidth().bringIntoViewRequester(ctaIntoView),
            enabled = flow.canContinue, loading = flow.requesting
        )
    }
}

@Composable
private fun OtpStep(flow: AuthFlow, cta: String) {
    Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
        OtpCells(value = flow.otpInput, onValueChange = flow::onOtpChanged, enabled = !flow.verifying)
        FailureText(flow.failure)
        Spacer(Modifier.height(TazSpace.lg))
        ResendRow(flow)
        Spacer(Modifier.height(TazSpace.lg))
        TazzzoPrimaryButton(
            text = cta, onClick = flow::submitOtp, modifier = Modifier.fillMaxWidth(),
            enabled = flow.canVerify || flow.verifying, loading = flow.verifying
        )
    }
}

@Composable
private fun ResendRow(flow: AuthFlow) {
    Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
        Text("Didn’t receive the code?", fontSize = TazType.captionSize, color = TazColors.TextSecondary)
        if (flow.resendRemaining > 0) {
            val s = flow.resendRemaining
            Text(
                "Resend in ${(s / 60).toString().padStart(2, '0')}:${(s % 60).toString().padStart(2, '0')}",
                fontSize = TazType.captionSize, fontWeight = FontWeight.Medium, color = TazColors.BrandEditorial,
                textDecoration = androidx.compose.ui.text.style.TextDecoration.Underline,
                modifier = Modifier.padding(top = TazSpace.xs)
            )
        } else {
            TextAction(
                if (flow.requesting) "Sending…" else "Resend code", onClick = flow::resend,
                color = TazColors.BrandEditorial, underline = true, enabled = flow.canResend
            )
        }
    }
}

@Composable
private fun FailureText(failure: AuthFailure?) {
    if (failure == null) return
    Spacer(Modifier.height(TazSpace.sm))
    Text(
        failure.message, fontSize = TazType.captionSize, fontWeight = FontWeight.SemiBold, color = TazColors.Danger,
        textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth()
    )
}

/**
 * Six cells over one hidden text field. A single real field gives correct numeric keyboard, focus, backspace,
 * paste and IME behaviour for free; the cells only draw its content. Paste of more than 6 characters is reduced
 * to the first six digits by the flow. Cells share the available width (weights), so the row fits a 320dp screen.
 */
@Composable
fun OtpCells(value: String, onValueChange: (String) -> Unit, enabled: Boolean, modifier: Modifier = Modifier) {
    val focus = remember { FocusRequester() }
    LaunchedEffect(Unit) { runCatching { focus.requestFocus() } }
    BasicTextField(
        value = value,
        onValueChange = onValueChange,
        enabled = enabled,
        singleLine = true,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
        cursorBrush = SolidColor(Color.Transparent),
        textStyle = TextStyle(color = Color.Transparent),
        modifier = modifier
            .focusRequester(focus)
            .semantics { contentDescription = "One-time code, ${value.length} of ${AuthFlow.OTP_LENGTH} digits entered" },
        decorationBox = { inner ->
            Box {
                Box(Modifier.size(1.dp).alpha(0f)) { inner() }
                Row(Modifier.fillMaxWidth().widthIn(max = 360.dp), horizontalArrangement = Arrangement.spacedBy(TazSpace.sm)) {
                    repeat(AuthFlow.OTP_LENGTH) { i ->
                        val active = enabled && i == value.length.coerceAtMost(AuthFlow.OTP_LENGTH - 1)
                        val shape = RoundedCornerShape(12.dp)
                        Box(
                            Modifier.weight(1f).aspectRatio(0.86f).clip(shape).background(TazColors.CreamStrong)
                                .border(BorderStroke(if (active) 1.5.dp else 1.dp, if (active) TazColors.BrandEditorial else TazColors.BorderStrong), shape),
                            contentAlignment = Alignment.Center
                        ) {
                            val digit = value.getOrNull(i)?.toString()
                            if (digit != null) {
                                Text(digit, fontSize = 22.sp, fontWeight = FontWeight.SemiBold, color = TazColors.TextPrimary)
                            } else if (active) {
                                Box(Modifier.width(1.5.dp).height(22.dp).background(TazColors.BrandEditorial))   // caret
                            }
                        }
                    }
                }
            }
        }
    )
}

/** Outlined-field colours shared by the address, cart and master-list forms (unchanged; lived in the old onboarding file). */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun tazFieldColors(): TextFieldColors = OutlinedTextFieldDefaults.colors(
    focusedBorderColor = TazColors.Green,
    unfocusedBorderColor = TazColors.BorderStrong,
    cursorColor = TazColors.Green,
    focusedContainerColor = TazColors.Surface,
    unfocusedContainerColor = TazColors.Surface,
    focusedTextColor = TazColors.TextPrimary,
    unfocusedTextColor = TazColors.TextPrimary
)
