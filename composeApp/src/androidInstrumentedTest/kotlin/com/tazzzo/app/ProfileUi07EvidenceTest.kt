package com.tazzzo.app

import android.graphics.Bitmap
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performScrollTo
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.tazzzo.app.config.appVersionLabel
import com.tazzzo.app.theme.TazzzoTheme
import com.tazzzo.app.ui.home.FloatingNavBar
import com.tazzzo.app.ui.profile.AboutLayout
import com.tazzzo.app.ui.profile.CoinsUnavailableLayout
import com.tazzzo.app.ui.profile.HelpLayout
import com.tazzzo.app.ui.profile.ProfileActions
import com.tazzzo.app.ui.profile.ProfileIdentity
import com.tazzzo.app.ui.profile.ProfileLayout
import com.tazzzo.app.ui.profile.SupportChannels
import com.tazzzo.app.ui.profile.legalLinks
import com.tazzzo.app.ui.voice.GenieLayout
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

/**
 * UI-07 visual evidence: Profile (signed in / signed out / logout confirmation), Help, About and Coins LAYOUTS. State is
 * explicitly sample (a signed-in session with two saved addresses); the composables are the production ones and the
 * version label is the real installed one.
 */
@RunWith(AndroidJUnit4::class)
class ProfileUi07EvidenceTest {
    @get:Rule val rule = createComposeRule()

    private val actions = ProfileActions({}, {}, {}, {}, {})

    private fun content(nav: Boolean = false, body: @Composable () -> Unit) {
        TestState.resetKeepRemote()
        com.tazzzo.app.data.auth.AndroidAppContext.init(ApplicationProvider.getApplicationContext<android.content.Context>())
        rule.setContent {
            val app = remember { TazzzoAppState().also { it.homeTab = HomeTab.PROFILE } }
            CompositionLocalProvider(LocalAppState provides app) { TazzzoTheme { Box(Modifier.fillMaxSize()) { body(); if (nav) FloatingNavBar(Modifier.align(Alignment.BottomCenter)) } } }
        }
        rule.waitForIdle(); rule.mainClock.advanceTimeBy(1_500); rule.waitForIdle(); Thread.sleep(900)
    }

    private fun snapshot(name: String) {
        val bmp = InstrumentationRegistry.getInstrumentation().uiAutomation.takeScreenshot()
        val dir = File(ApplicationProvider.getApplicationContext<android.content.Context>().filesDir, "evidence").apply { mkdirs() }
        File(dir, "$name.png").outputStream().use { bmp.compress(Bitmap.CompressFormat.PNG, 100, it) }
    }

    @Test fun profile_signed_in() {
        content(nav = true) { ProfileLayout(true, ProfileIdentity.NONE, 2, appVersionLabel(), false, actions) }
        rule.onNodeWithContentDescription("Saved addresses").assertIsDisplayed()
        rule.onNodeWithContentDescription("Log out").performScrollTo().assertIsDisplayed()
        rule.onNodeWithTag("appVersion").performScrollTo().assertIsDisplayed()
        snapshot("ui07_profile_signed_in")
    }

    @Test fun profile_signed_out() {
        content(nav = true) { ProfileLayout(false, ProfileIdentity.NONE, null, appVersionLabel(), false, actions) }
        rule.onNodeWithText("You're not signed in").assertIsDisplayed()
        rule.onNodeWithTag("profileLogin").assertIsDisplayed()
        snapshot("ui07_profile_signed_out")
    }

    @Test fun profile_logout_confirmation() {
        content(nav = true) { ProfileLayout(true, ProfileIdentity.NONE, 2, appVersionLabel(), true, actions) }
        rule.onNodeWithText("Log out of Tazzzo?").assertIsDisplayed()
        rule.onNodeWithTag("confirmLogout").assertIsDisplayed()
        snapshot("ui07_logout_sheet")
    }

    @Test fun help_unavailable() {
        content { HelpLayout(SupportChannels.NONE, onBack = {}, onOrders = {}) }
        rule.onNodeWithText("Support isn't set up in the app yet").assertIsDisplayed()
        snapshot("ui07_help")
    }

    @Test fun about() {
        content { AboutLayout(appVersionLabel(), legalLinks(), onBack = {}) }
        rule.onNodeWithTag("tagline").assertIsDisplayed()
        rule.onNodeWithTag("aboutVersion").assertIsDisplayed()
        rule.onNodeWithTag("legalUnavailable").assertIsDisplayed()
        snapshot("ui07_about")
    }

    @Test fun coins_unavailable() {
        content { CoinsUnavailableLayout(onBack = {}) }
        rule.onNodeWithText("Tazzzo Coins aren't available yet").assertIsDisplayed()
        snapshot("ui07_coins")
    }

    @Test fun genie_coming_soon() {
        content { GenieLayout(onBack = {}, onShop = {}) }
        rule.onNodeWithText("Voice ordering is coming soon".uppercase()).assertIsDisplayed()
        rule.onNodeWithTag("genieShop").performScrollTo().assertIsDisplayed()
        snapshot("ui08_genie")
    }
}
