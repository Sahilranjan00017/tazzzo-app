package com.tazzzo.app

import com.tazzzo.app.config.BillCalculator
import com.tazzzo.app.config.ChargeRules
import com.tazzzo.app.config.CoinRules
import com.tazzzo.app.data.model.AppliedPromotion
import com.tazzzo.app.data.model.BillSummary
import com.tazzzo.app.data.model.CartLine
import com.tazzzo.app.data.model.DeliverySlot
import com.tazzzo.app.data.model.MembershipDiscountRule
import com.tazzzo.app.data.model.Money
import com.tazzzo.app.data.model.Product
import com.tazzzo.app.data.model.sumOfMoney
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

/** PR-04B policy: integer paise, floor rounding, checked arithmetic, explicit sign rules. */
class MoneyPolicyTest {
    private fun p(price: Money, mrp: Money = price) = Product(
        id = "x", name = "X", brand = "B", unit = "1 kg", price = price, mrp = mrp,
        categoryId = "c", subcategoryId = "s", rating = 4.0, ratingCount = 1
    )

    // ---- rupees <-> paise ----------------------------------------------------------------------

    @Test fun rupeeToPaiseEquivalence() {
        assertEquals(100L, r(1).paise); assertEquals(9_900L, r(99).paise); assertEquals(0L, r(0).paise)
        assertEquals(Money.ofPaise(4_950), Money.ofRupees(49) + Money.ofPaise(50))
    }

    @Test fun paiseBearingValuesAreNeverTruncated() {
        assertEquals("₹49.50", Money.ofPaise(4_950).format())
        assertEquals("₹49.50", Money.ofPaise(4_950).toString())
        assertEquals("₹99", r(99).format())          // whole rupees keep their familiar look
        assertEquals("₹0.05", Money.ofPaise(5).format())
        assertEquals("₹1,23,456", r(123_456).format())
    }

    @Test fun quantityMultiplicationAndSubtotal() {
        val line = CartLine(p(Money.ofPaise(4_950)), 3)
        assertEquals(Money.ofPaise(14_850), line.lineTotal)
        assertEquals(Money.ofPaise(14_850 + 6_000), listOf(line, CartLine(p(r(30)), 2)).sumOfMoney { it.lineTotal })
        assertEquals(Money.ZERO, emptyList<CartLine>().sumOfMoney { it.lineTotal })
    }

    // ---- rounding: floor(amountPaise * percent / 100), integers only -----------------------------

    @Test fun percentIsFlooredInPaise() {
        assertEquals(Money.ofPaise(3_345), r(669).percentOf(5))        // 66900 * 5 / 100 = 3345 exactly
        assertEquals(Money.ofPaise(0), Money.ofPaise(19).percentOf(5)) // 0.95 paise -> 0
        assertEquals(Money.ofPaise(1), Money.ofPaise(39).percentOf(5)) // 1.95 -> 1 (floor, never 2)
        assertEquals(Money.ofPaise(1), Money.ofPaise(199).percentOf(1)) // 1.99 -> 1
        assertEquals(Money.ofPaise(499), Money.ofPaise(999).percentOf(50)) // 499.5 -> 499
        assertEquals(Money.ZERO, Money.ZERO.percentOf(100))
        assertEquals(r(7), r(7).percentOf(100))
        assertEquals(Money.ZERO, r(7).percentOf(0))
    }

    @Test fun percentMatchesPlainLongArithmeticOverTheRangeThatDoesNotOverflow() {
        for (paise in listOf(0L, 1L, 99L, 100L, 101L, 4_950L, 123_457L, 99_999_999L)) for (pct in listOf(0, 1, 2, 5, 10, 33, 50, 99, 100)) {
            assertEquals(Money.ofPaise(paise * pct / 100L), Money.ofPaise(paise).percentOf(pct), "$paise x $pct%")
        }
    }

    @Test fun percentDoesNotOverflowInTheIntermediateProduct() {
        // paise * percent would overflow Long here; the split computation is exact.
        val big = Money.ofPaise(Long.MAX_VALUE)
        assertEquals(big, big.percentOf(100))
        assertEquals(Money.ofPaise(Long.MAX_VALUE / 100L * 50L + (Long.MAX_VALUE % 100L) * 50L / 100L), big.percentOf(50))
    }

    @Test fun aResultThatCannotFitIsAnOverflowNotAWrap() {
        assertFailsWith<ArithmeticException> { Money.ofPaise(Long.MAX_VALUE).percentOf(101) }
    }

    @Test fun percentRejectsSignedInputs() {
        assertFailsWith<IllegalArgumentException> { Money.ofPaise(-100).percentOf(5) }
        assertFailsWith<IllegalArgumentException> { r(100).percentOf(-5) }
    }

    @Test fun floorDivSplitsWithoutRounding() {
        assertEquals(Money.ofPaise(33), Money.ofPaise(100).floorDiv(3))
        assertFailsWith<IllegalArgumentException> { r(1).floorDiv(0) }
        assertFailsWith<IllegalArgumentException> { Money.ofPaise(-1).floorDiv(2) }
    }

    // ---- overflow ---------------------------------------------------------------------------------

    @Test fun additionMultiplicationAndSumsAreChecked() {
        assertFailsWith<ArithmeticException> { Money.ofPaise(Long.MAX_VALUE) + Money.ofPaise(1) }
        assertFailsWith<ArithmeticException> { Money.ofPaise(Long.MAX_VALUE) * 2 }
        assertFailsWith<ArithmeticException> { listOf(Money.ofPaise(Long.MAX_VALUE), Money.ofPaise(1)).sumOfMoney { it } }
        assertFailsWith<ArithmeticException> { Money.ofRupees(Long.MAX_VALUE / 10) }
    }

    @Test fun largeValuesAreExact() {
        val big = Money.ofRupees(1_000_000_000L)
        assertEquals(100_000_000_000L, big.paise)
        assertEquals("₹1,00,00,00,000", big.format())
    }

    // ---- sign policy -------------------------------------------------------------------------------

    @Test fun moneyItselfIsSigned() {
        assertEquals(Money.ofPaise(-500), r(5) - r(10))
        assertEquals(Money.ofPaise(-500), -r(5))
        assertTrue((r(5) - r(10)).isNegative)
        assertEquals("-₹5", (r(5) - r(10)).format())
    }

    @Test fun productPricesAreNeverNegative() {
        assertFailsWith<IllegalArgumentException> { p(Money.ofPaise(-1)) }
        assertFailsWith<IllegalArgumentException> { p(r(10), Money.ofPaise(-1)) }
        assertEquals(Money.ZERO, p(Money.ZERO).price) // a free item is legal
    }

    @Test fun customerFacingChargesAndDiscountMagnitudesAreNeverNegative() {
        fun bill(tip: Money = Money.ZERO, club: Money = Money.ZERO, delivery: Money = Money.ZERO) =
            BillSummary(r(10), r(10), delivery, r(0), 0, r(10), clubDiscount = club, tip = tip)
        assertFailsWith<IllegalArgumentException> { bill(tip = Money.ofPaise(-1)) }
        assertFailsWith<IllegalArgumentException> { bill(club = Money.ofPaise(-1)) }
        assertFailsWith<IllegalArgumentException> { bill(delivery = Money.ofPaise(-1)) }
        assertFailsWith<IllegalArgumentException> { DeliverySlot("s", "l", true, fee = Money.ofPaise(-1)) }
        assertFailsWith<IllegalArgumentException> { AppliedPromotion("p", "t", Money.ofPaise(-1), "e") }
        assertFailsWith<IllegalArgumentException> { MembershipDiscountRule(5, Money.ofPaise(-1)) }
    }

    @Test fun aDiscountIsASeparateNonNegativeFieldNotANegativePrice() {
        val bill = BillCalculator.bill(listOf(CartLine(p(r(1_000)), 1)), isClubMember = true, promotions = emptyList())
        assertTrue(bill.clubDiscount.isPositive && !bill.clubDiscount.isNegative)
        assertEquals(bill.itemTotal - bill.clubDiscount + bill.deliveryFee + bill.handlingCharge, bill.grandTotal)
    }

    // ---- rule-level relation to the old rupee-floor behaviour ----------------------------------------------

    @Test fun clubDiscountFlooredToARupeeEqualsTheOldRupeeFloorForWholeRupeeBaskets() {
        val rule = MembershipDiscountRule(percent = 5, minOrderValue = r(500))
        for (rupees in 500..3_000 step 7) {
            val old = rupees * 5 / 100                        // the pre-paise formula
            val now = rule.discountFor(r(rupees))
            assertEquals(old, (now.paise / 100L).toInt(), "₹$rupees")
            assertTrue(now.paise >= old * 100L && now.paise < (old + 1) * 100L, "never rounds up past the next rupee: ₹$rupees")
        }
    }

    @Test fun clubDiscountHonoursMinimumAndCap() {
        val capped = MembershipDiscountRule(percent = 10, minOrderValue = r(500), maxDiscount = r(40))
        assertEquals(Money.ZERO, capped.discountFor(r(499)))
        assertEquals(r(40), capped.discountFor(r(5_000)))
        assertEquals(Money.ofPaise(5_000), MembershipDiscountRule(10, r(500)).discountFor(r(500))) // uncapped 10% of ₹500
    }

    // ---- coins ----------------------------------------------------------------------------------------------

    @Test fun coinsAreFlooredWholeCoins() {
        val rules = CoinRules(earnPercent = 2)
        assertEquals(0, rules.coinsFor(r(49)))                      // 0.98 coins
        assertEquals(1, rules.coinsFor(r(50)))
        assertEquals(2, rules.coinsFor(Money.ofPaise(14_950)))      // 2.99 -> 2
        assertEquals(24, rules.coinsFor(r(1_234)))                  // 24.68 -> 24
        assertEquals(0, rules.coinsFor(Money.ZERO))
        assertEquals(0, CoinRules(earnPercent = 0).coinsFor(r(1_000)))
    }

    @Test fun coinRedemptionIsBoundedByRequestCapAndItemTotal() {
        val rules = CoinRules(valuePerCoin = Money.ofPaise(50), maxRedeemPerOrder = 100)
        assertEquals(Money.ofPaise(1_500), BillCalculator.redeemableValue(30, r(1_000), rules))
        assertEquals(r(50), BillCalculator.redeemableValue(500, r(1_000), rules))       // capped at 100 coins
        assertEquals(r(10), BillCalculator.redeemableValue(100, r(10), rules))          // never above the items
        assertEquals(Money.ZERO, BillCalculator.redeemableValue(0, r(10), rules))
    }

    // ---- configuration uses explicit Money -----------------------------------------------------------------------

    @Test fun chargeDefaultsAreExplicitMoney() {
        val c = ChargeRules()
        assertEquals(r(199), c.freeDeliveryAbove); assertEquals(r(25), c.deliveryFee); assertEquals(r(5), c.handlingFee)
    }

    // ---- serialization: bare integer paise on the wire ---------------------------------------------------------------

    @Test fun moneySerializesAsBarePaise() {
        assertEquals("4950", Json.encodeToString(Money.ofPaise(4_950)))
        assertEquals(Money.ofPaise(4_950), Json.decodeFromString<Money>("4950"))
        assertEquals(Money.ofPaise(Long.MAX_VALUE), Json.decodeFromString<Money>(Long.MAX_VALUE.toString()))
    }

    @Test fun productJsonCarriesPaiseNotRupees() {
        val json = Json { encodeDefaults = true }
        val text = json.encodeToString(p(Money.ofPaise(2_950), Money.ofPaise(3_000)))
        assertTrue(text.contains("\"price\":2950") && text.contains("\"mrp\":3000"), text)
        assertEquals(Money.ofPaise(2_950), json.decodeFromString<Product>(text).price)
    }

    @Test fun aNegativePriceInAPayloadIsRejectedOnDecode() {
        val json = Json { encodeDefaults = true }
        val ok = json.encodeToString(p(r(10)))
        assertFailsWith<Exception> { json.decodeFromString<Product>(ok.replace("\"price\":1000", "\"price\":-1000")) }
    }

    @Test fun discountPercentIsFlooredInPaise() {
        assertEquals(33, p(Money.ofPaise(6_700), Money.ofPaise(10_000)).discountPercent)   // 33.0
        assertEquals(0, p(r(10), r(10)).discountPercent)
        assertEquals(0, p(r(12), r(10)).discountPercent)        // price above mrp: no discount shown
        assertEquals(9, p(Money.ofPaise(9_100), Money.ofPaise(10_000)).discountPercent)
    }
}
