package com.tazzzo.app

import android.content.Intent
import android.graphics.Bitmap
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.semantics.SemanticsActions
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.rules.ActivityScenarioRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.tazzzo.app.data.MockCatalog
import com.tazzzo.app.theme.MotionSettings
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.onNodeWithTag

/**
 * The three redesigned screens the journey suite never reaches: Categories,
 * Account and Help.
 *
 * Each assertion targets something the mockups asked for AND that had to be
 * derived rather than drawn: a real category count, a real coin balance, a real
 * order on the help screen. If any of them ever renders a comp value again,
 * this fails.
 */
@RunWith(AndroidJUnit4::class)
class RedesignEvidenceTest {

    init {
        TestState.reset()
        MotionSettings.ambientEnabled = false
    }

    @get:Rule(order = 0)
    val rule = createEmptyComposeRule()

    @get:Rule(order = 1)
    val activity = ActivityScenarioRule<MainActivity>(
        Intent(ApplicationProvider.getApplicationContext(), MainActivity::class.java)
            .putExtra("taz_start_home", true)
    )

    private fun waitFor(m: SemanticsMatcher, ms: Long = 60_000) {
        rule.waitUntil(timeoutMillis = ms) { rule.onAllNodes(m).fetchSemanticsNodes().isNotEmpty() }
    }
    private fun click(m: SemanticsMatcher) {
        rule.onAllNodes(m and hasClickAction()).onFirst().performSemanticsAction(SemanticsActions.OnClick)
        rule.waitForIdle()
    }
    private fun present(m: SemanticsMatcher) = rule.onAllNodes(m).fetchSemanticsNodes().isNotEmpty()
    private fun snapshot(name: String) {
        try {
            val bmp = rule.onRoot().captureToImage().asAndroidBitmap()
            val dir = File(ApplicationProvider.getApplicationContext<android.content.Context>()
                .getExternalFilesDir(null), "evidence").apply { mkdirs() }
            File(dir, "$name.png").outputStream().use { bmp.compress(Bitmap.CompressFormat.PNG, 100, it) }
        } catch (t: Throwable) {
            android.util.Log.w("TazzzoEvidence", "snapshot '$name' skipped: ${t.message}")
        }
    }

    @Test fun categories_account_and_help_render_derived_data_not_comp_values() {
        waitFor(hasContentDescription("Home"))
        waitFor(hasContentDescription("Vegetables & Fruits") and hasClickAction())

        // ---- Categories: group pills, a real coupon, real counts ----
        click(hasContentDescription("Categories"))
        waitFor(hasText("All categories"), 30_000)
        waitFor(hasText("All"))
        assert(present(hasText("Grocery & Kitchen"))) { "group pills missing" }
        // The spotlight must show the coupon that genuinely exists, with the
        // minimum spend that gates it — never the mockup's invented FRESH100.
        assert(present(hasText("TAZZZO50"))) { "promotion spotlight must show the live coupon" }
        val groceryItems = MockCatalog.products.count { p ->
            MockCatalog.categories.firstOrNull { it.id == p.categoryId }?.group == "Grocery & Kitchen"
        }
        rule.onNodeWithTag("categoriesList").performScrollToNode(hasText("$groceryItems items"))
        rule.waitForIdle()
        assert(present(hasText("$groceryItems items"))) {
            "the group count must be the real one ($groceryItems), not a comp value"
        }
        snapshot("r1_categories")

        // ---- Account: derived counts, coins not a rupee wallet ----
        click(hasContentDescription("Account"))
        waitFor(hasText("Tazzzo Coins"), 30_000)
        assert(present(hasText("coins", substring = true))) { "coin balance missing" }
        // The mockup's rupee wallet must not have shipped.
        assert(!present(hasText("Add Balance"))) { "a rupee wallet is a regulated product, not a card" }
        assert(!present(hasText("cashback", substring = true, ignoreCase = true))) {
            "no cashback rate exists to advertise"
        }
        assert(present(hasContentDescription("Notifications", substring = true))) { "notifications toggle missing" }
        assert(present(hasContentDescription("Voice shopping", substring = true))) { "voice row must be untouched" }
        snapshot("r2_account")

        // ---- Help: reachable, with its issue routes ----
        click(hasContentDescription("Help & care"))
        waitFor(hasText("How can we help?"), 30_000)
        assert(present(hasContentDescription("Report a delivery partner", substring = true))) {
            "the mockup's rider-behaviour route is missing"
        }
        // The refund-time promise must not have shipped.
        assert(!present(hasText("2 minutes", substring = true))) {
            "an unsigned refund SLA must never be printed"
        }
        snapshot("r3_help")
    }
}
