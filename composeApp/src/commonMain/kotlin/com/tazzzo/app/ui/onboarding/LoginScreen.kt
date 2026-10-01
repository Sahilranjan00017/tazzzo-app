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
 * The Login route. Uses the same shared [AuthFlow] as first-run onboarding:
 * real backend phone + 6-digit OTP. Functional structure only — the approved
 * TZ-LOGIN-001 / TZ-OTP-001 visuals are PR-03B.
 */
@Composable
fun LoginScreen() {
    val app = LocalAppState.current
    val flow = rememberAuthFlow(onDone = {
        app.requestGuidedTourIfFirstTime()
        app.goHome()
    })

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

            Column(
                Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(16.dp))
                    .background(Color.White)
                    .border(BorderStroke(1.dp, TazColors.CardBorder), RoundedCornerShape(16.dp))
                    .padding(20.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    if (flow.step == AuthStep.Phone) "Log in with your phone number" else "Verify your number",
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Bold,
                    color = TazColors.TextPrimary,
                    textAlign = TextAlign.Center
                )
                Spacer(Modifier.height(6.dp))
                Text(
                    if (flow.step == AuthStep.Phone) "We'll text you a 6-digit code"
                    else "Enter the code we just sent you",
                    fontSize = 13.sp,
                    color = TazColors.TextSecondary,
                    textAlign = TextAlign.Center
                )
                Spacer(Modifier.height(20.dp))
                AuthEntry(flow = flow, phoneCta = "Send code", otpCta = "Verify & Continue")
            }

            Spacer(Modifier.height(TazSpace.lg))
            Box(
                Modifier
                    .fillMaxWidth()
                    .defaultMinSize(minHeight = 48.dp)
                    .clip(TazRadius.card)
                    .tazPressable(onClick = {
                        app.requestGuidedTourIfFirstTime()
                        app.goHome()
                    }, pressScale = TazPress.compact),
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
