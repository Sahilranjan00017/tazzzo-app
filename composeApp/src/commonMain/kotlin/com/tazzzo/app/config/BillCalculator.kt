package com.tazzzo.app.config

import com.tazzzo.app.data.model.BillSummary
import com.tazzzo.app.data.model.CartLine
import com.tazzzo.app.data.model.MembershipPlan
import com.tazzzo.app.data.model.Promotion
import com.tazzzo.app.data.model.DeliverySlot

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
        promotionPolicy: PromotionPolicy = PromotionConfig.policy,
        slot: DeliverySlot? = null,
        /** Voluntary amount added to this order. Never discounted, never netted. */
        tipRupees: Int = 0
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

        // Delivery: the chosen slot's own fee when one is chosen, else the flat
        // rule. Whatever the fee WOULD have been, if it is not charged the
        // difference is money the customer kept — reported as its own realised
        // line with the reason, so "Delivery FREE" is never an unexplained gift.
        val baseDeliveryFee = if (itemTotal == 0) 0 else (slot?.feeRupees ?: charges.deliveryFeeRupees)
        val (delivery, deliveryReason) = when {
            itemTotal == 0 -> 0 to null
            promo.freeDelivery -> 0 to "Free delivery offer applied"
            itemTotal >= charges.freeDeliveryAboveRupees ->
                0 to "Free on orders above ₹${charges.freeDeliveryAboveRupees}"
            slot != null && slot.feeRupees == 0 -> 0 to "Free for this slot"
            slot != null -> slot.feeRupees to slot.feeReason
            else -> charges.deliveryFeeRupees to null
        }
        // What was waived: the flat fee is the honest reference when the slot is
        // free or unknown; a paid slot waived by threshold/promo saves its own fee.
        val referenceFee = if (slot != null && slot.feeRupees > 0) slot.feeRupees else charges.deliveryFeeRupees
        val deliveryWaived = if (itemTotal > 0 && delivery == 0) referenceFee else 0

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
            grandTotal = discountedItems + delivery + handling - redemption + tipRupees,
            clubDiscount = clubDiscount,
            promotionDiscount = promotionDiscount,
            appliedPromotions = promo.applied,
            declinedPromotions = promo.declined,
            bestOfferNote = promo.bestOfferNote,
            freeDeliveryByPromotion = promo.freeDelivery,
            deliveryFeeWaivedRupees = deliveryWaived,
            deliveryFeeReason = deliveryReason,
            tip = tipRupees
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

/**
 * How close this basket is to free delivery.
 *
 * @param alreadyFree delivery costs nothing on this order
 * @param reason why it is free, when it already is
 * @param thresholdRupees the spend that earns free delivery
 * @param remainingRupees how much more to spend; 0 once [alreadyFree]
 * @param fraction 0f..1f progress toward the threshold, for the track
 */
data class FreeDeliveryProgress(
    val alreadyFree: Boolean,
    val reason: String?,
    val thresholdRupees: Int,
    val remainingRupees: Int,
    val fraction: Float,
)

/**
 * Progress toward free delivery, derived entirely from the bill and the live
 * charge configuration.
 *
 * The supplied cart mockup hard-codes "Shop for ₹176 more" against a ₹499
 * target. Neither figure belongs to this app — the threshold is
 * [AppConfig.charges.freeDeliveryAboveRupees] and it can change without a
 * release, so a literal here would start lying the first time ops moved it.
 *
 * The [alreadyFree] case matters as much as the progress one: when a promotion
 * or a free slot has already waived the fee, telling somebody to spend more to
 * unlock what they have would invent a hurdle to sell against.
 */
fun freeDeliveryProgress(bill: BillSummary): FreeDeliveryProgress {
    val threshold = AppConfig.charges.freeDeliveryAboveRupees
    val free = bill.deliveryFee == 0 && bill.itemTotal > 0
    val remaining = (threshold - bill.itemTotal).coerceAtLeast(0)
    return FreeDeliveryProgress(
        alreadyFree = free,
        reason = bill.deliveryFeeReason,
        thresholdRupees = threshold,
        remainingRupees = if (free) 0 else remaining,
        fraction = if (threshold <= 0) 1f
        else (bill.itemTotal.toFloat() / threshold).coerceIn(0f, 1f),
    )
}

/**
 * The price band a SKU falls into — "Under ₹29", "Under ₹49", "Under ₹99".
 *
 * The Home mockup shows "₹9 STORE" / "₹19 STORE" ribbons. Tazzzo has no
 * price-point store, and nothing in the catalogue sells at ₹9, so those bands
 * would label permanently empty shelves. A band derived from the item's own
 * price says the same useful thing and cannot be empty: the product is already
 * in it.
 *
 * Returns null above the top band, where "under" stops being a selling point.
 */
fun priceBandLabel(priceRupees: Int): String? = when {
    priceRupees <= 0 -> null
    priceRupees < 29 -> "Under ₹29"
    priceRupees < 49 -> "Under ₹49"
    priceRupees < 99 -> "Under ₹99"
    else -> null
}
