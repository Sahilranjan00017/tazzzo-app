package com.tazzzo.app

import com.tazzzo.app.config.AppConfig
import com.tazzzo.app.config.freeDeliveryProgress
import com.tazzzo.app.config.priceBandLabel
import com.tazzzo.app.data.model.BillSummary
import com.tazzzo.app.data.model.Money
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

    private fun bill(itemTotal: Money, deliveryFee: Money, reason: String? = null) = BillSummary(
        itemTotal = itemTotal, itemMrpTotal = itemTotal, deliveryFee = deliveryFee,
        handlingCharge = r(0), coinsEarned = 0, grandTotal = itemTotal + deliveryFee,
        deliveryFeeReason = reason
    )

    @Test fun the_target_is_the_configured_threshold_never_a_literal() {
        val p = freeDeliveryProgress(bill(itemTotal = r(100), deliveryFee = r(25)))
        assertEquals(AppConfig.charges.freeDeliveryAbove, p.threshold)
    }

    @Test fun the_gap_is_what_is_actually_left_to_spend() {
        val threshold = AppConfig.charges.freeDeliveryAbove
        val p = freeDeliveryProgress(bill(itemTotal = threshold - r(40), deliveryFee = r(25)))
        assertEquals(r(40), p.remaining)
        assertFalse(p.alreadyFree)
    }

    @Test fun an_order_that_already_ships_free_is_never_told_to_spend_more() {
        val p = freeDeliveryProgress(bill(itemTotal = r(250), deliveryFee = r(0), reason = "Free on orders above ₹199"))
        assertTrue(p.alreadyFree)
        assertEquals(r(0), p.remaining)
        assertEquals("Free on orders above ₹199", p.reason)
    }

    @Test fun an_empty_basket_is_not_free_delivery() {
        // deliveryFee is 0 on an empty cart because there is nothing to deliver.
        // Reporting that as "free delivery unlocked" would celebrate nothing.
        val p = freeDeliveryProgress(bill(itemTotal = r(0), deliveryFee = r(0)))
        assertFalse(p.alreadyFree)
    }

    @Test fun the_track_fraction_stays_inside_its_bounds() {
        assertEquals(0f, freeDeliveryProgress(bill(r(0), r(25))).fraction)
        assertEquals(1f, freeDeliveryProgress(bill(r(100_000), r(0))).fraction)
        val half = AppConfig.charges.freeDeliveryAbove.floorDiv(2)
        assertTrue(freeDeliveryProgress(bill(half, r(25))).fraction in 0.4f..0.6f)
    }

    @Test fun price_bands_describe_the_item_and_stop_when_they_stop_helping() {
        assertEquals("Under ₹29", priceBandLabel(r(18)))
        assertEquals("Under ₹49", priceBandLabel(r(32)))
        assertEquals("Under ₹99", priceBandLabel(r(85)))
        assertNull(priceBandLabel(r(199)), "above the top band 'under' is not a selling point")
        assertNull(priceBandLabel(r(0)))
    }
}
