package com.tazzzo.app.ui.onboarding

import androidx.compose.animation.Crossfade
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
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
import androidx.compose.foundation.layout.padding
import com.tazzzo.app.LocalAppState
import com.tazzzo.app.data.auth.AuthFailure
import com.tazzzo.app.data.repository.ServiceLocator
import com.tazzzo.app.theme.TazColors
import com.tazzzo.app.theme.TazRadius
import com.tazzzo.app.theme.TazSize
import com.tazzzo.app.theme.TazSpace
import com.tazzzo.app.theme.TazType
import com.tazzzo.app.ui.common.PillButton
import com.tazzzo.app.ui.interaction.TazHaptic
import com.tazzzo.app.ui.interaction.TazPress
import com.tazzzo.app.ui.interaction.tazPressable

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
 * The phone and OTP steps, functional structure only. Final visual treatment
 * (TZ-LOGIN-001 / TZ-OTP-001) is PR-03B and changes presentation, not this logic.
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

@Composable
private fun PhoneStep(flow: AuthFlow, cta: String) {
    Column(Modifier.fillMaxWidth()) {
        OutlinedTextField(
            value = flow.phoneInput,
            onValueChange = flow::onPhoneChanged,
            modifier = Modifier.fillMaxWidth().height(TazSize.inputHeight),
            singleLine = true,
            shape = TazRadius.card,
            leadingIcon = { DialPrefix() },
            placeholder = {
                Text("Enter mobile number", fontSize = TazType.bodySize, color = TazColors.TextTertiary)
            },
            textStyle = TextStyle(
                fontSize = TazType.titleSize, fontWeight = TazType.titleWeight, color = TazColors.TextPrimary
            ),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
            colors = tazFieldColors()
        )
        FailureText(flow.failure)
        Spacer(Modifier.height(TazSpace.md))
        PillButton(
            text = cta,
            loading = flow.requesting,
            loadingText = "Sending code…",
            onClick = flow::submitPhone,
            modifier = Modifier.fillMaxWidth(),
            enabled = flow.canContinue,
            disabledHint = "Enter your 10-digit mobile number to continue"
        )
    }
}

@Composable
private fun OtpStep(flow: AuthFlow, cta: String) {
    Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.Center) {
            Text(
                "Enter the 6-digit code sent to ${flow.phoneDisplay}",
                fontSize = TazType.captionSize,
                color = TazColors.TextSecondary,
                textAlign = TextAlign.Center,
                modifier = Modifier.weight(1f, fill = false)
            )
        }
        Box(
            Modifier
                .defaultMinSize(minHeight = TazSize.touchTarget)
                .clip(TazRadius.chip)
                .tazPressable(onClick = flow::changeNumber, pressScale = TazPress.compact),
            contentAlignment = Alignment.Center
        ) {
            Text("Change number", fontSize = TazType.captionSize, fontWeight = FontWeight.SemiBold, color = TazColors.Green)
        }
        Spacer(Modifier.height(TazSpace.sm))
        OtpCells(value = flow.otpInput, onValueChange = flow::onOtpChanged, enabled = !flow.verifying)
        FailureText(flow.failure)
        Spacer(Modifier.height(TazSpace.sm))
        ResendRow(flow)
        Spacer(Modifier.height(TazSpace.md))
        PillButton(
            text = cta,
            loading = flow.verifying,
            loadingText = "Verifying…",
            onClick = flow::submitOtp,
            modifier = Modifier.fillMaxWidth(),
            enabled = flow.canVerify || flow.verifying,
            disabledHint = "Enter the 6-digit code to continue"
        )
    }
}

@Composable
private fun ResendRow(flow: AuthFlow) {
    Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
        if (flow.resendRemaining > 0) {
            val s = flow.resendRemaining
            Text(
                "Resend code in ${s / 60}:${(s % 60).toString().padStart(2, '0')}",
                fontSize = TazType.captionSize,
                color = TazColors.TextTertiary
            )
        } else {
            Box(
                Modifier
                    .defaultMinSize(minHeight = TazSize.touchTarget)
                    .clip(TazRadius.chip)
                    .tazPressable(pressScale = TazPress.compact, haptic = TazHaptic.Tap, onClick = flow::resend)
                    .then(if (flow.canResend) Modifier else Modifier.alpha(0.5f)),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    if (flow.requesting) "Sending…" else "Resend code",
                    fontSize = TazType.captionSize,
                    fontWeight = FontWeight.SemiBold,
                    color = TazColors.Green,
                    modifier = Modifier.padding(horizontal = TazSpace.md)
                )
            }
        }
    }
}

@Composable
private fun FailureText(failure: AuthFailure?) {
    if (failure == null) return
    Spacer(Modifier.height(TazSpace.sm))
    Text(
        failure.message,
        fontSize = TazType.captionSize,
        fontWeight = FontWeight.SemiBold,
        color = TazColors.Danger,
        textAlign = TextAlign.Center,
        modifier = Modifier.fillMaxWidth()
    )
}

/**
 * Six cells over one hidden text field. A single real field gives correct
 * numeric keyboard, focus, backspace, paste and IME behaviour for free; the
 * cells only draw its content. Paste of more than 6 characters is reduced to
 * the first six digits by the flow.
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
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center) {
                    repeat(AuthFlow.OTP_LENGTH) { i ->
                        val active = i == value.length.coerceAtMost(AuthFlow.OTP_LENGTH - 1)
                        Box(
                            Modifier
                                .width(44.dp)
                                .height(52.dp)
                                .clip(RoundedCornerShape(12.dp))
                                .background(Color.White)
                                .border(
                                    BorderStroke(if (active) 2.dp else 1.dp, if (active) TazColors.Green else TazColors.CardBorder),
                                    RoundedCornerShape(12.dp)
                                ),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                value.getOrNull(i)?.toString().orEmpty(),
                                fontSize = 22.sp,
                                fontWeight = FontWeight.Bold,
                                color = TazColors.TextPrimary
                            )
                        }
                        if (i < AuthFlow.OTP_LENGTH - 1) Spacer(Modifier.width(8.dp))
                    }
                }
            }
        }
    )
}
