package com.tazzzo.app

import android.content.Intent
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.semantics.SemanticsActions
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.rules.ActivityScenarioRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.tazzzo.app.theme.MotionSettings
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * E2 navigation and continuity journeys.
 *
 * These assert the thing the customer actually feels: that going somewhere and
 * coming back does not throw away what they were doing. Before E2 every one of
 * these lost state, because the screen host disposed the outgoing composable
 * and nothing was saveable.
 *
 * Ambient motion is off (see MotionSettings) so Compose can reach idle; system
 * back is driven through the real activity, not simulated.
 */
@RunWith(AndroidJUnit4::class)
class NavigationJourneyTest {

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

    private fun systemBack() {
        InstrumentationRegistry.getInstrumentation().runOnMainSync {
            activity.scenario.onActivity { it.onBackPressedDispatcher.onBackPressed() }
        }
        rule.waitForIdle()
    }

    /**
     * 60s, matching every other class in the suite.
     *
     * This class was the lone outlier at 20s, and
     * `category_scroll_position_survives_opening_a_product_and_coming_back`
     * timed out once under full-suite load on the 2-core emulator while passing
     * twice in isolation immediately afterwards — contention, not a defect.
     * This is aligning one straggler to the established value, NOT the
     * open-ended timeout inflation the testing notes warn about: if it fails
     * again the next step is diagnosis, not 90s.
     */
    private fun waitFor(matcher: androidx.compose.ui.test.SemanticsMatcher, ms: Long = 60_000) {
        rule.waitUntil(timeoutMillis = ms) {
            rule.onAllNodes(matcher).fetchSemanticsNodes().isNotEmpty()
        }
    }

    @Before
    fun landOnHome() { waitFor(hasContentDescription("Home")) }

    /**
     * Since UI-02 the aisle tiles live on the Shop tab (Home is the editorial landing page and no longer lists them), and the
     * old "Categories" tab is now "Shop". Journeys that start from an aisle therefore enter through that tab.
     */
    private fun openShop() {
        rule.onNodeWithContentDescription("Shop").performClick()
        rule.onNodeWithContentDescription("Shop").assertIsSelected()
        waitFor(hasContentDescription("Vegetables & Fruits"))
    }

    // ---------------------------------------------------------------- journeys

    @Test
    fun shop_to_category_to_pdp_and_back_returns_through_the_stack() {
        openShop()
        rule.onAllNodes(hasContentDescription("Vegetables & Fruits") and hasClickAction()).onFirst()
            .performSemanticsAction(SemanticsActions.OnClick)

        waitFor(hasText("Fresh Onion"))
        rule.onNode(hasText("Fresh Onion")).performSemanticsAction(SemanticsActions.OnClick)
        waitFor(hasText("Product details"))

        systemBack()
        // Back lands on the category listing, NOT on Home.
        waitFor(hasText("Fresh Onion"))
        rule.onAllNodes(hasText("Product details")).fetchSemanticsNodes().let {
            assert(it.isEmpty()) { "system back should have left the PDP" }
        }

        systemBack()
        waitFor(hasContentDescription("Shop"))
        rule.onNodeWithContentDescription("Shop").assertIsSelected()   // back returns to the tab the journey started from
    }

    @Test
    fun search_query_survives_opening_a_product_and_coming_back() {
        waitFor(hasContentDescription("Search products"))
        rule.onAllNodes(hasContentDescription("Search products") and hasClickAction())
            .onFirst().performSemanticsAction(SemanticsActions.OnClick)

        // Now on the search screen: the field carries the same label.
        waitFor(hasContentDescription("Search products") and hasSetTextAction())
        rule.onAllNodes(hasContentDescription("Search products") and hasSetTextAction())
            .onFirst().performTextInput("onion")
        waitFor(hasText("Fresh Onion"))

        rule.onNode(hasText("Fresh Onion")).performSemanticsAction(SemanticsActions.OnClick)
        waitFor(hasText("Product details"))
        systemBack()

        // The query is still there — the customer does not retype it.
        waitFor(hasText("onion", substring = true))
        rule.onAllNodes(hasText("onion", substring = true)).fetchSemanticsNodes().let {
            assert(it.isNotEmpty()) { "search query was lost on back" }
        }
    }

    @Test
    fun rapid_taps_do_not_stack_duplicate_destinations() {
        openShop()
        // Five taps as fast as the harness can issue them. Once the first tap
        // navigates away the node is gone, so later taps legitimately fail to
        // find it — that is the harness, not the app, and is swallowed here.
        // What matters is the resulting back stack, asserted below.
        repeat(5) {
            runCatching {
                rule.onAllNodes(hasContentDescription("Vegetables & Fruits") and hasClickAction()).onFirst()
                    .performSemanticsAction(SemanticsActions.OnClick)
            }
        }
        waitFor(hasText("Fresh Onion"))
        // One back press must return to the Shop tab. If the guard failed, the stack
        // holds five copies and this lands on the category listing again.
        systemBack()
        waitFor(hasContentDescription("Shop"))
        rule.onNodeWithContentDescription("Shop").assertIsSelected()
    }

    @Test
    fun system_back_dismisses_the_voice_sheet_before_popping_the_screen() {
        // Two nodes carry this label — the header mic button and the banner.
        // Only the actionable one is wanted.
        waitFor(hasContentDescription("Voice shopping — coming soon") and hasClickAction())
        rule.onAllNodes(hasContentDescription("Voice shopping — coming soon") and hasClickAction())
            .onFirst().performSemanticsAction(SemanticsActions.OnClick)
        waitFor(hasText("Voice", substring = true))

        systemBack()
        // Sheet dismissed, and we are still on Home — not popped past it.
        rule.onNodeWithContentDescription("Home").assertIsSelected()
    }

    @Test
    fun category_scroll_position_survives_opening_a_product_and_coming_back() {
        openShop()
        rule.onAllNodes(hasContentDescription("Vegetables & Fruits") and hasClickAction()).onFirst()
            .performSemanticsAction(SemanticsActions.OnClick)
        waitFor(hasText("Fresh Onion"))

        // Scroll the grid down until a product that is NOT in the first screen
        // is visible, and remember it. If scroll resets on back, this node will
        // be gone — that is exactly the regression being guarded.
        val deepProduct = "Palak (Spinach)"
        rule.onNodeWithTag("categoryGrid").performScrollToNode(hasText(deepProduct))
        rule.waitForIdle()
        val yBefore = rule.onNode(hasText(deepProduct)).fetchSemanticsNode().boundsInRoot.top

        rule.onNode(hasText(deepProduct)).performSemanticsAction(SemanticsActions.OnClick)
        waitFor(hasText("Product details"))
        systemBack()

        // Same product, still on screen, at the same offset: the customer is
        // returned to where they were rather than to the top of the aisle.
        waitFor(hasText(deepProduct))
        val yAfter = rule.onNode(hasText(deepProduct)).fetchSemanticsNode().boundsInRoot.top
        val drift = kotlin.math.abs(yAfter - yBefore)
        assert(drift < 8f) { "scroll position drifted by ${drift}px on back" }
    }

    @Test
    fun tab_switching_preserves_each_tabs_state() {
        rule.onNodeWithContentDescription("Shop").performClick()
        rule.onNodeWithContentDescription("Shop").assertIsSelected()
        waitFor(hasContentDescription("Vegetables & Fruits"))
        rule.onNodeWithContentDescription("Home").performClick()
        rule.onNodeWithContentDescription("Home").assertIsSelected()
        // Home still has its content; it was not rebuilt into a loading state.
        waitFor(hasTestTag("homeFeed"))
        // ...and Shop kept its own: switching back shows the aisles again without a reload.
        rule.onNodeWithContentDescription("Shop").performClick()
        waitFor(hasContentDescription("Vegetables & Fruits"))
    }
}
