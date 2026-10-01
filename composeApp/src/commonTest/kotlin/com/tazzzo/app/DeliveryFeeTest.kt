package com.tazzzo.app

import com.tazzzo.app.config.BillCalculator
import com.tazzzo.app.data.model.CartLine
import com.tazzzo.app.data.model.DeliverySlot
import com.tazzzo.app.data.model.Product
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/**
 * Delivery fee is money; it follows the same rule as every other number on
 * the bill — computed once, explained, and never a silent gift. Waived
 * delivery counts toward realised savings WITH its reason.
 */
class DeliveryFeeTest {
    private fun onion(qty: Int) = CartLine(
        Product("p1", "Fresh Onion", "Tazzzo Farm", "🧅", "1 kg", r(32), r(40), "fruits", "fresh-veg", 0.0, 0), qty
    )
    private val none = emptyList<com.tazzzo.app.data.model.Promotion>()

    @Test fun below_threshold_no_slot_charges_the_flat_fee_and_waives_nothing() {
        val b = BillCalculator.bill(listOf(onion(1)), promotions = none)          // ₹32
        assertEquals(r(25), b.deliveryFee); assertEquals(r(0), b.deliveryFeeWaived); assertNull(b.deliveryFeeReason)
    }

    @Test fun above_threshold_waives_the_flat_fee_with_the_threshold_as_reason() {
        val b = BillCalculator.bill(listOf(onion(10)), promotions = none)         // ₹320
        assertEquals(r(0), b.deliveryFee); assertEquals(r(25), b.deliveryFeeWaived)
        assertEquals("Free on orders above ₹199", b.deliveryFeeReason)
        assertEquals(r(25), b.realisedSavings, "waived delivery is realised savings")
    }

    @Test fun a_paid_slot_charges_its_own_fee_with_its_own_reason_below_threshold() {
        val late = DeliverySlot("s", "Today, 8–10 PM", true, fee = r(15), feeReason = "Late-evening slot")
        val b = BillCalculator.bill(listOf(onion(1)), promotions = none, slot = late)
        assertEquals(r(15), b.deliveryFee); assertEquals("Late-evening slot", b.deliveryFeeReason)
        assertEquals(r(0), b.deliveryFeeWaived)
    }

    @Test fun a_free_slot_below_threshold_is_free_for_the_slot_and_waives_the_flat_fee() {
        val free = DeliverySlot("s", "Tomorrow, 7–9 AM", true, fee = r(0))
        val b = BillCalculator.bill(listOf(onion(1)), promotions = none, slot = free)
        assertEquals(r(0), b.deliveryFee); assertEquals("Free for this slot", b.deliveryFeeReason)
        assertEquals(r(25), b.deliveryFeeWaived)
    }

    @Test fun threshold_beats_a_paid_slot_and_the_saving_is_the_slot_fee() {
        val late = DeliverySlot("s", "Today, 8–10 PM", true, fee = r(15), feeReason = "Late-evening slot")
        val b = BillCalculator.bill(listOf(onion(10)), promotions = none, slot = late) // ₹320
        assertEquals(r(0), b.deliveryFee); assertEquals(r(15), b.deliveryFeeWaived)
        assertEquals("Free on orders above ₹199", b.deliveryFeeReason)
    }

    @Test fun grand_total_uses_the_slot_fee() {
        val late = DeliverySlot("s", "late", true, fee = r(15))
        val b = BillCalculator.bill(listOf(onion(1)), promotions = none, slot = late)
        assertEquals(r(32 + 15 + 5), b.grandTotal)
    }
}
