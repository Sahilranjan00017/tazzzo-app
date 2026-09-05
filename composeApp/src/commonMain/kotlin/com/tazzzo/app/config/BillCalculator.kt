package com.tazzzo.app.config

import com.tazzzo.app.data.model.BillSummary
import com.tazzzo.app.data.model.CartLine
import com.tazzzo.app.data.model.MembershipPlan
import com.tazzzo.app.data.model.Promotion

/**
 * The single source of truth for order money maths.
 *
 * Pure and dependency-free so it is trivially unit-tested; AppState and the
 * checkout both delegate here. If a number appears on a bill, it comes from
 * this file and nowhere else — the club discount is no exception, and is
 * computed via [MembershipCalculator], never re-derived in a screen.
 */
object BillCalculator {

    fun bill(
        lines: List<CartLine>,
        charges: ChargeRules = AppConfig.charges,
        coins: CoinRules = AppConfig.coins,
        redeemCoins: Int = 0,
        isClubMember: Boolean = false,
        clubPlan: MembershipPlan = MembershipConfig.plan,
        clubCumulativeSpendRupees: Int = 0,
        promotions: List<Promotion> = PromotionConfig.active,
        couponCode: String? = null,
        promotionPolicy: PromotionPolicy = PromotionConfig.policy
    ): BillSummary {
        val itemTotal = lines.sumOf { it.lineTotal }
        val mrpTotal = lines.sumOf { it.lineMrp }
        val handling = if (itemTotal == 0) 0 else charges.handlingFeeRupees
        val earned = if (coins.enabled) coins.coinsFor(itemTotal) else 0

        // Club's candidate discount, BEFORE stacking. The engine decides whether
        // it survives against a competing non-stackable promotion.
        val clubCandidate = if (itemTotal == 0) 0 else
            MembershipCalculator.evaluate(itemTotal, isClubMember, clubPlan, clubCumulativeSpendRupees)
                .discountRupees

        // One resolution, one place. Screens render it; nothing recomputes it.
        val promo = PromotionEngine.evaluate(
            lines = lines,
            promotions = promotions,
            isMember = isClubMember,
            couponCode = couponCode,
            clubDiscountRupees = clubCandidate,
            policy = promotionPolicy
        )
        val clubDiscount = promo.clubDiscountRupees
        val promotionDiscount = promo.promotionDiscountRupees

        val delivery = when {
            itemTotal == 0 -> 0
            promo.freeDelivery -> 0
            itemTotal >= charges.freeDeliveryAboveRupees -> 0
            else -> charges.deliveryFeeRupees
        }

        // Coins redeem against what is left AFTER discounts, never against
        // rupees that were already taken off.
        val discountedItems = (itemTotal - clubDiscount - promotionDiscount).coerceAtLeast(0)
        val redemption = redeemableValue(redeemCoins, discountedItems, coins)

        return BillSummary(
            itemTotal = itemTotal,
            itemMrpTotal = mrpTotal,
            deliveryFee = delivery,
            handlingCharge = handling,
            coinsEarned = earned,
            // Items can be discounted to zero; fees are never discounted below zero.
            grandTotal = discountedItems + delivery + handling - redemption,
            clubDiscount = clubDiscount,
            promotionDiscount = promotionDiscount,
            appliedPromotions = promo.applied,
            declinedPromotions = promo.declined,
            bestOfferNote = promo.bestOfferNote,
            freeDeliveryByPromotion = promo.freeDelivery
        )
    }

    /**
     * Rupee value of a coin redemption, clamped so it can never exceed the
     * item total, the customer's request, or the per-order cap.
     *
     * NOTE: redemption is exercised by tests but not yet exposed in the UI —
     * coin economics (decision D5) are not approved as production policy.
     */
    fun redeemableValue(requestedCoins: Int, itemTotalRupees: Int, coins: CoinRules = AppConfig.coins): Int {
        if (!coins.enabled || requestedCoins <= 0) return 0
        val capped = coins.maxRedeemPerOrder?.let { minOf(requestedCoins, it) } ?: requestedCoins
        return minOf(capped * coins.rupeesPerCoin, itemTotalRupees)
    }
}
