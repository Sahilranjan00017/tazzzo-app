package com.tazzzo.app

import com.tazzzo.app.config.BillCalculator
import com.tazzzo.app.config.CoinRules
import com.tazzzo.app.config.MembershipCalculator
import com.tazzzo.app.config.MembershipConfig
import com.tazzzo.app.config.freeDeliveryProgress
import com.tazzzo.app.config.priceBandLabel
import com.tazzzo.app.data.model.BillSummary
import com.tazzzo.app.data.model.CartLine
import com.tazzzo.app.data.model.DeliverySlot
import com.tazzzo.app.data.model.MembershipState
import com.tazzzo.app.data.model.Money
import com.tazzzo.app.data.model.Product
import com.tazzzo.app.data.model.unitPriceLabel
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * PR-04B parity: the migrated (integer-paise) calculators must reproduce the pre-migration
 * rupee-`Int` behaviour.
 *
 * The three golden hashes below were captured by running THE ORIGINAL rupee-Int code
 * (`main` @ be42f98) over exactly these scenarios — every field of every bill, every
 * customer-facing sentence (promotion explanations, delivery reasons, best-offer notes,
 * declined reasons), membership results, coin maths, price bands, per-unit labels and
 * discount percentages. Any difference in any value or any word changes the hash.
 *
 * Tiers:
 *  1. Scenarios where NO percent rule can apply: every number and sentence must match exactly.
 *  2. Percent rules active, but every base is a multiple of ₹20 so each percent result is a whole
 *     rupee: the paise-floor and the old rupee-floor coincide, so this must match exactly too.
 *  3. Labels and price helpers.
 *
 * Where a percent result is NOT a whole rupee the two policies legitimately differ (the old code
 * floored to a rupee; the approved policy floors in paise — see [MoneyPolicyTest]).
 */
class MoneyParityTest {
    private var h = 1469598103934665603L
    private fun fresh() = 1469598103934665603L
    private fun mix(v: Long) { h = (h xor v) * 1099511628211L }
    private fun mix(s: String?) { if (s == null) mix(-7L) else { mix(s.length.toLong()); s.forEach { mix(it.code.toLong()) } } }
    private fun mix(i: Int) = mix(i.toLong())
    private fun mix(b: Boolean) = mix(if (b) 1L else 0L)

    /** Whole rupees, asserting the value really is a whole rupee (so no precision is hidden by the hash). */
    private fun Money.rupees(): Int { check(paise % 100L == 0L) { "not a whole rupee: $paise paise" }; return (paise / 100L).toInt() }
    private fun mix(m: Money) = mix(m.rupees())

    private fun p(id: String, price: Int, mrp: Int, cat: String = "c", unit: String = "1 kg") = Product(
        id = id, name = "N$id", brand = "B", unit = unit, price = r(price), mrp = r(mrp), categoryId = cat, subcategoryId = "s",
        rating = 4.0, ratingCount = 1
    )

    private fun mixBill(b: BillSummary) {
        mix(b.itemTotal); mix(b.itemMrpTotal); mix(b.deliveryFee); mix(b.handlingCharge); mix(b.coinsEarned); mix(b.grandTotal)
        mix(b.clubDiscount); mix(b.promotionDiscount); mix(b.deliveryFeeWaived); mix(b.tip); mix(b.saved); mix(b.realisedSavings)
        mix(b.freeDeliveryByPromotion); mix(b.deliveryFeeReason); mix(b.bestOfferNote)
        b.appliedPromotions.forEach { mix(it.promotionId); mix(it.title); mix(it.discount); mix(it.explanation); mix(it.freeDelivery) }
        b.declinedPromotions.forEach { mix(it.promotionId); mix(it.title); mix(it.reason); mix(it.wouldHaveSaved?.rupees() ?: -1) }
        val f = freeDeliveryProgress(b)
        mix(f.alreadyFree); mix(f.reason); mix(f.threshold); mix(f.remaining); mix((f.fraction * 1000).toInt())
    }

    private val slots = listOf(null, DeliverySlot("f", "free", true, fee = r(0)), DeliverySlot("p", "paid", true, fee = r(15), feeReason = "Peak"))

    @Test fun tier1_no_percent_rule_applies_everything_matches_the_original_exactly() {
        val prices = listOf(1 to 2, 7 to 9, 29 to 35, 49 to 55, 99 to 120, 120 to 120, 199 to 250, 250 to 300, 499 to 520, 33 to 40, 198 to 198)
        val broad = ArrayList<List<CartLine>>()
        for ((a, am) in prices) for (q in listOf(1, 2, 3, 5)) {
            broad.add(listOf(CartLine(p("x", a, am, "other"), q)))
            for ((b, bm) in prices.take(5)) broad.add(listOf(CartLine(p("x", a, am, "other"), q), CartLine(p("y", b, bm, "other2"), 2)))
        }
        broad.add(emptyList())
        var n = 0
        for (cart in broad) for (s in slots) for (tip in listOf(0, 10)) for (redeem in listOf(0, 7, 1000)) {
            mixBill(BillCalculator.bill(cart, redeemCoins = redeem, slot = s, tip = r(tip), promotions = emptyList())); n++
        }
        assertEquals(4770, n)
        assertEquals(3176537541242368791L, h)
    }

    @Test fun tier2_percent_rules_on_whole_rupee_results_match_the_original_exactly() {
        val exactPrices = listOf(20 to 25, 40 to 40, 60 to 80, 100 to 120, 120 to 150, 200 to 220, 500 to 520, 1000 to 1100)
        val carts = ArrayList<List<CartLine>>()
        for ((a, am) in exactPrices) for (q in listOf(1, 2, 3, 5)) {
            carts.add(listOf(CartLine(p("x", a, am, "dairy"), q)))
            for ((b, bm) in exactPrices.take(4)) carts.add(listOf(CartLine(p("x", a, am, "dairy"), q), CartLine(p("y", b, bm, "fruits"), 2)))
        }
        var n = 0
        for (cart in carts) for (member in listOf(false, true)) for (spend in listOf(0, 5000, 20000)) for (c in listOf(null, "TAZZZO50", "BOGUS"))
            for (s in slots) for (tip in listOf(0, 10)) for (redeem in listOf(0, 7, 1000)) {
                mixBill(BillCalculator.bill(cart, redeemCoins = redeem, isClubMember = member, clubCumulativeSpend = r(spend),
                    couponCode = c, slot = s, tip = r(tip))); n++
            }
        assertEquals(51840, n)
        val plan = MembershipConfig.plan
        for (t in listOf(0, 100, 500, 520, 1000, 1240, 5000, 20000)) for (m in listOf(false, true)) for (sp in listOf(0, 4999, 5000, 9000)) {
            val e = MembershipCalculator.evaluate(r(t), m, plan, r(sp))
            mix(e.isMember); mix(e.isEligible); mix(e.discount); mix(e.amountToUnlock); mix(e.appliedRule?.percent ?: -1)
        }
        for (sp in listOf(0, 4999, 5000, 9999)) {
            mix(MembershipCalculator.activeDiscountRule(plan, r(sp)).percent)
            val nx = MembershipCalculator.nextSpendMilestone(plan, r(sp)); mix(nx?.second?.rupees() ?: -1); mix(nx?.first?.id)
        }
        for (ex in listOf(0, 100, 500, 1000, 2500)) mix(MembershipCalculator.exampleSavings(plan, r(ex)))
        var st = MembershipState()
        for (t in listOf(300, 600, 700, 4000, 900, 100)) {
            st = MembershipCalculator.applyEligibleOrder(plan, st, r(t), r(t / 20 * 1), "lbl")
            mix(st.cumulativeSpend); mix(st.cumulativeSavings); mix(st.eligibleOrderCount); mix(st.unlockedSpendMilestoneIds.size); mix(st.rewards.size)
        }
        for (rules in listOf(CoinRules(), CoinRules(earnPercent = 5, valuePerCoin = r(2), maxRedeemPerOrder = 50), CoinRules(enabled = false)))
            for (t in listOf(0, 1, 49, 50, 99, 100, 149, 150, 999, 1234)) for (rc in listOf(0, 3, 60, 1000)) {
                mix(rules.coinsFor(r(t))); mix(BillCalculator.redeemableValue(rc, r(t), rules))
            }
        assertEquals(7562318821311254228L, h)
    }

    @Test fun tier3_labels_price_bands_and_discount_percent_match_the_original_exactly() {
        for (pr in listOf(0, 1, 28, 29, 30, 48, 49, 50, 98, 99, 100, 5000)) mix(priceBandLabel(r(pr)))
        val units = listOf("1 kg", "500 g", "250 g", "100 g", "19 g", "1 L", "500 ml", "250 ml", "20 ml", "6 pcs", "1 pc", "12 pieces", "4 x 100 g", "2 x 1 L", "0.5 kg", "1.5 L", "180 pages", "1 unit", "2 x 0.5 kg", "750 g")
        for (u in units) for (pr in listOf(1, 7, 29, 33, 49, 55, 99, 100, 149, 250, 333, 999)) {
            mix(unitPriceLabel(r(pr), u)); mix(p("u", pr, pr + 17, unit = u).discountPercent)
        }
        assertEquals(1268544545966640018L, h)
    }
}
