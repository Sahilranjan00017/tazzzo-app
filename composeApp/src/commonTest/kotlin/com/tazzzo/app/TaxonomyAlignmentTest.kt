package com.tazzzo.app

import com.tazzzo.app.data.MockCatalog
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Pins the app's mock aisle to taxonomy v0.9.0 where a real node exists.
 *
 * The mock is allowed to be ahead of the backend, but not silently: a SKU that
 * carries no vertical id is one the catalogue service cannot serve today, and
 * that set must stay visible rather than blend in.
 */
class TaxonomyAlignmentTest {

    @Test fun pooja_mirrors_the_real_category_shape() {
        val pooja = MockCatalog.categories.single { it.id == "pooja" }
        assertEquals("Pooja & Religious Needs", pooja.name, "name must match the TZC node verbatim")
        assertEquals(listOf("Daily Pooja", "Pooja Materials"), pooja.subcategories.map { it.name })
    }

    @Test fun every_pooja_sku_maps_to_a_distinct_real_vertical() {
        val ids = MockCatalog.products.filter { it.categoryId == "pooja" }.map { it.verticalId }
        assertEquals(9, ids.size, "one SKU per vertical in the Pooja branch")
        assertEquals(ids.size, ids.toSet().size, "two SKUs must not claim the same vertical")
        ids.forEach { id ->
            assertTrue(id != null && Regex("^TZV-\\d{6}$").matches(id),
                "vertical id must be the bare id, never the CSV's '<id> (provisional)' form: $id")
        }
        val expected = (225..233).map { "TZV-" + it.toString().padStart(6, '0') }.toSet()
        assertEquals(expected, ids.toSet())
    }

    @Test fun skus_with_no_backend_vertical_are_the_known_excluded_aisles() {
        // Fresh produce, dairy and pet care were excluded from v0.9.0 by
        // recorded decision. This test does not object to them existing; it
        // objects to anyone forgetting that the backend cannot serve them.
        val unmapped = MockCatalog.products.filter { it.verticalId == null }.map { it.categoryId }.toSet()
        assertTrue("fruits" in unmapped && "dairy" in unmapped && "pet" in unmapped,
            "the excluded aisles must remain visibly unmapped, not quietly given ids")
    }
}
