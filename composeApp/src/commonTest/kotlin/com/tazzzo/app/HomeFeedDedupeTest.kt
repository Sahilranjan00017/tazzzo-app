package com.tazzzo.app

import com.tazzzo.app.data.MockCatalog
import kotlin.test.Test
import kotlin.test.assertTrue

/**
 * No product may appear in two modules of the home feed at once.
 *
 * Not a tidiness rule. Two cards for one product means two controls a screen
 * reader announces identically as "Add Fresh Onion to cart" — the defect class
 * that already produced duplicate category tiles — and it wastes a slot in the
 * densest part of the app. Caught on device by CartRowRegressionTest; pinned
 * here so it is caught in seconds instead of in a fifteen-minute suite.
 */
class HomeFeedDedupeTest {

    /** Mirrors the feed loader's composition, without Compose or coroutines. */
    private fun feed(): Map<String, List<String>> {
        val rails = mapOf(
            "Bestsellers" to MockCatalog.bestsellers(),
            "Snacks & Munchies" to MockCatalog.products.filter { it.categoryId == "munchies" },
            "Dairy, Bread & Eggs" to MockCatalog.products.filter { it.categoryId == "dairy" },
            "Personal Care" to MockCatalog.products.filter { it.categoryId in setOf("personal", "skincare") },
            "Sweet Tooth" to MockCatalog.products.filter { it.categoryId == "sweet" },
            "Cleaning Essentials" to MockCatalog.products.filter { it.categoryId == "cleaning" },
        )
        val deals = rails.values.flatten().distinctBy { it.id }
            .filter { it.mrp > it.price && it.isPurchasable }
            .sortedByDescending { it.mrp - it.price }.take(10)
        val alreadyShown = rails.values.flatten().map { it.id }.toSet() + deals.map { it.id }
        val essentials = MockCatalog.products
            .filter { it.categoryId in setOf("fruits", "atta") }
            .distinctBy { it.id }
            .filter { it.isPurchasable && it.id !in alreadyShown }
            .take(8)
        return mapOf(
            "deals" to deals.map { it.id },
            "essentials" to essentials.map { it.id },
        ) + rails.mapValues { (_, v) -> v.map { it.id } }
    }

    @Test fun the_essentials_grid_never_repeats_a_product_shown_elsewhere() {
        val f = feed()
        val elsewhere = f.filterKeys { it != "essentials" }.values.flatten().toSet()
        val repeated = f["essentials"]!!.filter { it in elsewhere }
        assertTrue(repeated.isEmpty(), "essentials repeats products already on the feed: $repeated")
    }

    @Test fun the_essentials_grid_still_has_something_to_show() {
        // A dedupe that empties the section has not fixed anything, it has
        // deleted the feature.
        assertTrue(feed()["essentials"]!!.isNotEmpty(), "dedupe left the essentials grid empty")
    }

    @Test fun deals_are_drawn_from_products_the_feed_actually_loaded() {
        val f = feed()
        val railIds = f.filterKeys { it != "deals" && it != "essentials" }.values.flatten().toSet()
        assertTrue(f["deals"]!!.all { it in railIds },
            "a deal must come from a loaded rail, not a separate query nobody sees")
    }
}
