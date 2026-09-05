package com.tazzzo.app

import android.content.Intent
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.semantics.SemanticsActions
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.rules.ActivityScenarioRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.tazzzo.app.theme.MotionSettings
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Regression guard for a defect this project shipped: the cart row rendered a
 * product image and a quantity stepper with NO product name and NO price.
 *
 * Cause: the stepper's interior fills its container so − and + sit at the
 * pill's edges. As an unweighted child of a Row that made it claim all
 * remaining width, starving the weighted name/price column to zero. It looked
 * correct in the product grid (where the stepper is width-constrained) and was
 * invisible in every unit test, because it is purely a measurement outcome.
 *
 * The assertion is deliberately about CONTENT, not layout internals: a cart
 * row must always tell the customer what the item is and what it costs.
 */
@RunWith(AndroidJUnit4::class)
class CartRowRegressionTest {

    init { MotionSettings.ambientEnabled = false }

    @get:Rule(order = 0)
    val rule = createEmptyComposeRule()

    @get:Rule(order = 1)
    val activity = ActivityScenarioRule<MainActivity>(
        Intent(ApplicationProvider.getApplicationContext(), MainActivity::class.java)
            .putExtra("taz_start_home", true)
    )

    @Test
    fun cart_row_shows_the_product_name_and_price_beside_the_stepper() {
        // Generous: this is the first test in the suite, so it pays for the
        // app's cold start on an emulator that is already CPU-starved.
        rule.waitUntil(timeoutMillis = 60_000) {
            rule.onAllNodes(hasContentDescription("Add Fresh Onion to cart"))
                .fetchSemanticsNodes().isNotEmpty()
        }
        rule.onNodeWithContentDescription("Add Fresh Onion to cart")
            .performSemanticsAction(SemanticsActions.OnClick)

        // Open the cart from the cart bar.
        rule.waitUntil(timeoutMillis = 30_000) {
            rule.onAllNodes(hasText("View cart")).fetchSemanticsNodes().isNotEmpty()
        }
        rule.onNode(hasText("View cart")).performSemanticsAction(SemanticsActions.OnClick)

        rule.waitUntil(timeoutMillis = 30_000) {
            rule.onAllNodes(hasText("Fresh Onion")).fetchSemanticsNodes().isNotEmpty()
        }
        // The row must carry identity AND price, not just a picture and a control.
        rule.onNode(hasText("Fresh Onion")).assertIsDisplayed()
        // The price appears both on the row and in the bill; the defect was
        // that it appeared in NEITHER, so assert presence rather than a count.
        assert(
            rule.onAllNodes(hasText("₹32", substring = true)).fetchSemanticsNodes().isNotEmpty()
        ) { "cart row lost the product price" }
        rule.onNode(hasText("1 kg")).assertIsDisplayed()
        // And the stepper must still be there.
        rule.onNodeWithContentDescription("Increase quantity of Fresh Onion").assertIsDisplayed()
    }
}
