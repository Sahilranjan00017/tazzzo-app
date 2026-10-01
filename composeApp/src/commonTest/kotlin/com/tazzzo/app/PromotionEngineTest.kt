package com.tazzzo.app

import com.tazzzo.app.config.BillCalculator
import com.tazzzo.app.config.PromotionConfig
import com.tazzzo.app.config.PromotionEngine
import com.tazzzo.app.config.PromotionPolicy
import com.tazzzo.app.data.model.CartLine
import com.tazzzo.app.data.model.Money
import com.tazzzo.app.data.model.Product
import com.tazzzo.app.data.model.Promotion
import com.tazzzo.app.data.model.PromotionAudience
import com.tazzzo.app.data.model.PromotionScope
import com.tazzzo.app.data.model.PromotionType
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Financial correctness for the promotion engine and its integration into the
 * bill. Every number here is exact and deterministic. If one of these breaks,
 * a customer would have been charged the wrong amount or told the wrong reason.
 *
 * Money rules pinned:
 *  - a promotion never discounts more than its own eligible base;
 *  - all discounts together never exceed the item total;
 *  - the customer is told WHY an offer did not apply, in words;
 *  - mutually exclusive offers resolve to the best, and the loser is named;
 *  - the total never changes without an explanation attached to the bill;
 *  - coins redeem against the post-discount amount, never against rupees
 *    already taken off.
 */
class PromotionEngineTest {

    // ---- fixtures: real catalogue ids, so every offer is exercisable in-app ----
    private fun milk(qty: Int = 1) = CartLine(
        Product("p8", "Toned Milk Pouch", "Amul", "🥛", "500 ml", r(29), r(30), "dairy", "milk", 0.0, 0), qty
    )
    private fun paneer(qty: Int = 1) = CartLine(
        Product("p12", "Malai Paneer", "Amul", "🧀", "200 g", r(95), r(105), "dairy", "paneer", 0.0, 0), qty
    )
    private fun banana(qty: Int = 1) = CartLine(
        Product("p4", "Banana Robusta", "Tazzzo Farm", "🍌", "6 pcs", r(42), r(55), "fruits", "fresh-fruit", 0.0, 0), qty
    )
    private fun onion(qty: Int = 1) = CartLine(
        Product("p1", "Fresh Onion", "Tazzzo Farm", "🧅", "1 kg", r(32), r(40), "fruits", "fresh-veg", 0.0, 0), qty
    )

    private val noClub = PromotionPolicy(clubStacksWithPromotions = false)
    private val stackClub = PromotionPolicy(clubStacksWithPromotions = true)

    private fun flat(id: String, rupees: Int, min: Int = 0, stackable: Boolean = false, priority: Int = 0,
                     scope: PromotionScope = PromotionScope.CART, ids: List<String> = emptyList(),
                     coupon: String? = null, cap: Int? = null) = Promotion(
        id = id, title = id, description = "", type = PromotionType.FLAT_OFF, scope = scope,
        scopeIds = ids, flat = r(rupees), minOrder = r(min), stackable = stackable,
        priority = priority, couponCode = coupon, maxDiscount = cap?.let { r(it) }
    )

    // ------------------------------------------------------------ eligibility

    @Test fun empty_cart_yields_no_promotions_and_no_notes() {
        val r = PromotionEngine.evaluate(emptyList(), PromotionConfig.active, false, null, Money.ZERO)
        assertEquals(r(0), r.promotionDiscount)
        assertTrue(r.applied.isEmpty()); assertNull(r.bestOfferNote)
    }

    @Test fun product_scope_only_touches_its_own_lines() {
        // ₹5 off milk must not touch the onion.
        val r = PromotionEngine.evaluate(listOf(milk(), onion()), PromotionConfig.active, false, null, Money.ZERO)
        val milkOffer = r.applied.single { it.promotionId == "milk-5" }
        assertEquals(r(5), milkOffer.discount)
    }

    @Test fun a_promotion_never_discounts_more_than_its_own_base() {
        // ₹50 flat on a ₹29 milk line, product-scoped: capped to ₹29, not ₹50.
        val p = flat("big", 50, scope = PromotionScope.PRODUCT, ids = listOf("p8"), stackable = true)
        val r = PromotionEngine.evaluate(listOf(milk(), onion()), listOf(p), false, null, Money.ZERO)
        assertEquals(r(29), r.promotionDiscount)
    }

    @Test fun minimum_is_measured_on_the_promotions_own_scope_not_the_cart() {
        // dairy-10 needs ₹150 of DAIRY. Milk ₹29 + onion ₹500 worth = cart is big, dairy is not.
        val lines = listOf(milk(), onion(qty = 16))   // 16 × 32 = ₹512 of onion
        val r = PromotionEngine.evaluate(lines, PromotionConfig.active, false, null, Money.ZERO)
        assertTrue(r.applied.none { it.promotionId == "dairy-10" })
        val declined = r.declined.single { it.promotionId == "dairy-10" }
        assertEquals("Add ₹121 more of eligible items to unlock this offer.", declined.reason)
    }

    @Test fun the_customer_is_told_exactly_how_much_more_unlocks_a_cart_offer() {
        val p = flat("c", 50, min = 400)
        val r = PromotionEngine.evaluate(listOf(onion(qty = 10)), listOf(p), false, null, Money.ZERO)   // ₹320
        assertEquals("Add ₹80 more to unlock this offer.", r.declined.single().reason)
    }

    @Test fun percent_off_is_capped_and_the_cap_is_explained() {
        // 10% of (₹29 + ₹95×5 = ₹504) = ₹50 → capped at ₹40.
        val lines = listOf(milk(), paneer(qty = 5))
        val r = PromotionEngine.evaluate(lines, PromotionConfig.active, false, null, Money.ZERO)
        val dairy = r.applied.single { it.promotionId == "dairy-10" }
        assertEquals(r(40), dairy.discount)
        assertTrue(dairy.explanation.contains("capped at ₹40"), dairy.explanation)
    }

    // ---------------------------------------------------------------- coupons

    @Test fun a_coupon_applies_only_when_entered_and_is_case_insensitive() {
        val lines = listOf(onion(qty = 15))   // ₹480 ≥ ₹400
        val without = PromotionEngine.evaluate(lines, PromotionConfig.active, false, null, Money.ZERO)
        assertTrue(without.applied.none { it.promotionId == "tazzzo50" })
        // Not even a "declined" nag: an un-entered coupon is not a missed offer.
        assertTrue(without.declined.none { it.promotionId == "tazzzo50" })

        val with = PromotionEngine.evaluate(lines, PromotionConfig.active, false, " tazzzo50 ", Money.ZERO)
        assertEquals(r(50), with.applied.single { it.promotionId == "tazzzo50" }.discount)
    }

    @Test fun an_entered_coupon_below_minimum_gets_a_visible_reason() {
        val r = PromotionEngine.evaluate(listOf(onion(qty = 5)), PromotionConfig.active, false, "TAZZZO50", Money.ZERO) // ₹160
        assertEquals("Add ₹240 more to unlock this offer.", r.declined.single { it.promotionId == "tazzzo50" }.reason)
    }

    // ---------------------------------------------------------------- stacking

    @Test fun stackables_all_apply_and_exclusives_resolve_to_the_single_best() {
        val a = flat("a", 10, stackable = true)
        val b = flat("b", 20, stackable = false)
        val c = flat("c", 30, stackable = false)
        val r = PromotionEngine.evaluate(listOf(onion(qty = 10)), listOf(a, b, c), false, null, Money.ZERO)
        assertEquals(setOf("a", "c"), r.applied.map { it.promotionId }.toSet())
        assertEquals(r(40), r.promotionDiscount)
        val lost = r.declined.single { it.promotionId == "b" }
        assertEquals("A better offer was applied to this order.", lost.reason)
        assertEquals(r(20), lost.wouldHaveSaved)
    }

    @Test fun equal_exclusives_tie_break_by_priority_then_id_deterministically() {
        val low = flat("zeta", 20, priority = 1)
        val high = flat("alpha", 20, priority = 5)
        val r1 = PromotionEngine.evaluate(listOf(onion(qty = 10)), listOf(low, high), false, null, Money.ZERO)
        val r2 = PromotionEngine.evaluate(listOf(onion(qty = 10)), listOf(high, low), false, null, Money.ZERO)
        assertEquals("alpha", r1.applied.single().promotionId)
        assertEquals(r1, r2, "input order must not change the verdict")
    }

    // ------------------------------------------------------------ club vs offer

    @Test fun club_wins_when_it_saves_more_and_the_loser_is_named() {
        // Club ₹31 vs a ₹24 exclusive offer.
        val offer = flat("promo24", 24)
        val r = PromotionEngine.evaluate(listOf(onion(qty = 20)), listOf(offer), true, null, r(31), noClub)
        assertTrue(r.clubApplied); assertEquals(r(31), r.clubDiscount)
        assertTrue(r.applied.isEmpty())
        assertEquals(
            "Best offer applied — Club saves you ₹31; promo24 would have saved ₹24.",
            r.bestOfferNote
        )
        assertEquals(r(24), r.declined.single().wouldHaveSaved)
    }

    @Test fun offer_wins_when_it_saves_more_and_club_is_set_aside_with_explanation() {
        val offer = flat("promo60", 60)
        val r = PromotionEngine.evaluate(listOf(onion(qty = 20)), listOf(offer), true, null, r(31), noClub)
        assertEquals(false, r.clubApplied); assertEquals(r(0), r.clubDiscount)
        assertEquals(r(60), r.promotionDiscount)
        assertEquals(
            "Best offer applied — promo60 saves you ₹60; your Club discount would have saved ₹31.",
            r.bestOfferNote
        )
    }

    @Test fun stackable_offers_never_compete_with_club() {
        val r = PromotionEngine.evaluate(listOf(milk(), onion(qty = 20)), PromotionConfig.active, true, null, r(33), noClub)
        // milk-5 is stackable: applies alongside Club, no competition note.
        assertTrue(r.clubApplied)
        assertTrue(r.applied.any { it.promotionId == "milk-5" })
        assertNull(r.bestOfferNote)
    }

    @Test fun policy_can_let_club_stack_with_everything() {
        val offer = flat("promo60", 60)
        val r = PromotionEngine.evaluate(listOf(onion(qty = 20)), listOf(offer), true, null, r(31), stackClub)
        assertTrue(r.clubApplied); assertEquals(r(60), r.promotionDiscount); assertNull(r.bestOfferNote)
    }

    @Test fun members_only_offers_are_invisible_to_non_members_not_declined() {
        val r = PromotionEngine.evaluate(listOf(banana(qty = 3)), PromotionConfig.active, false, null, Money.ZERO)
        assertTrue(r.applied.none { it.promotionId == "club-banana-b2g1" })
        assertTrue(r.declined.none { it.promotionId == "club-banana-b2g1" })
    }

    @Test fun buy_two_get_one_frees_the_cheapest_unit_per_full_group() {
        // 3 bananas → 1 free (₹42). 5 bananas → still 1 free. 6 → 2 free.
        assertEquals(r(42), PromotionEngine.evaluate(listOf(banana(3)), PromotionConfig.active, true, null, Money.ZERO).promotionDiscount)
        assertEquals(r(42), PromotionEngine.evaluate(listOf(banana(5)), PromotionConfig.active, true, null, Money.ZERO).promotionDiscount)
        assertEquals(r(84), PromotionEngine.evaluate(listOf(banana(6)), PromotionConfig.active, true, null, Money.ZERO).promotionDiscount)
    }

    // ------------------------------------------------------------ into the bill

    @Test fun bill_lines_add_up_exactly_with_promotion_and_club() {
        // Member, cart = onion ×20 (₹640) + milk (₹29) = ₹669.
        // Club 5% of ₹669 = ₹33.45 (floor in integer PAISE; the pre-paise engine floored to ₹33). milk-5 stackable = ₹5. dairy-10 on ₹29 < ₹150 min → declined.
        val lines = listOf(onion(20), milk())
        val b = BillCalculator.bill(lines, isClubMember = true, promotionPolicy = noClub)
        assertEquals(r(669), b.itemTotal)
        assertEquals(Money.ofPaise(3_345), b.clubDiscount)
        assertEquals(r(5), b.promotionDiscount)
        assertEquals(r(0), b.deliveryFee)          // ≥ ₹199
        assertEquals(r(5), b.handlingCharge)
        assertEquals(Money.ofPaise(66_900 - 3_345 - 500 + 0 + 500), b.grandTotal)
        // Realised = Club ₹33.45 + milk-5 ₹5 + WAIVED delivery ₹25 (₹669 ≥ ₹199).
        // Delivery the customer did not pay is money kept, so it counts — and
        // the bill carries the reason ("Free on orders above ₹199").
        assertEquals(Money.ofPaise(6_345), b.realisedSavings) // 33.45 + 5 + 25
        assertEquals(r(25), b.deliveryFeeWaived)
        // MRP savings stay a separate figure: (40−32)×20 + (30−29) = 161.
        assertEquals(r(161), b.saved)
    }

    @Test fun bill_never_goes_negative_on_items_and_fees_are_never_discounted() {
        val huge = flat("huge", 10_000, stackable = true)
        val b = BillCalculator.bill(listOf(onion(1)), promotions = listOf(huge))   // ₹32 item
        assertEquals(r(32), b.promotionDiscount)                  // clamped to item total
        assertEquals(r(0 + 25 + 5), b.grandTotal)                 // delivery + handling survive
    }

    @Test fun coupon_reaches_the_bill_and_the_total_matches_the_note() {
        // ₹640 — ABOVE the ₹500 Club minimum, so Club (₹32) actually competes.
        // (First draft used ₹480, below the minimum: no contest, no note.)
        val lines = listOf(onion(20))
        val b = BillCalculator.bill(lines, couponCode = "TAZZZO50", isClubMember = true, promotionPolicy = noClub)
        // Club 5% of 640 = ₹32 vs coupon ₹50 → coupon wins, Club set aside, customer told.
        assertEquals(r(50), b.promotionDiscount); assertEquals(r(0), b.clubDiscount)
        assertEquals(
            "Best offer applied — TAZZZO50 saves you ₹50; your Club discount would have saved ₹32.",
            b.bestOfferNote
        )
        assertEquals(r(640 - 50 + 0 + 5), b.grandTotal)
    }

    @Test fun free_delivery_promotion_zeroes_the_fee_below_threshold() {
        val fd = Promotion("fd", null, "Free delivery", "", PromotionType.FREE_DELIVERY, PromotionScope.CART, stackable = true)
        val b = BillCalculator.bill(listOf(onion(1)), promotions = listOf(fd))    // ₹32 < ₹199
        assertEquals(r(0), b.deliveryFee); assertTrue(b.freeDeliveryByPromotion)
        assertEquals(r(0), b.promotionDiscount)                  // it is not a rupee discount
    }

    @Test fun coins_redeem_against_the_discounted_amount_not_the_original() {
        // Member: ₹669 items, Club ₹33, milk-5 ₹5 → ₹631 discounted. Ask to redeem 700 coins.
        val lines = listOf(onion(20), milk())
        val b = BillCalculator.bill(lines, redeemCoins = 700, isClubMember = true, promotionPolicy = noClub)
        // Redemption cannot exceed the post-discount items (₹631), so total = fees only.
        assertEquals(r(0 + 5), b.grandTotal)
    }

    @Test fun the_fixture_promotion_set_is_internally_consistent() {
        val ids = PromotionConfig.active.map { it.id }
        assertEquals(ids.size, ids.toSet().size, "duplicate promotion ids")
        PromotionConfig.active.forEach { p ->
            when (p.type) {
                PromotionType.PERCENT_OFF -> assertNotNull(p.percent, p.id)
                PromotionType.FLAT_OFF -> assertNotNull(p.flat, p.id)
                PromotionType.BUY_X_GET_Y -> { assertNotNull(p.buyQuantity, p.id); assertNotNull(p.getQuantity, p.id) }
                PromotionType.FREE_DELIVERY -> Unit
            }
            if (p.scope != PromotionScope.CART) assertTrue(p.scopeIds.isNotEmpty(), "${p.id} has no scope ids")
            if (p.audience == PromotionAudience.MEMBERS_ONLY) assertTrue(p.stackable, "${p.id}: a Club benefit must not compete with Club")
        }
    }
}
