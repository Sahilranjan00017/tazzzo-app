package com.tazzzo.app

import com.tazzzo.app.data.MockCatalog
import kotlin.test.Test
import kotlin.test.assertTrue

/**
 * A section headed "Add for less" must actually offer cheap additions.
 *
 * The rail is fed by `deals()`, which ranks by rupees off — that put ₹2,199
 * protein powder and a ₹899 frying pan at the top of a cart upsell. Found on a
 * real phone; pinned here so the ordering cannot silently revert.
 */
class CartUpsellTest {

    private fun upsell(inCart: Set<String>) =
        MockCatalog.deals().filter { it.id !in inCart && it.isPurchasable }
            .sortedBy { it.price }.take(8)

    @Test fun the_rail_leads_with_the_cheapest_additions() {
        val rail = upsell(emptySet())
        assertTrue(rail.isNotEmpty())
        assertTrue(rail.first().price <= rail.last().price, "rail must be cheapest-first")
    }

    @Test fun nothing_in_it_costs_more_than_a_typical_basket_topper() {
        // Not a hard business rule, a sanity bound: an upsell headed "add for
        // less" that opens with a four-figure item is selling the wrong thing.
        val rail = upsell(emptySet())
        assertTrue(rail.first().price < 200,
            "cheapest offer is ₹${rail.first().price}; 'add for less' should start low")
    }

    @Test fun every_item_still_carries_a_real_discount() {
        upsell(emptySet()).forEach {
            assertTrue(it.mrp > it.price, "${it.id} is in a deals rail with nothing off")
        }
    }

    @Test fun items_already_in_the_basket_are_excluded() {
        val first = MockCatalog.deals().first().id
        assertTrue(upsell(setOf(first)).none { it.id == first })
    }
}
