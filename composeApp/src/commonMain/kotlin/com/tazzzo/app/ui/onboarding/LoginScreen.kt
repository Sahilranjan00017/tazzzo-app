package com.tazzzo.app.ui.onboarding

import androidx.compose.animation.Crossfade
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.tazzzo.app.LocalAppState
import com.tazzzo.app.Screen
import com.tazzzo.app.data.repository.ServiceLocator
import kotlinx.coroutines.CancellationException
import com.tazzzo.app.ui.state.toLoadError
import com.tazzzo.app.ui.interaction.TazPress
import com.tazzzo.app.ui.interaction.tazPressable
import com.tazzzo.app.ui.interaction.TazHaptic
import com.tazzzo.app.theme.TazColors
import com.tazzzo.app.theme.TazRadius
import com.tazzzo.app.theme.TazSize
import com.tazzzo.app.theme.TazSpace
import com.tazzzo.app.theme.TazType
import com.tazzzo.app.ui.common.LogoImage
import com.tazzzo.app.ui.common.PillButton
import com.tazzzo.app.ui.common.TazTopBar
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import com.tazzzo.app.config.AppConfig
import com.tazzzo.app.config.BrandCopy

/**
 * Two-step phone + OTP login (demo: any 4-digit OTP works).
 * Step 1 = phone number entry, Step 2 = OTP verification.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LoginScreen() {
    val app = LocalAppState.current
    val scope = rememberCoroutineScope()

    var step by remember { mutableStateOf(1) }
    var phone by remember { mutableStateOf("") }
    var otp by remember { mutableStateOf("") }
    var sending by remember { mutableStateOf(false) }
    var verifying by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var resendIn by remember { mutableStateOf(30) }

    // Resend countdown: ticks once a second while the OTP step is showing.
    // Tapping "Resend OTP" just resets [resendIn]; this loop keeps ticking.
    LaunchedEffect(step) {
        if (step != 2) return@LaunchedEffect
        resendIn = 30
        while (true) {
            delay(1_000)
            if (resendIn > 0) resendIn--
        }
    }

    Column(Modifier.fillMaxSize().background(TazColors.Cream)) {
        TazTopBar("Login", onBack = { app.back() })

        Column(
            Modifier
                .weight(1f)
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Spacer(Modifier.height(28.dp))
            LogoImage(56.dp)
            Spacer(Modifier.height(24.dp))

            // White form card
            Column(
                Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(16.dp))
                    .background(Color.White)
                    .border(BorderStroke(1.dp, TazColors.CardBorder), RoundedCornerShape(16.dp))
                    .padding(20.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Crossfade(targetState = step) { currentStep ->
                    if (currentStep == 1) {
                        // ------------------------------------------------ Step 1: phone
                        Column(
                            Modifier.fillMaxWidth(),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Text(
                                "Log in with your phone number",
                                fontSize = 20.sp,
                                fontWeight = FontWeight.Bold,
                                color = TazColors.TextPrimary,
                                textAlign = TextAlign.Center
                            )
                            Spacer(Modifier.height(6.dp))
                            Text(
                                // Demo shortcut copy renders only in demo builds.
                                if (AppConfig.demoMode) "We'll send a 4-digit OTP (demo: any 4 digits)"
                                else "We'll send a 4-digit OTP",
                                fontSize = 13.sp,
                                color = TazColors.TextSecondary,
                                textAlign = TextAlign.Center
                            )
                            Spacer(Modifier.height(20.dp))
                            OutlinedTextField(
                                value = phone,
                                onValueChange = { input ->
                                    phone = input.filter { it.isDigit() }.take(10)
                                },
                                modifier = Modifier.fillMaxWidth().height(TazSize.inputHeight),
                                singleLine = true,
                                shape = TazRadius.card,
                                leadingIcon = { DialPrefix() },
                                placeholder = {
                                    Text(
                                        "10-digit mobile number",
                                        fontSize = TazType.bodySize,
                                        color = TazColors.TextTertiary
                                    )
                                },
                                textStyle = TextStyle(
                                    fontSize = TazType.titleSize,
                                    fontWeight = TazType.titleWeight,
                                    color = TazColors.TextPrimary
                                ),
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
                                colors = tazFieldColors()
                            )
                            // Only rendered when the OTP request actually
                            // failed. Before this, a failed request silently
                            // did nothing and the spinner never stopped.
                            error?.let { message ->
                                Spacer(Modifier.height(10.dp))
                                Text(
                                    message,
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    color = TazColors.Danger,
                                    textAlign = TextAlign.Center,
                                    modifier = Modifier.fillMaxWidth()
                                )
                            }
                            Spacer(Modifier.height(18.dp))
                            run {
                                // Was: the button vanished and a bare spinner
                                // took its place, so the CTA disappeared from
                                // under the customer's finger. The button now
                                // stays put and shows that it is working.
                                PillButton(
                                    text = "Send OTP",
                                    loading = sending,
                                    loadingText = "Sending OTP…",
                                    onClick = {
                                        if (!sending) {
                                            scope.launch {
                                                sending = true
                                                error = null
                                                try {
                                                    ServiceLocator.auth.requestOtp(phone)
                                                    otp = ""
                                                    step = 2
                                                } catch (cancellation: CancellationException) {
                                                    throw cancellation
                                                } catch (t: Throwable) {
                                                    // Never advance to the OTP
                                                    // step for a code that was
                                                    // never sent.
                                                    error = t.toLoadError().message
                                                } finally {
                                                    sending = false
                                                }
                                            }
                                        }
                                    },
                                    modifier = Modifier.fillMaxWidth(),
                                    enabled = phone.length == 10,
                                    disabledHint = "Enter your 10-digit mobile number to continue"
                                )
                            }
                        }
                    } else {
                        // ------------------------------------------------ Step 2: OTP
                        Column(
                            Modifier.fillMaxWidth(),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Text(
                                "Enter OTP sent to +91 $phone",
                                fontSize = 20.sp,
                                fontWeight = FontWeight.Bold,
                                color = TazColors.TextPrimary,
                                textAlign = TextAlign.Center
                            )
                            Spacer(Modifier.height(6.dp))
                            Text(
                                // Demo shortcut copy renders only in demo builds.
                                if (AppConfig.demoMode) "Any 4 digits work in this demo"
                                else "We've sent a code to +91 $phone",
                                fontSize = 13.sp,
                                color = TazColors.TextSecondary,
                                textAlign = TextAlign.Center
                            )
                            Spacer(Modifier.height(20.dp))
                            OutlinedTextField(
                                value = otp,
                                onValueChange = { input ->
                                    otp = input.filter { it.isDigit() }.take(4)
                                },
                                modifier = Modifier.fillMaxWidth().height(TazSize.inputHeight),
                                singleLine = true,
                                shape = TazRadius.card,
                                placeholder = {
                                    Text(
                                        "• • • •",
                                        fontSize = TazType.titleSize,
                                        color = TazColors.TextTertiary,
                                        modifier = Modifier.fillMaxWidth(),
                                        textAlign = TextAlign.Center
                                    )
                                },
                                textStyle = TextStyle(
                                    fontSize = 20.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = TazColors.TextPrimary,
                                    letterSpacing = 10.sp,
                                    textAlign = TextAlign.Center
                                ),
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                colors = tazFieldColors()
                            )
                            if (error != null) {
                                Spacer(Modifier.height(10.dp))
                                Text(
                                    error ?: "",
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    color = TazColors.Danger,
                                    textAlign = TextAlign.Center
                                )
                            }
                            Spacer(Modifier.height(8.dp))
                            Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                                if (resendIn > 0) {
                                    Text(
                                        "Resend OTP in 0:" + resendIn.toString().padStart(2, '0'),
                                        fontSize = TazType.captionSize,
                                        color = TazColors.TextSecondary
                                    )
                                } else {
                                    Box(
                                        Modifier
                                            .defaultMinSize(minHeight = 44.dp)
                                            .clip(RoundedCornerShape(8.dp))
                                            .tazPressable(
                                                pressScale = TazPress.compact,
                                                haptic = TazHaptic.Tap,
                                                onClick = {
                                                scope.launch {
                                                    error = null
                                                    try {
                                                        ServiceLocator.auth.requestOtp(phone)
                                                    } catch (cancellation: CancellationException) {
                                                        throw cancellation
                                                    } catch (t: Throwable) {
                                                        error = t.toLoadError().message
                                                    }
                                                }
                                                resendIn = 30
                                                }
                                            )
                                            .padding(horizontal = 12.dp),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Text(
                                            "Resend OTP",
                                            fontSize = TazType.captionSize,
                                            fontWeight = FontWeight.SemiBold,
                                            color = TazColors.Green
                                        )
                                    }
                                }
                            }
                            Spacer(Modifier.height(10.dp))
                            if (verifying) {
                                Box(
                                    Modifier.fillMaxWidth().height(48.dp),
                                    contentAlignment = Alignment.Center
                                ) {
                                    CircularProgressIndicator(
                                        color = TazColors.Green,
                                        modifier = Modifier.size(28.dp)
                                    )
                                }
                            } else {
                                PillButton(
                                    text = "Verify & Continue",
                                    onClick = {
                                        if (otp.length == 4 && !verifying) {
                                            scope.launch {
                                                verifying = true
                                                error = null
                                                try {
                                                    val profile =
                                                        ServiceLocator.auth.verifyOtp(phone, otp)
                                                    if (profile != null) {
                                                        app.user = profile
                                                        app.requestGuidedTourIfFirstTime()
                                                        app.goHome()
                                                    } else {
                                                        error = "Invalid OTP, try again"
                                                    }
                                                } catch (cancellation: CancellationException) {
                                                    // A thrown auth error is not
                                                    // a wrong OTP; say what it is.
                                                    throw cancellation
                                                } catch (t: Throwable) {
                                                    error = t.toLoadError().message
                                                } finally {
                                                    verifying = false
                                                }
                                            }
                                        }
                                    },
                                    modifier = Modifier.fillMaxWidth(),
                                    enabled = otp.length == 4,
                                    disabledHint = "Enter the 4-digit OTP to continue"
                                )
                            }
                            Spacer(Modifier.height(14.dp))
                            Box(
                                Modifier
                                    .defaultMinSize(minHeight = 44.dp)
                                    .clip(RoundedCornerShape(8.dp))
                                    .tazPressable(onClick = { step = 1
                                        otp = ""
                                        error = null }, pressScale = TazPress.compact)
                                    .padding(horizontal = 12.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    "Change number",
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    color = TazColors.Green
                                )
                            }
                        }
                    }
                }
            }

            Spacer(Modifier.height(TazSpace.lg))
            Box(
                Modifier
                    .fillMaxWidth()
                    .defaultMinSize(minHeight = 48.dp)
                    .clip(TazRadius.card)
                    .tazPressable(onClick = { app.requestGuidedTourIfFirstTime()
                        app.goHome() }, pressScale = TazPress.compact),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    "Skip for now",
                    fontSize = TazType.buttonSize,
                    fontWeight = FontWeight.SemiBold,
                    color = TazColors.Green
                )
            }
            Spacer(Modifier.height(TazSpace.xxl))
        }

        // Trust footer
        Text(
            "Your number is safe with us  ·  WhatsApp ordering: say HI to ${BrandCopy.whatsappNumber}",
            fontSize = TazType.microSize,
            lineHeight = TazType.microLine,
            color = TazColors.TextTertiary,
            textAlign = TextAlign.Center,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp, vertical = 10.dp)
                .navigationBarsPadding()
        )
    }
}
