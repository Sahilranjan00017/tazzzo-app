package com.tazzzo.app.payable

import com.tazzzo.app.data.model.Money
import com.tazzzo.app.data.model.PayableMoney
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** The V1 money: `payable = merchandiseSubtotal - benefitDiscount`, consistent or not at all. */
class PayableMoneyTest {
    @Test fun aConsistentBlockMapsExactlyInPaise() {
        val m = assertNotNull(PayableMoney.fromPaise(10_000, 1_000, 9_000))
        assertEquals(10_000L, m.merchandiseSubtotal.paise); assertEquals(1_000L, m.benefitDiscount.paise); assertEquals(9_000L, m.payable.paise)
        assertTrue(m.hasDiscount); assertFalse(m.isNothingDue)
    }

    @Test fun fortyNineFiftyIsExact() {
        val m = assertNotNull(PayableMoney.fromPaise(4_950, 0, 4_950))
        assertEquals(4_950L, m.payable.paise); assertEquals("₹49.50", m.payable.format()); assertFalse(m.hasDiscount)
    }

    @Test fun aFullDiscountIsAValidZeroAmountDue() {
        val m = assertNotNull(PayableMoney.fromPaise(4_950, 4_950, 0))
        assertTrue(m.isNothingDue); assertEquals("₹0", m.payable.format())
        assertNotNull(PayableMoney.fromPaise(0, 0, 0))
    }

    @Test fun anyNegativeValueIsAContractViolation() {
        assertNull(PayableMoney.fromPaise(-1, 0, -1))
        assertNull(PayableMoney.fromPaise(100, -1, 101))
        assertNull(PayableMoney.fromPaise(100, 101, -1))
    }

    @Test fun aDiscountLargerThanTheSubtotalIsAContractViolation() {
        assertNull(PayableMoney.fromPaise(100, 101, 0))
    }

    @Test fun aPayableThatIsNotSubtotalMinusDiscountIsNeverRepaired() {
        assertNull(PayableMoney.fromPaise(10_000, 1_000, 9_001))
        assertNull(PayableMoney.fromPaise(10_000, 1_000, 8_999))
        assertNull(PayableMoney.fromPaise(10_000, 0, 9_000))
        assertNull(PayableMoney.fromPaise(10_000, 1_000, 10_000))       // the discount silently ignored
    }

    @Test fun itNeverPrintsItsAmounts() {
        val m = PayableMoney.fromPaise(12_345, 2_345, 10_000)!!
        assertEquals("PayableMoney(***)", m.toString())
        for (d in listOf("12345", "2345", "10000", "123", "₹")) assertFalse(d in m.toString(), d)
    }

    @Test fun theCanonicalCompactInrFormatIsTheOnlyFormat() {
        assertEquals("₹100", Money.ofPaise(10_000).format())
        assertEquals("₹0", Money.ofPaise(0).format())
        assertEquals("₹49.50", Money.ofPaise(4_950).format())
        assertEquals("₹99.25", Money.ofPaise(9_925).format())
        assertEquals("-₹10", (-Money.ofPaise(1_000)).format())
    }
}
