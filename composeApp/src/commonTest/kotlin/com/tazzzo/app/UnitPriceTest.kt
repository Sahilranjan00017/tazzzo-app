package com.tazzzo.app

import com.tazzzo.app.data.MockCatalog
import com.tazzzo.app.data.model.unitPriceLabel
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Per-unit price is the one comparison figure a customer can check themselves,
 * so it is derived from the pack size and never authored. These tests pin the
 * cases that would otherwise quietly lie.
 */
class UnitPriceTest {

    @Test fun mass_and_volume_normalise_to_kg_and_litre() {
        assertEquals("₹32/kg", unitPriceLabel(r(32), "1 kg"))
        assertEquals("₹44/kg", unitPriceLabel(r(22), "500 g"))
        assertEquals("₹56/L", unitPriceLabel(r(56), "1 L"))
        assertEquals("₹58/L", unitPriceLabel(r(29), "500 ml"))
    }

    @Test fun a_multipack_counts_every_pack_not_just_the_last_figure() {
        // "4 x 100 g" is 400 g. Reading only the trailing 100 g would claim a
        // per-kilo price four times too high.
        assertEquals("₹250/kg", unitPriceLabel(r(100), "4 x 100 g"))
    }

    @Test fun pieces_divide_only_when_there_is_more_than_one() {
        assertEquals("₹7/pc", unitPriceLabel(r(42), "6 pcs"))
        assertNull(unitPriceLabel(r(75), "1 pc"))
    }

    @Test fun an_unmeasurable_pack_gets_no_label_rather_than_a_guess() {
        assertNull(unitPriceLabel(r(45), "180 pages"))
        assertNull(unitPriceLabel(r(899), "1 unit"))
        assertNull(unitPriceLabel(r(449), "1 set"))
        assertNull(unitPriceLabel(r(25), "15 tabs"))
    }

    @Test fun a_parenthesised_weight_wins_over_the_piece_count() {
        // "4 pcs (approx 500 g)" — the weight is the honest denominator.
        assertEquals("₹238/kg", unitPriceLabel(r(119), "4 pcs (approx 500 g)"))
    }

    @Test fun every_catalogue_sku_either_derives_a_label_or_declines_cleanly() {
        MockCatalog.products.forEach { p ->
            val label = p.unitPriceLabel
            if (label != null) {
                assertTrue(label.startsWith("₹"), "${p.id} produced '$label'")
                assertTrue(label.contains("/"), "${p.id} produced '$label'")
            }
        }
    }
}
