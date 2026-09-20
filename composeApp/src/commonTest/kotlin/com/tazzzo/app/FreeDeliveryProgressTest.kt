package com.tazzzo.app

import com.tazzzo.app.config.AppConfig
import com.tazzzo.app.config.freeDeliveryProgress
import com.tazzzo.app.config.priceBandLabel
import com.tazzzo.app.data.model.BillSummary
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * The cart's milestone bar is a promise about money. These tests pin it to the
 * live configuration, because the mockup it came from hard-coded a ₹499 target
 * and a ₹176 gap that belong to no Tazzzo bill.
 */
class FreeDeliveryProgressTest {

    private fun bill(itemTotal: Int, deliveryFee: Int, reason: String? = null) = BillSummary(
        itemTotal = itemTotal, itemMrpTotal = itemTotal, deliveryFee = deliveryFee,
        handlingCharge = 0, coinsEarned = 0, grandTotal = itemTotal + deliveryFee,
        deliveryFeeReason = reason
    )

    @Test fun the_target_is_the_configured_threshold_never_a_literal() {
        val p = freeDeliveryProgress(bill(itemTotal = 100, deliveryFee = 25))
        assertEquals(AppConfig.charges.freeDeliveryAboveRupees, p.thresholdRupees)
    }

    @Test fun the_gap_is_what_is_actually_left_to_spend() {
        val threshold = AppConfig.charges.freeDeliveryAboveRupees
        val p = freeDeliveryProgress(bill(itemTotal = threshold - 40, deliveryFee = 25))
        assertEquals(40, p.remainingRupees)
        assertFalse(p.alreadyFree)
    }

    @Test fun an_order_that_already_ships_free_is_never_told_to_spend_more() {
        val p = freeDeliveryProgress(bill(itemTotal = 250, deliveryFee = 0, reason = "Free on orders above ₹199"))
        assertTrue(p.alreadyFree)
        assertEquals(0, p.remainingRupees)
        assertEquals("Free on orders above ₹199", p.reason)
    }

    @Test fun an_empty_basket_is_not_free_delivery() {
        // deliveryFee is 0 on an empty cart because there is nothing to deliver.
        // Reporting that as "free delivery unlocked" would celebrate nothing.
        val p = freeDeliveryProgress(bill(itemTotal = 0, deliveryFee = 0))
        assertFalse(p.alreadyFree)
    }

    @Test fun the_track_fraction_stays_inside_its_bounds() {
        assertEquals(0f, freeDeliveryProgress(bill(0, 25)).fraction)
        assertEquals(1f, freeDeliveryProgress(bill(100_000, 0)).fraction)
        val half = AppConfig.charges.freeDeliveryAboveRupees / 2
        assertTrue(freeDeliveryProgress(bill(half, 25)).fraction in 0.4f..0.6f)
    }

    @Test fun price_bands_describe_the_item_and_stop_when_they_stop_helping() {
        assertEquals("Under ₹29", priceBandLabel(18))
        assertEquals("Under ₹49", priceBandLabel(32))
        assertEquals("Under ₹99", priceBandLabel(85))
        assertNull(priceBandLabel(199), "above the top band 'under' is not a selling point")
        assertNull(priceBandLabel(0))
    }
}
