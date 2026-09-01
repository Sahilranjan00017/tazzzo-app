package com.tazzzo.app

import com.tazzzo.app.data.repository.MockCatalogRepository
import com.tazzzo.app.ui.common.categoryArtTiles
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals

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

    @Test fun art_table_covers_the_taxonomy_in_order() = runTest {
        val categories = MockCatalogRepository().getCategories()
        assertEquals(
            categories.map { it.id },
            categoryArtTiles.map { it.id },
            "brand wall tiles must match the catalogue's categories, in order"
        )
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
