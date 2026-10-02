package com.tazzzo.app

import android.graphics.Bitmap
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.unit.dp
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.performTextInput
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.tazzzo.app.theme.TazColors
import com.tazzzo.app.theme.TazSpace
import com.tazzzo.app.theme.TazType
import com.tazzzo.app.theme.TazzzoTheme
import com.tazzzo.app.ui.common.ButtonTone
import com.tazzzo.app.ui.common.EditorialText
import com.tazzzo.app.ui.common.PhotoAnchor
import com.tazzzo.app.ui.common.PhotoBackdrop
import com.tazzzo.app.ui.common.PhotoPlaceholders
import com.tazzzo.app.ui.common.TazzzoPrimaryButton
import com.tazzzo.app.ui.common.VSpace
import com.tazzzo.app.ui.common.italic
import com.tazzzo.app.ui.common.plain
import com.tazzzo.app.ui.onboarding.LoginCopy
import com.tazzzo.app.ui.onboarding.OtpCells
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

/**
 * Visual evidence for the OTP step. The real OTP step needs a successful backend OTP request, which no non-prod
 * environment can answer yet, so this HARNESS renders the same presentation pieces the Login screen uses for it
 * (editorial headline, number line, [OtpCells], resend copy, Verify) with a partially entered code. It is evidence
 * of the presentation, not of the auth flow — AuthFlow is covered by its own tests.
 */
@RunWith(AndroidJUnit4::class)
class BrandOtpEvidenceTest {
    @get:Rule val rule = createComposeRule()

    @Test fun otp_step_presentation() {
        rule.setContent {
            TazzzoTheme {
                Box(Modifier.fillMaxSize()) {
                    PhotoBackdrop(photo = null, placeholder = PhotoPlaceholders.creamWall, anchor = PhotoAnchor.Bottom, modifier = Modifier.fillMaxSize())
                    Column(Modifier.fillMaxSize().statusBarsPadding().padding(top = 148.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                        EditorialText(listOf(plain("Enter the\n"), italic("6-digit code")), size = TazType.editorialHeadlineSize, lineHeight = TazType.editorialHeadlineLine, color = TazColors.BrandEditorial)
                        VSpace(TazSpace.lg)
                        EditorialText(listOf(plain(LoginCopy.OTP_SUPPORT)), size = TazType.editorialSubSize, lineHeight = TazType.editorialSubLine, color = TazColors.TextSecondary)
                        EditorialText(listOf(plain("+91 98765 43210")), size = TazType.editorialSubSize, lineHeight = TazType.editorialSubLine, color = TazColors.BrandEditorial)
                        VSpace(TazSpace.xxl)
                        Column(Modifier.fillMaxWidth().padding(horizontal = TazSpace.xxl), horizontalAlignment = Alignment.CenterHorizontally) {
                            var code by remember { mutableStateOf("4") }
                            OtpCells(value = code, onValueChange = { code = it.take(6) }, enabled = true)
                            VSpace(TazSpace.lg)
                            androidx.compose.material3.Text("Didn’t receive the code?", fontSize = TazType.captionSize, color = TazColors.TextSecondary)
                            androidx.compose.material3.Text("Resend in 00:30", fontSize = TazType.captionSize, color = TazColors.BrandEditorial,
                                textDecoration = androidx.compose.ui.text.style.TextDecoration.Underline)
                            VSpace(TazSpace.lg)
                            TazzzoPrimaryButton(text = "Verify", onClick = {}, modifier = Modifier.fillMaxWidth(), tone = ButtonTone.Editorial, enabled = false)
                        }
                    }
                }
            }
        }
        save("ui01_otp_harness.png")
    }

    /** Isolation case: the editorial button enabled vs disabled, both tones, with no keyboard or scroll involved. */
    @Test fun primary_button_states() {
        rule.setContent {
            TazzzoTheme {
                Column(Modifier.fillMaxSize().statusBarsPadding().padding(TazSpace.xxl), horizontalAlignment = Alignment.CenterHorizontally) {
                    TazzzoPrimaryButton(text = "Enabled editorial", onClick = {}, modifier = Modifier.fillMaxWidth(), enabled = true)
                    VSpace(TazSpace.lg)
                    TazzzoPrimaryButton(text = "Disabled editorial", onClick = {}, modifier = Modifier.fillMaxWidth(), enabled = false)
                    VSpace(TazSpace.lg)
                    TazzzoPrimaryButton(text = "Loading", onClick = {}, modifier = Modifier.fillMaxWidth(), loading = true)
                    VSpace(TazSpace.lg)
                    Box(Modifier.fillMaxWidth().padding(TazSpace.lg).background(TazColors.BrandEditorial).padding(TazSpace.lg)) {
                        TazzzoPrimaryButton(text = "Enabled cream", onClick = {}, modifier = Modifier.fillMaxWidth(), tone = ButtonTone.Cream, italic = true)
                    }
                }
            }
        }
        save("ui01_button_states.png")
    }

    /** The REAL Login screen with a valid number typed (keyboard up): the Continue button must stay filled. */
    @Test fun login_screen_with_valid_number() {
        TestState.resetKeepRemote()
        com.tazzzo.app.data.auth.AndroidAppContext.init(ApplicationProvider.getApplicationContext<android.content.Context>())
        rule.setContent {
            val app = androidx.compose.runtime.remember { TazzzoAppState() }
            androidx.compose.runtime.CompositionLocalProvider(LocalAppState provides app) { TazzzoTheme { com.tazzzo.app.ui.onboarding.LoginScreen() } }
        }
        rule.onNodeWithContentDescription("Mobile number").performTextInput("9876543210")
        rule.waitForIdle()
        save("ui01_login_valid.png")
        rule.onNodeWithContentDescription("Continue").assertIsEnabled()
    }

    private fun save(name: String) {
        rule.waitForIdle()
        Thread.sleep(600)
        // UiAutomation screenshot: works on a headless/software-GPU emulator where PixelCopy does not.
        val bmp = androidx.test.platform.app.InstrumentationRegistry.getInstrumentation().uiAutomation.takeScreenshot()
        // App-internal files: survive until the app is uninstalled and can be pulled with `run-as`.
        val dir = File(ApplicationProvider.getApplicationContext<android.content.Context>().filesDir, "evidence").apply { mkdirs() }
        File(dir, name).outputStream().use { bmp.compress(Bitmap.CompressFormat.PNG, 100, it) }
    }
}
