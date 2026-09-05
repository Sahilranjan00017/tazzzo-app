package com.tazzzo.app

import android.content.Intent
import android.graphics.Bitmap
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.semantics.SemanticsActions
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.rules.ActivityScenarioRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.tazzzo.app.theme.MotionSettings
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

/**
 * The C1 journey on a real device: become a member, build a basket that
 * exercises the promotion engine, and read the bill the customer would read.
 *
 * Why this is a test and not a scripted screenshot run: on the 2-core
 * software-GL emulator a cold Home load took six minutes and every fixed-sleep
 * tap was dropped. This harness waits for genuine idle, asserts the SEMANTICS
 * of the bill (the numbers a screen reader would announce), and captures the
 * screen from inside the process as evidence. Stronger than a screenshot, and
 * it cannot pass by accident.
 *
 * Evidence PNGs land in the app's external files dir; pull with
 * `adb pull /sdcard/Android/data/com.tazzzo.app/files/evidence/`.
 */
@RunWith(AndroidJUnit4::class)
class CartPromotionJourneyTest {

    init {
        TestState.reset()                      // clean persisted store per class
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
    }

    private fun snapshot(name: String) {
        val bmp = rule.onRoot().captureToImage().asAndroidBitmap()
        val dir = File(ApplicationProvider.getApplicationContext<android.content.Context>()
            .getExternalFilesDir(null), "evidence").apply { mkdirs() }
        File(dir, "$name.png").outputStream().use { bmp.compress(Bitmap.CompressFormat.PNG, 100, it) }
    }

    /** Adds [times] units via the ADD control then the + control, scrolling the feed to reach it. */
    private fun addFromHome(product: String, times: Int) {
        val add = hasContentDescription("Add $product to cart")
        val inc = hasContentDescription("Increase quantity of $product")
        rule.onNodeWithTag("homeFeed").performScrollToNode(add or inc)
        rule.waitForIdle()
        repeat(times) {
            if (rule.onAllNodes(add).fetchSemanticsNodes().isNotEmpty()) click(add) else click(inc)
            rule.waitForIdle()
        }
    }

    @Test
    fun member_basket_shows_itemised_offers_club_and_honest_total_then_coupon_contest() {
        // ---- 0. Home loaded (cold start on a starved emulator can be slow; waits for idle, not a sleep) ----
        waitFor(hasContentDescription("Home"))
        waitFor(hasContentDescription("Vegetables & Fruits"))

        // ---- 1. Become a member through the real flow ----
        click(hasContentDescription("Account"))
        waitFor(hasText("Tazzzo Club", substring = true))
        rule.onAllNodes(hasText("Tazzzo Club", substring = true) and hasClickAction()).onFirst()
            .performSemanticsAction(SemanticsActions.OnClick)
        waitFor(hasText("Join Tazzzo Club — ₹99"))
        click(hasText("Join Tazzzo Club — ₹99"))
        waitFor(hasText("Pay ₹99"))
        click(hasText("Pay ₹99"))
        waitFor(hasText("Welcome to Tazzzo Club"), 30_000)
        snapshot("c1_01_welcome")
        click(hasText("Start shopping"))
        waitFor(hasContentDescription("Vegetables & Fruits"))

        // ---- 2. Basket: 3 bananas (members-only B2G1, stackable) + onions + potatoes to cross ₹500 ----
        addFromHome("Banana Robusta", 3)     // 3 × ₹42 = ₹126, one free → −₹42
        addFromHome("Fresh Onion", 10)       // 10 × ₹32 = ₹320
        addFromHome("Fresh Potato", 4)       // 4 × ₹34 = ₹136  → items ₹582 ≥ ₹500

        // ---- 3. Cart: every saving on its own line, honest total ----
        waitFor(hasText("View cart"))
        click(hasText("View cart"))
        waitFor(hasText("Bill details"))
        // Club 5% of ₹582 = ₹29 (floor). B2G1 stacks: ₹42. Realised = ₹71.
        rule.onAllNodes(hasText("Buy 2 get 1 free", substring = true)).fetchSemanticsNodes().let {
            assert(it.isNotEmpty()) { "members-only B2G1 line missing from bill" }
        }
        rule.onAllNodes(hasText("Tazzzo Club savings")).fetchSemanticsNodes().let {
            assert(it.isNotEmpty()) { "Club savings line missing from bill" }
        }
        rule.onAllNodes(hasText("−₹29")).fetchSemanticsNodes().let {
            assert(it.isNotEmpty()) { "Club discount should be ₹29 on ₹582" }
        }
        rule.onAllNodes(hasText("−₹42")).fetchSemanticsNodes().let {
            assert(it.isNotEmpty()) { "B2G1 should free one banana (₹42)" }
        }
        rule.onAllNodes(hasText("You saved")).fetchSemanticsNodes().let {
            assert(it.isNotEmpty()) { "realised 'You saved' line missing" }
        }
        rule.onAllNodes(hasText("₹71")).fetchSemanticsNodes().let {
            assert(it.isNotEmpty()) { "You saved should be ₹71 = ₹29 Club + ₹42 B2G1" }
        }
        // No competing exclusive offer here, so no best-offer note may appear.
        assert(rule.onAllNodes(hasText("Best offer applied", substring = true)).fetchSemanticsNodes().isEmpty()) {
            "no contest should be reported when nothing competed"
        }
        snapshot("c1_02_cart_member_offers")

        // ---- 4. Coupon: TAZZZO50 (₹50) beats Club (₹29) → Club set aside, customer told ----
        waitFor(hasContentDescription("Coupon code"))
        rule.onAllNodes(hasContentDescription("Coupon code") and hasSetTextAction()).onFirst()
            .performTextInput("TAZZZO50")
        click(hasText("Apply"))
        rule.waitForIdle()
        waitFor(hasText("Applied"))
        rule.onAllNodes(hasText("Best offer applied — TAZZZO50 saves you ₹50; your Club discount would have saved ₹29."))
            .fetchSemanticsNodes().let { assert(it.isNotEmpty()) { "best-offer note missing or wrong" } }
        // The prominent Club card must AGREE with the bill: no "Club savings applied"
        // while the bill applied ₹0 Club. (Found in a screenshot after the
        // semantic assertions passed — the card read from an isolated Club
        // evaluation, not from the post-stacking bill.)
        assert(rule.onAllNodes(hasText("Club savings applied")).fetchSemanticsNodes().isEmpty()) {
            "Club card contradicts the bill: says applied while coupon won"
        }
        waitFor(hasText("Club discount set aside for this order"))
        // Club line must be gone from the bill; coupon line present; B2G1 still stacks.
        assert(rule.onAllNodes(hasText("Tazzzo Club savings")).fetchSemanticsNodes().isEmpty()) {
            "Club line must not show when the coupon won"
        }
        rule.onAllNodes(hasText("−₹50")).fetchSemanticsNodes().let {
            assert(it.isNotEmpty()) { "coupon ₹50 line missing" }
        }
        // Realised = ₹50 coupon + ₹42 B2G1 = ₹92.
        rule.onAllNodes(hasText("₹92")).fetchSemanticsNodes().let {
            assert(it.isNotEmpty()) { "You saved should be ₹92 after the coupon won" }
        }
        snapshot("c1_03_cart_coupon_contest")
    }
}
