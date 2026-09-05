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
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.semantics.SemanticsActions
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.rules.ActivityScenarioRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.tazzzo.app.config.CampaignConfig
import com.tazzzo.app.theme.MotionSettings
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

/**
 * C3 on device: Home reads as a commerce destination, and the merchandising
 * that puts one category in two modules stays distinguishable.
 *
 * Why in-process and not `adb` taps: two scripted capture runs landed on the
 * Account tab and the login wall. This captures from inside the process after
 * genuine idle, and asserts the semantics a screen reader would announce.
 * Pull evidence with `adb pull /sdcard/Android/data/com.tazzzo.app/files/evidence/`.
 */
@RunWith(AndroidJUnit4::class)
class HomeEvidenceTest {

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
    private fun count(m: SemanticsMatcher) = rule.onAllNodes(m).fetchSemanticsNodes().size
    /**
     * Evidence capture is BEST EFFORT and must never decide a verdict.
     *
     * `captureToImage` goes through PixelCopy, which fails on a loaded
     * software-GL emulator with "Failed waiting for PixelCopy!" — an
     * environment fault, not a defect in the app or in what this test asserts.
     * Letting it throw turned a passing journey into a red test (seen
     * 2026-09-06). The assertions are the oracle; the PNG is documentation.
     */
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

    @Test
    fun home_is_a_commerce_destination_and_duplicate_category_tiles_stay_distinct() {
        val campaign = requireNotNull(CampaignConfig.current) { "this evidence run documents a live campaign; none is configured" }
        val gridTile = hasContentDescription("Vegetables & Fruits") and hasClickAction()
        val heroTile = hasContentDescription("${campaign.title}: Vegetables & Fruits") and hasClickAction()

        waitFor(hasContentDescription("Home"))
        waitFor(gridTile)

        // ---- Festival hero above the fold, with its own distinct tile label ----
        waitFor(hasText(campaign.title, substring = true))
        assert(count(heroTile) == 1) { "hero tile for the campaign category missing or duplicated: ${count(heroTile)}" }
        // The SAME category in the grid must remain exactly one actionable node.
        // (Regression: the hero once reused the grid label → 2 identical nodes.)
        assert(count(gridTile) == 1) { "expected exactly one actionable 'Vegetables & Fruits', found ${count(gridTile)}" }
        snapshot("c3_01_home_top")

        // ---- Deals rail: real MRP-vs-price differences, no fabricated badges ----
        val deals = hasText("deals", substring = true, ignoreCase = true)
        rule.onNodeWithTag("homeFeed").performScrollToNode(deals); rule.waitForIdle()
        assert(count(hasText("OFF", substring = true)) >= 1) { "deals rail must show ₹X OFF derived from MRP − price" }
        snapshot("c3_02_home_deals")

        // ---- Category grid, then the dense product grid inside a category ----
        rule.onNodeWithTag("homeFeed").performScrollToNode(gridTile); rule.waitForIdle()
        snapshot("c3_03_home_categories")
        rule.onAllNodes(gridTile).onFirst().performSemanticsAction(SemanticsActions.OnClick); rule.waitForIdle()
        waitFor(hasContentDescription("Add Banana Robusta to cart"), 30_000)
        snapshot("c3_04_category_product_grid")

        // Back returns to Home with the grid still present (state preserved).
        rule.onAllNodes(hasContentDescription("Back") and hasClickAction()).onFirst()
            .performSemanticsAction(SemanticsActions.OnClick); rule.waitForIdle()
        waitFor(gridTile)
    }
}
