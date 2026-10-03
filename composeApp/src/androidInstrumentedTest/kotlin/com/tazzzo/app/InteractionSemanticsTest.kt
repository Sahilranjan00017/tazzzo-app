package com.tazzzo.app

import android.content.Intent
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotSelected
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.printToLog
import androidx.compose.ui.test.performScrollTo
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.rules.ActivityScenarioRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.tazzzo.app.theme.MotionSettings
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * On-device semantics verification for the E1 interaction substrate.
 *
 * Asserts against the SAME semantics tree TalkBack reads. `uiautomator dump`
 * is not an oracle for Compose accessibility (it flattened the nav into three
 * nodes with identical bounds and hid the selected state); this is.
 *
 * Determinism:
 *  - Launched with the existing `taz_start_home` QA extra, so the run lands on
 *    Home with no splash, no login wall, no coach-mark overlay.
 *  - Ambient motion is switched off before the activity exists. A surface with
 *    a perpetual animation never lets Compose idle, and the harness then times
 *    out on "pending recompositions" — which is how the six infinite floats in
 *    the voice banner were found.
 */
@RunWith(AndroidJUnit4::class)
class InteractionSemanticsTest {

    init {
        TestState.reset()                      // clean persisted store per class
        MotionSettings.ambientEnabled = false
    }

    // Lower order = outermost = runs first. The Compose rule must be armed
    // BEFORE the activity calls setContent, or it finds no hierarchy.
    @get:Rule(order = 0)
    val rule = createEmptyComposeRule()

    @get:Rule(order = 1)
    val activity = ActivityScenarioRule<MainActivity>(
        Intent(ApplicationProvider.getApplicationContext(), MainActivity::class.java)
            .putExtra("taz_start_home", true)
    )

    @Before
    fun landOnHome() {
        rule.waitUntil(timeoutMillis = 20_000) {
            rule.onAllNodes(hasContentDescription("Home")).fetchSemanticsNodes().isNotEmpty()
        }
    }

    @Test
    fun bottom_nav_publishes_selected_state_to_the_accessibility_tree() {
        // Absent before E1: a screen reader said "Home" but never "selected".
        rule.onNodeWithContentDescription("Home").assertIsSelected()
        // Tabs since UI-02: Home, Shop, Deals, Orders, Order Again, Profile (formerly Categories / Account).
        for (tab in listOf("Shop", "Deals", "Orders", "Order Again", "Profile"))
            rule.onNodeWithContentDescription(tab).assertIsNotSelected()
    }

    @Test
    fun switching_tab_moves_the_selected_state() {
        rule.onNodeWithContentDescription("Shop").performClick()
        rule.onNodeWithContentDescription("Shop").assertIsSelected()
        rule.onNodeWithContentDescription("Home").assertIsNotSelected()
    }

    @Test
    fun add_control_transforms_into_a_stepper_in_place() {
        // Home is a LazyColumn: the Bestsellers rail (and its ADD controls) is not
        // composed until scrolled into range. Scroll first, then wait.
        rule.waitUntil(timeoutMillis = 20_000) {
            rule.onAllNodes(hasTestTag("homeFeed")).fetchSemanticsNodes().isNotEmpty()
        }
        rule.onNodeWithTag("homeFeed").performScrollToNode(
            hasContentDescription("Add Fresh Onion to cart") or hasContentDescription("Increase quantity of Fresh Onion")
        )
        rule.waitUntil(timeoutMillis = 20_000) {
            rule.onAllNodes(hasContentDescription("Add Fresh Onion to cart"))
                .fetchSemanticsNodes().isNotEmpty()
        }
        // Invoke the accessibility OnClick action — the path a screen reader
        // takes. This verifies the SEMANTIC contract of the control: one node
        // that is "Add … to cart" becomes nodes that are "Increase/Decrease
        // quantity of …", with the cart bar reporting the line.
        //
        // Deliberately NOT performScrollTo()+performClick(): on this
        // LazyRow-inside-LazyColumn the scroll leaves the item with zero
        // semantic bounds (logged during E1), so touch injection cannot land.
        // The touch path was verified by hand on the emulator with screenshots
        // (docs/screenshots/e1-after/). Geometry assertions belong to E2's
        // scroll/continuity work, where that lazy nesting is revisited.
        // Filter on the ACTIONABLE node. After a lazy scroll a label can bind to a
        // node without the click action (E1 finding) — every journey test already
        // filters this way; this older test now does too.
        rule.waitForIdle()
        rule.onAllNodes(hasContentDescription("Add Fresh Onion to cart") and hasClickAction()).onFirst()
            .performSemanticsAction(SemanticsActions.OnClick)
        rule.waitUntil(timeoutMillis = 5_000) {
            rule.onAllNodes(hasContentDescription("Increase quantity of Fresh Onion"))
                .fetchSemanticsNodes().isNotEmpty()
        }
        // Counts, not singletons: MockOrderRepository is process-global, so a test
        // that placed an order earlier in the run puts Fresh Onion in Home's
        // "Order again" rail as well — two steppers for one product is correct
        // UI, and onNodeWith… would throw on it.
        fun count(m: androidx.compose.ui.test.SemanticsMatcher) = rule.onAllNodes(m).fetchSemanticsNodes().size
        assert(count(hasContentDescription("Increase quantity of Fresh Onion")) >= 1) { "stepper + missing" }
        assert(count(hasContentDescription("Decrease quantity of Fresh Onion")) >= 1) { "stepper − missing" }
        assert(count(hasContentDescription("Add Fresh Onion to cart")) == 0) { "ADD should have transformed away" }
        assert(count(hasText("1 item", substring = true)) >= 1) { "cart bar should report 1 item" }
    }
}
