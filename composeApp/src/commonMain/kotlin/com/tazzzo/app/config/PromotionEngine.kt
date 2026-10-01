package com.tazzzo.app.config

import com.tazzzo.app.data.model.AppliedPromotion
import com.tazzzo.app.data.model.CartLine
import com.tazzzo.app.data.model.Money
import com.tazzzo.app.data.model.sumOfMoney
import com.tazzzo.app.data.model.DeclinedPromotion
import com.tazzzo.app.data.model.Promotion
import com.tazzzo.app.data.model.PromotionAudience
import com.tazzzo.app.data.model.PromotionResolution
import com.tazzzo.app.data.model.PromotionScope
import com.tazzzo.app.data.model.PromotionType

/**
 * Which promotions apply to this cart, how much each saves, and — for every
 * one that does NOT apply — why, in words a customer can read.
 *
 * Pure, dependency-free, deterministic. Same inputs → same verdict, always;
 * ties resolve by `priority` then by id, so two runs can never disagree.
 * `BillCalculator` is the only caller. No screen computes a discount.
 *
 * Stacking is data, not code:
 *  - every `stackable = true` promotion that is eligible applies;
 *  - among `stackable = false` promotions, the single largest saving applies
 *    and the rest are declined with "a better offer was applied";
 *  - the Club discount joins that competition when
 *    [PromotionPolicy.clubStacksWithPromotions] is false. Whichever is worth
 *    more wins, and the loser is named in [PromotionResolution.bestOfferNote]
 *    — "Best offer applied — Club saves you ₹31; TAZZZO50 would have saved
 *    ₹24." The total never changes without that sentence.
 *
 * Money guards: a promotion can never discount more than its own eligible
 * base, and the sum of all discounts can never exceed the item total. A cart
 * cannot go negative, and a coupon on one aisle cannot pay for another.
 */
object PromotionEngine {

    fun evaluate(
        lines: List<CartLine>,
        promotions: List<Promotion>,
        isMember: Boolean,
        couponCode: String?,
        clubDiscount: Money,
        policy: PromotionPolicy = PromotionConfig.policy
    ): PromotionResolution {
        val itemTotal = lines.sumOfMoney { it.lineTotal }
        if (itemTotal.isZero) return PromotionResolution.NONE

        // ---- 1. Evaluate every promotion independently -----------------------
        val candidates = mutableListOf<Pair<Promotion, AppliedPromotion>>()
        val declined = mutableListOf<DeclinedPromotion>()

        for (p in promotions.sortedWith(compareByDescending<Promotion> { it.priority }.thenBy { it.id })) {
            when (val v = evaluateOne(p, lines, isMember, couponCode)) {
                is Verdict.Eligible -> candidates += p to v.applied
                is Verdict.Ineligible -> if (v.showToCustomer) declined += DeclinedPromotion(p.id, p.title, v.reason)
            }
        }

        // ---- 2. Stacking ----------------------------------------------------
        val stackables = candidates.filter { it.first.stackable }
        val exclusives = candidates.filter { !it.first.stackable }

        // Best exclusive by discount, then priority, then id — deterministic.
        val bestExclusive = exclusives.maxWithOrNull(
            compareBy<Pair<Promotion, AppliedPromotion>> { it.second.discount }
                .thenBy { it.first.priority }
                .thenByDescending { it.first.id }
        )
        exclusives.filter { it !== bestExclusive }.forEach { (p, a) ->
            declined += DeclinedPromotion(
                p.id, p.title,
                reason = "A better offer was applied to this order.",
                wouldHaveSaved = a.discount.takeIf { it.isPositive }
            )
        }

        // ---- 3. Club vs the best exclusive ----------------------------------
        var clubApplied = clubDiscount.isPositive
        var chosenExclusive = bestExclusive
        var note: String? = null

        if (!policy.clubStacksWithPromotions && clubApplied && bestExclusive != null) {
            val (p, a) = bestExclusive
            if (a.discount > clubDiscount) {
                // The promotion wins. Club is set aside for THIS order and the
                // customer is told exactly what happened.
                clubApplied = false
                note = "Best offer applied — ${p.title} saves you ${a.discount}; " +
                    "your Club discount would have saved $clubDiscount."
            } else {
                chosenExclusive = null
                declined += DeclinedPromotion(
                    p.id, p.title,
                    reason = "Cannot be combined with your Club discount, which saves you more.",
                    wouldHaveSaved = a.discount
                )
                note = "Best offer applied — Club saves you $clubDiscount; " +
                    "${p.title} would have saved ${a.discount}."
            }
        }

        val applied = (stackables + listOfNotNull(chosenExclusive)).map { it.second }

        // ---- 4. Money guards ------------------------------------------------
        val effectiveClub = if (clubApplied) clubDiscount else Money.ZERO
        val rawPromo = applied.sumOfMoney { it.discount }
        // Discounts can never exceed what the items cost.
        val promoDiscount = minOf(rawPromo, (itemTotal - effectiveClub).coerceAtLeast(Money.ZERO))

        return PromotionResolution(
            applied = applied,
            declined = declined,
            promotionDiscount = promoDiscount,
            freeDelivery = applied.any { it.freeDelivery },
            clubApplied = clubApplied,
            clubDiscount = effectiveClub,
            bestOfferNote = note
        )
    }

    // -----------------------------------------------------------------------

    private sealed interface Verdict {
        data class Eligible(val applied: AppliedPromotion) : Verdict
        /** [showToCustomer] false for silent misses (wrong audience, coupon not entered). */
        data class Ineligible(val reason: String, val showToCustomer: Boolean) : Verdict
    }

    private fun evaluateOne(
        p: Promotion,
        lines: List<CartLine>,
        isMember: Boolean,
        couponCode: String?
    ): Verdict {
        // Audience. A members-only offer is not "declined" to a non-member in
        // the cart; it is simply not theirs. Club landing sells it instead.
        when (p.audience) {
            PromotionAudience.MEMBERS_ONLY -> if (!isMember) return Verdict.Ineligible("Members only", false)
            PromotionAudience.NON_MEMBERS_ONLY -> if (isMember) return Verdict.Ineligible("Non-members only", false)
            PromotionAudience.EVERYONE -> Unit
        }

        // Coupons apply only when entered. An un-entered coupon is not a
        // "missed" offer to nag about; it is a code the customer may not have.
        if (p.isCoupon && !p.couponCode.equals(couponCode?.trim(), ignoreCase = true)) {
            return Verdict.Ineligible("Coupon not entered", false)
        }

        // Eligible base: which lines this promotion may touch.
        val scoped = lines.filter { line ->
            line.product.id !in p.excludedProductIds && when (p.scope) {
                PromotionScope.CART -> true
                PromotionScope.PRODUCT -> line.product.id in p.scopeIds
                PromotionScope.CATEGORY -> line.product.categoryId in p.scopeIds
            }
        }
        val base = scoped.sumOfMoney { it.lineTotal }
        if (scoped.isEmpty() || base.isZero) {
            return Verdict.Ineligible(
                when (p.scope) {
                    PromotionScope.CART -> "Your cart is empty."
                    else -> "No eligible items in your cart."
                },
                // Coupons the customer typed deserve a visible reason; auto
                // offers with nothing eligible are just quiet.
                showToCustomer = p.isCoupon
            )
        }
        if (base < p.minOrder) {
            val gap = p.minOrder - base
            return Verdict.Ineligible(
                when (p.scope) {
                    PromotionScope.CART -> "Add $gap more to unlock this offer."
                    else -> "Add $gap more of eligible items to unlock this offer."
                },
                showToCustomer = true
            )
        }

        // Value.
        val raw: Money = when (p.type) {
            PromotionType.PERCENT_OFF -> base.percentOf(p.percent ?: 0) // floor, integer paise
            PromotionType.FLAT_OFF -> p.flat ?: Money.ZERO
            PromotionType.BUY_X_GET_Y -> buyXGetY(scoped, p.buyQuantity ?: 0, p.getQuantity ?: 0)
            PromotionType.FREE_DELIVERY -> Money.ZERO
        }
        val capped = p.maxDiscount?.let { minOf(raw, it) } ?: raw
        // Never more than the items it applies to.
        val discount = minOf(capped, base).coerceAtLeast(Money.ZERO)

        if (p.type != PromotionType.FREE_DELIVERY && discount.isZero) {
            return Verdict.Ineligible("This offer doesn't reduce your total.", showToCustomer = false)
        }

        return Verdict.Eligible(
            AppliedPromotion(
                promotionId = p.id,
                title = p.title,
                discount = discount,
                explanation = explain(p, discount, capped != raw),
                freeDelivery = p.type == PromotionType.FREE_DELIVERY
            )
        )
    }

    /**
     * Buy X get Y: for every full group of (X+Y) units of an eligible product,
     * the cheapest Y units are free. Computed per product line, then summed.
     */
    private fun buyXGetY(scoped: List<CartLine>, buy: Int, get: Int): Money {
        if (buy <= 0 || get <= 0) return Money.ZERO
        val group = buy + get
        return scoped.sumOfMoney { line ->
            val freeUnits = (line.quantity / group) * get
            line.product.price * freeUnits
        }
    }

    private fun explain(p: Promotion, discount: Money, wasCapped: Boolean): String = when (p.type) {
        PromotionType.PERCENT_OFF ->
            "${p.percent}% off" + scopeWord(p) + if (wasCapped) ", capped at ${p.maxDiscount}" else ""
        PromotionType.FLAT_OFF -> "$discount off" + scopeWord(p)
        PromotionType.BUY_X_GET_Y -> "Buy ${p.buyQuantity}, get ${p.getQuantity} free" + scopeWord(p)
        PromotionType.FREE_DELIVERY -> "Free delivery on this order"
    }

    private fun scopeWord(p: Promotion): String = when (p.scope) {
        PromotionScope.CART -> " your order"
        PromotionScope.PRODUCT -> " selected items"
        PromotionScope.CATEGORY -> " eligible items"
    }
}
