package com.tazzzo.app

import com.tazzzo.app.data.repository.MockCatalogRepository
import com.tazzzo.app.ui.common.categoryArtTiles
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * The login screen's brand wall is drawn from a local design-asset table so it
 * renders with no network call and no failure state. That table therefore
 * carries copies of category ids, labels and tints.
 *
 * These tests stop the copy from silently drifting away from the taxonomy
 * while both exist. When the catalogue becomes remote, a mismatch here is the
 * signal to refresh the bundled art — not to start blocking login on a
 * catalogue fetch.
 */
class CategoryArtTilesTest {

    @Test fun art_table_is_a_subset_of_the_taxonomy_in_taxonomy_order() = runTest {
        // A PARTIAL cover by design: a category with no vetted, brand-free,
        // licence-confirmed photograph renders the tinted emoji tile instead.
        // What must still hold is that every entry names a real category, none
        // repeats, and the order follows the taxonomy so the wall reads as the
        // aisle list rather than an arbitrary pile.
        val order = MockCatalogRepository().getCategories().map { it.id }
        val tiles = categoryArtTiles.map { it.id }
        assertTrue(tiles.all { it in order }, "art table names a category that does not exist")
        assertEquals(tiles.distinct(), tiles, "a category has two tiles")
        assertEquals(tiles.sortedBy { order.indexOf(it) }, tiles, "tiles are out of taxonomy order")
    }

    @Test fun no_withdrawn_tile_creeps_back() = runTest {
        // These four were withdrawn for trade dress, an unverified licence, or
        // showing an identifiable person. Re-adding one needs a new asset and a
        // new attribution line, not a revert.
        val withdrawn = setOf("cleaning", "oil", "skincare", "baby")
        val back = categoryArtTiles.map { it.id }.filter { it in withdrawn }
        assertTrue(back.isEmpty(), "withdrawn category art is back: $back")
    }

    @Test fun art_table_labels_and_tints_match_the_taxonomy() = runTest {
        val byId = MockCatalogRepository().getCategories().associateBy { it.id }
        for (tile in categoryArtTiles) {
            val category = byId.getValue(tile.id)
            assertEquals(category.name, tile.label, "label drift for ${tile.id}")
            assertEquals(category.tint, tile.tint, "tint drift for ${tile.id}")
        }
    }

    @Test fun art_table_has_no_duplicate_ids() {
        assertEquals(categoryArtTiles.size, categoryArtTiles.map { it.id }.toSet().size)
    }
}
