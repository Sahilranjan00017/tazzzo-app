package com.tazzzo.app

import com.tazzzo.app.data.MockCatalog
import com.tazzzo.app.data.model.CartLine
import com.tazzzo.app.data.model.Product
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * The Master List ranks by how many separate ORDERS a product appears in, not
 * by units bought. One bulk purchase of ten must not outrank a staple bought
 * every week — the list is evidence of a habit, and a habit is a repeat.
 */
class MasterListTest {

    private fun p(id: String) = MockCatalog.products.first { it.id == id }

    /** Mirrors the screen's ranking, without Compose. */
    private fun rank(orders: List<List<CartLine>>): List<Pair<Product, Int>> =
        orders.flatMap { lines -> lines.map { it.product }.distinctBy { it.id } }
            .groupBy { it.id }
            .map { (_, items) -> items.first() to items.size }
            .sortedWith(compareByDescending<Pair<Product, Int>> { it.second }.thenBy { it.first.name })

    @Test fun a_weekly_staple_outranks_one_bulk_purchase() {
        val staple = p("p1")   // in three separate orders, one unit each
        val bulk = p("p4")     // one order, ten units
        val ranked = rank(listOf(
            listOf(CartLine(staple, 1)),
            listOf(CartLine(staple, 1)),
            listOf(CartLine(staple, 1), CartLine(bulk, 10)),
        ))
        assertEquals(staple.id, ranked.first().first.id, "the repeat purchase must lead")
        assertEquals(3, ranked.first().second)
        assertEquals(1, ranked.first { it.first.id == bulk.id }.second)
    }

    @Test fun one_order_cannot_count_a_product_twice() {
        // Two lines of the same product in a single order is still one order.
        val ranked = rank(listOf(listOf(CartLine(p("p1"), 2), CartLine(p("p1"), 3))))
        assertEquals(1, ranked.single().second)
    }

    @Test fun no_history_means_no_list_rather_than_borrowed_bestsellers() {
        // Presenting strangers' popular items as "your list" is a claim about
        // this customer the data does not support.
        assertTrue(rank(emptyList()).isEmpty())
    }

    @Test fun ties_break_alphabetically_so_the_order_is_stable() {
        val a = p("p1"); val b = p("p4")
        val once = rank(listOf(listOf(CartLine(a, 1)), listOf(CartLine(b, 1))))
        assertEquals(once.map { it.first.name }, once.map { it.first.name }.sorted())
    }
}
