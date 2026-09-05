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
import com.tazzzo.app.config.PromotionConfig
import com.tazzzo.app.data.MockCatalog
import com.tazzzo.app.data.model.PromotionAudience
import com.tazzzo.app.theme.MotionSettings
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

/**
 * Deals is a destination, and everything on it must be a saving the customer
 * could verify against the pack.
 *
 * The load-bearing assertion is the last one: a signed-out shopper must not be
 * shown a members-only offer. Listing an offer someone cannot use is a broken
 * promise dressed as a nudge.
 */
@RunWith(AndroidJUnit4::class)
class DealsTabTest {

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

    @Test fun deals_is_a_destination_showing_only_verifiable_savings() {
        waitFor(hasContentDescription("Home"))
        waitFor(hasContentDescription("Vegetables & Fruits") and hasClickAction())

        // ---- Reachable as its own tab, not a filter buried in a list ----
        waitFor(hasContentDescription("Deals"))
        click(hasContentDescription("Deals"))
        waitFor(hasText("Everything with money off today"), 30_000)
        // The header sits OUTSIDE the load state, so it appears while the grid
        // is still loading. Waiting on it alone raced the content and failed on
        // a band that was about to render. Wait for something inside the loaded
        // grid before asserting anything about the grid.
        waitFor(hasText("Biggest savings"), 30_000)
        snapshot("n2_00_deals_loaded")

        // ---- The dated campaign band leads ----
        CampaignConfig.current?.let { campaign ->
            assert(present(hasText(campaign.title))) { "campaign band missing from Deals" }
            campaign.validUntilLabel?.let {
                assert(present(hasText(it.uppercase()))) { "campaign validity not stated" }
            }
        }

        // ---- Offers state the condition that gates them ----
        val everyone = PromotionConfig.active.filter { it.audience != PromotionAudience.MEMBERS_ONLY }
        assert(everyone.isNotEmpty()) { "fixture must contain at least one open offer" }
        assert(present(hasText("Offers you can use"))) { "offers section missing" }
        everyone.firstOrNull { it.minOrderRupees > 0 }?.let { gated ->
            rule.onNodeWithTag("dealsGrid").performScrollToNode(hasText(gated.title))
            rule.waitForIdle()
            assert(present(hasText("On orders above ₹${gated.minOrderRupees}", substring = true))) {
                "the minimum spend on '${gated.title}' must be stated up front, not at the till"
            }
        }

        // ---- A members-only offer must NOT be dangled at a non-member ----
        PromotionConfig.active.firstOrNull { it.audience == PromotionAudience.MEMBERS_ONLY }?.let { members ->
            assert(!present(hasText(members.title))) {
                "members-only offer '${members.title}' shown to a non-member"
            }
        }

        // ---- The savings grid: deepest rupee saving first, all real ----
        rule.onNodeWithTag("dealsGrid").performScrollToNode(hasText("Biggest savings"))
        rule.waitForIdle()
        val top = MockCatalog.deals().first()
        assert(top.mrp > top.price) { "the deals list must only contain genuine discounts" }
        rule.onNodeWithTag("dealsGrid").performScrollToNode(hasText(top.name))
        rule.waitForIdle()
        assert(present(hasText("₹${top.mrp - top.price} OFF"))) {
            "the deepest saving (${top.name}) must lead and state its rupees off"
        }
        snapshot("n2_01_deals_tab")
    }
}
