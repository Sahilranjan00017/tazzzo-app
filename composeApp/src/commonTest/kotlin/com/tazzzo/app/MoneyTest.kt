package com.tazzzo.app

import com.tazzzo.app.data.model.Money
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class MoneyTest {
    @Test fun zero() {
        assertEquals(0L, Money.ZERO.paise)
        assertTrue(Money.ZERO.isZero)
        assertEquals("₹0", Money.ZERO.format())
    }

    @Test fun rupeesAreHundredPaise() {
        assertEquals(100L, Money.ofRupees(1).paise)
        assertEquals(9900L, Money.ofRupees(99).paise)
        assertEquals(Money.ofPaise(9900), Money.ofRupees(99))
    }

    @Test fun formatsWholeRupeesWithoutDecimals() {
        assertEquals("₹1", Money.ofPaise(100).format())
        assertEquals("₹99", Money.ofPaise(9900).format())
    }

    @Test fun formatsPaiseWithTwoDecimals() {
        assertEquals("₹99.50", Money.ofPaise(9950).format())
        assertEquals("₹0.05", Money.ofPaise(5).format())
        assertEquals("₹0.50", Money.ofPaise(50).format())
        assertEquals("₹12.07", Money.ofPaise(1207).format())
    }

    @Test fun usesIndianGrouping() {
        assertEquals("₹999", Money.ofRupees(999).format())
        assertEquals("₹1,000", Money.ofRupees(1_000).format())
        assertEquals("₹12,345", Money.ofRupees(12_345).format())
        assertEquals("₹1,23,456", Money.ofRupees(123_456).format())
        assertEquals("₹12,34,567.89", Money.ofPaise(123_456_789).format())
    }

    @Test fun negativeAmounts() {
        assertEquals("-₹5", Money.ofPaise(-500).format())
        assertEquals("-₹0.05", Money.ofPaise(-5).format())
        assertEquals(Money.ofPaise(-500), -Money.ofPaise(500))
    }

    @Test fun largeValuesRoundTrip() {
        val big = Money.ofPaise(Long.MAX_VALUE)
        assertEquals(Long.MAX_VALUE, big.paise)
        assertEquals("₹92,23,37,20,36,85,47,758.07", big.format())
        assertEquals("-₹92,23,37,20,36,85,47,758.08", Money.ofPaise(Long.MIN_VALUE).format())
    }

    @Test fun arithmetic() {
        assertEquals(Money.ofPaise(1250), Money.ofPaise(1000) + Money.ofPaise(250))
        assertEquals(Money.ofPaise(750), Money.ofPaise(1000) - Money.ofPaise(250))
        assertEquals(Money.ofPaise(9900), Money.ofPaise(3300) * 3)
        assertTrue(Money.ofPaise(1) < Money.ofPaise(2))
    }

    @Test fun overflowIsRejectedNotWrapped() {
        assertFailsWith<ArithmeticException> { Money.ofPaise(Long.MAX_VALUE) + Money.ofPaise(1) }
        assertFailsWith<ArithmeticException> { Money.ofPaise(Long.MIN_VALUE) - Money.ofPaise(1) }
        assertFailsWith<ArithmeticException> { Money.ofPaise(Long.MAX_VALUE) * 2 }
        assertFailsWith<ArithmeticException> { Money.ofRupees(Long.MAX_VALUE) }
        assertFailsWith<ArithmeticException> { -Money.ofPaise(Long.MIN_VALUE) }
    }
}
