package com.tazzzo.app

import com.tazzzo.app.data.MockCatalog
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Category tiles print a count. It must be the real one.
 *
 * The supplied mockups showed "340+" and "1,840 Items" on a 72-SKU catalogue.
 * These tests exist so nobody can quietly reintroduce a decorative number.
 */
class CatalogCountsTest {

    @Test fun every_category_count_equals_the_products_actually_in_it() {
        val counts = MockCatalog.counts()
        MockCatalog.categories.forEach { cat ->
            val actual = MockCatalog.products.count { it.categoryId == cat.id }
            assertEquals(actual, counts[cat.id] ?: 0, "count wrong for ${cat.id}")
        }
    }

    @Test fun sub_category_counts_sum_to_their_parent() {
        val counts = MockCatalog.counts()
        MockCatalog.categories.forEach { cat ->
            val childSum = cat.subcategories.sumOf { counts[it.id] ?: 0 }
            val parent = counts[cat.id] ?: 0
            assertTrue(childSum <= parent,
                "${cat.id}: sub-counts ($childSum) exceed the category ($parent)")
        }
    }

    @Test fun a_category_with_no_products_reports_nothing_rather_than_zero_padding() {
        val counts = MockCatalog.counts()
        val empty = MockCatalog.categories.filter { c ->
            MockCatalog.products.none { it.categoryId == c.id }
        }
        empty.forEach { assertNull(counts[it.id], "${it.id} is empty and must not carry a count") }
    }

    @Test fun the_totals_reconcile_with_the_catalogue() {
        val counts = MockCatalog.counts()
        val perCategory = MockCatalog.categories.sumOf { counts[it.id] ?: 0 }
        assertEquals(MockCatalog.products.size, perCategory,
            "every product must belong to exactly one counted category")
    }
}
