package com.tazzzo.app

import com.tazzzo.app.data.MockCatalog
import com.tazzzo.app.ui.common.ProductFilters
import com.tazzzo.app.ui.common.ProductFiltersSaver
import com.tazzzo.app.ui.common.SortOption
import com.tazzzo.app.ui.common.applyFilters
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * "Deals" must mean a saving the customer can check against an MRP, and the
 * selection must survive navigation like every other filter.
 */
class DealsFilterTest {

    @Test fun deals_keeps_only_items_priced_below_their_own_mrp() {
        val out = MockCatalog.products.applyFilters(ProductFilters(dealsOnly = true))
        assertTrue(out.isNotEmpty(), "the catalogue should contain genuine discounts")
        assertTrue(out.all { it.mrp > it.price })
        val excluded = MockCatalog.products.filter { it.mrp <= it.price }
        excluded.forEach { assertFalse(it in out, "${it.id} has nothing off and must not be a deal") }
    }

    @Test fun deals_composes_with_the_other_filters_rather_than_replacing_them() {
        val brand = MockCatalog.products.first { it.mrp > it.price }.brand
        val out = MockCatalog.products.applyFilters(
            ProductFilters(dealsOnly = true, brands = setOf(brand), inStockOnly = true)
        )
        assertTrue(out.all { it.mrp > it.price && it.brand == brand && it.isPurchasable })
    }

    @Test fun the_chip_counts_towards_the_active_filter_badge() {
        assertEquals(0, ProductFilters().activeCount)
        assertEquals(1, ProductFilters(dealsOnly = true).activeCount)
        assertEquals(3, ProductFilters(dealsOnly = true, inStockOnly = true, sort = SortOption.PRICE_LOW).activeCount)
    }

    @Test fun the_selection_survives_a_save_and_restore() {
        val original = ProductFilters(sort = SortOption.DISCOUNT, inStockOnly = true,
            brands = setOf("Amul"), dealsOnly = true)
        var restored: ProductFilters? = null
        with(ProductFiltersSaver) {
            val scope = androidx.compose.runtime.saveable.SaverScope { true }
            val saved = scope.run { save(original) }
            restored = saved?.let { restore(it) }
        }
        assertEquals(original, restored)
    }

    @Test fun a_bundle_written_before_this_filter_existed_still_restores() {
        // Older builds saved three entries. Restoring must not crash — a state
        // bundle outlives the build that wrote it.
        val legacy = listOf(SortOption.PRICE_LOW.name, true, listOf("Amul"))
        val restored = ProductFiltersSaver.run { restore(legacy) }
        assertEquals(SortOption.PRICE_LOW, restored?.sort)
        assertFalse(restored?.dealsOnly ?: true)
    }
}
