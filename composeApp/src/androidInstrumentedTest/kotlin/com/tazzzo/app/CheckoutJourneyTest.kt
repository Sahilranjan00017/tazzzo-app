package com.tazzzo.app

import android.content.Intent
import android.graphics.Bitmap
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasTestTag
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
import com.tazzzo.app.theme.MotionSettings
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

/**
 * C2 on device: delivery is a CHOICE with a price, and the choice follows the
 * order everywhere — review, confirmation, orders list, order detail.
 *
 * Asserts the same slot at every stop. The customer deliberately picks the
 * window that is NOT recommended, so a pass proves their choice propagated and
 * not merely the default. Slot-fee arithmetic is covered in DeliveryFeeTest;
 * the three live windows are all free, so no fee is asserted on device.
 */
@RunWith(AndroidJUnit4::class)
class CheckoutJourneyTest {

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
    private fun addFromHome(product: String, times: Int) {
        val add = hasContentDescription("Add $product to cart")
        val inc = hasContentDescription("Increase quantity of $product")
        rule.onNodeWithTag("homeFeed").performScrollToNode(add or inc); rule.waitForIdle()
        repeat(times) { if (present(add)) click(add) else click(inc) }
    }

    @Test
    fun chosen_slot_and_fee_follow_the_order_from_review_to_receipt() {
        waitFor(hasContentDescription("Home"))
        waitFor(hasTestTag("homeFeed"))   // Home no longer lists the aisle tiles (they moved to Shop in UI-02)

        // Small basket, BELOW the free-delivery threshold, so delivery is a real line.
        addFromHome("Fresh Onion", 2)                                  // ₹64
        waitFor(hasText("View cart")); click(hasText("View cart"))
        waitFor(hasText("Choose your delivery"))
        snapshot("c2_01_cart_delivery_choice")

        click(hasText("Proceed to checkout"))
        // ---- Address ----
        waitFor(hasText("Continue"))
        waitFor(hasText("Home"), 30_000)                                // the serviceable saved address
        rule.onAllNodes(hasText("Home") and hasClickAction()).onFirst().performSemanticsAction(SemanticsActions.OnClick)
        rule.waitForIdle()
        click(hasText("Continue"))

        // ---- Slot: three fixed windows. Pick the one that is NOT recommended. ----
        waitFor(hasText("6 – 9 AM"))
        assert(present(hasText("12 – 3 PM"))) { "midday window missing" }
        assert(present(hasText("6 – 9 PM"))) { "evening window missing" }
        assert(present(hasText("Recommended"))) { "recommended tag missing on the morning window" }
        assert(present(hasText("Free"))) { "free slots must show 'Free'" }
        snapshot("c2_02_slot_choice")
        click(hasText("12 – 3 PM"))
        click(hasText("Continue"))

        // ---- Payment: COD (UPI/Card honestly disabled) ----
        waitFor(hasText("Cash on Delivery"))
        assert(present(hasText("Coming soon"))) { "UPI/Card must say Coming soon, not pretend" }
        click(hasText("Cash on Delivery"))
        click(hasText("Continue"))

        // ---- Review: slot + fee echoed; add an instruction ----
        waitFor(hasText("12 – 3 PM · Free"))
        waitFor(hasText("Leave at my door"))
        click(hasText("Leave at my door"))
        snapshot("c2_03_review_slot_instruction")
        rule.onAllNodes(hasText("Place order", substring = true) and hasClickAction()).onFirst()
            .performSemanticsAction(SemanticsActions.OnClick)

        // ---- Confirmation: same slot, same fee, the instruction, Track → detail ----
        waitFor(hasText("Order placed"), 30_000)
        assert(present(hasText("12 – 3 PM · Free"))) { "confirmation lost the chosen slot" }
        assert(present(hasText("Leave at my door"))) { "confirmation lost the instruction" }
        snapshot("c2_04_confirmation")
        click(hasText("Track order"))

        // ---- Order detail: the receipt agrees with everything above ----
        waitFor(hasText("Order details"))
        assert(present(hasText("12 – 3 PM · Free"))) { "receipt lost the chosen slot" }
        assert(present(hasText("Total paid"))) { "receipt missing total" }
        assert(present(hasText("Leave at my door"))) { "receipt lost the instruction" }
        assert(present(hasText("Need help"))) { "order-scoped help missing" }
        snapshot("c2_05_order_detail")
    }
}
