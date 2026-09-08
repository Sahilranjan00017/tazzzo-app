package com.tazzzo.app

import com.tazzzo.app.data.MockCatalog
import com.tazzzo.app.ui.common.bundledProductArtIds
import kotlin.test.Test
import kotlin.test.assertTrue

/** The bundled photograph table must only ever name products that exist. */
class ProductArtTest {
    @Test fun every_bundled_photo_belongs_to_a_real_product() {
        val ids = MockCatalog.products.map { it.id }.toSet()
        val strays = bundledProductArtIds.filter { it !in ids }
        assertTrue(strays.isEmpty(), "photos for products that do not exist: $strays")
    }
    @Test fun the_bundle_covers_a_meaningful_share_of_the_catalogue() {
        // Not a target, a floor: if this drops, photos were removed and the
        // reason should be in IMAGE_ATTRIBUTIONS.md.
        assertTrue(bundledProductArtIds.size >= 30, "only ${bundledProductArtIds.size} bundled photos")
    }
}
