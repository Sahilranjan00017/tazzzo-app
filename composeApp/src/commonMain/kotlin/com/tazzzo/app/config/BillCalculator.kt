package com.tazzzo.app.config

import com.tazzzo.app.data.model.BillSummary
import com.tazzzo.app.data.model.CartLine
import com.tazzzo.app.data.model.MembershipPlan
import com.tazzzo.app.data.model.Promotion
import com.tazzzo.app.data.model.DeliverySlot
import com.tazzzo.app.data.model.Money
import com.tazzzo.app.data.model.sumOfMoney

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
        clubCumulativeSpend: Money = Money.ZERO,
        promotions: List<Promotion> = PromotionConfig.active,
        couponCode: String? = null,
        promotionPolicy: PromotionPolicy = PromotionConfig.policy,
        slot: DeliverySlot? = null,
        /** Voluntary amount added to this order. Never discounted, never netted. */
        tip: Money = Money.ZERO
    ): BillSummary {
        val itemTotal = lines.sumOfMoney { it.lineTotal }
        val mrpTotal = lines.sumOfMoney { it.lineMrp }
        val empty = itemTotal.isZero
        val handling = if (empty) Money.ZERO else charges.handlingFee
        val earned = if (coins.enabled) coins.coinsFor(itemTotal) else 0

        // Club's candidate discount, BEFORE stacking. The engine decides whether
        // it survives against a competing non-stackable promotion.
        val clubCandidate = if (empty) Money.ZERO else
            MembershipCalculator.evaluate(itemTotal, isClubMember, clubPlan, clubCumulativeSpend)
                .discount

        // One resolution, one place. Screens render it; nothing recomputes it.
        val promo = PromotionEngine.evaluate(
            lines = lines,
            promotions = promotions,
            isMember = isClubMember,
            couponCode = couponCode,
            clubDiscount = clubCandidate,
            policy = promotionPolicy
        )
        val clubDiscount = promo.clubDiscount
        val promotionDiscount = promo.promotionDiscount

        // Delivery: the chosen slot's own fee when one is chosen, else the flat
        // rule. Whatever the fee WOULD have been, if it is not charged the
        // difference is money the customer kept — reported as its own realised
        // line with the reason, so "Delivery FREE" is never an unexplained gift.
        val (delivery, deliveryReason) = when {
            empty -> Money.ZERO to null
            promo.freeDelivery -> Money.ZERO to "Free delivery offer applied"
            itemTotal >= charges.freeDeliveryAbove ->
                Money.ZERO to "Free on orders above ${charges.freeDeliveryAbove}"
            slot != null && slot.fee.isZero -> Money.ZERO to "Free for this slot"
            slot != null -> slot.fee to slot.feeReason
            else -> charges.deliveryFee to null
        }
        // What was waived: the flat fee is the honest reference when the slot is
        // free or unknown; a paid slot waived by threshold/promo saves its own fee.
        val referenceFee = if (slot != null && slot.fee.isPositive) slot.fee else charges.deliveryFee
        val deliveryWaived = if (!empty && delivery.isZero) referenceFee else Money.ZERO

        // Coins redeem against what is left AFTER discounts, never against
        // rupees that were already taken off.
        val discountedItems = (itemTotal - clubDiscount - promotionDiscount).coerceAtLeast(Money.ZERO)
        val redemption = redeemableValue(redeemCoins, discountedItems, coins)

        return BillSummary(
            itemTotal = itemTotal,
            itemMrpTotal = mrpTotal,
            deliveryFee = delivery,
            handlingCharge = handling,
            coinsEarned = earned,
            // Items can be discounted to zero; fees are never discounted below zero.
            grandTotal = discountedItems + delivery + handling - redemption + tip,
            clubDiscount = clubDiscount,
            promotionDiscount = promotionDiscount,
            appliedPromotions = promo.applied,
            declinedPromotions = promo.declined,
            bestOfferNote = promo.bestOfferNote,
            freeDeliveryByPromotion = promo.freeDelivery,
            deliveryFeeWaived = deliveryWaived,
            deliveryFeeReason = deliveryReason,
            tip = tip
        )
    }

    /**
     * Rupee value of a coin redemption, clamped so it can never exceed the
     * item total, the customer's request, or the per-order cap.
     *
     * NOTE: redemption is exercised by tests but not yet exposed in the UI —
     * coin economics (decision D5) are not approved as production policy.
     */
    fun redeemableValue(requestedCoins: Int, itemTotal: Money, coins: CoinRules = AppConfig.coins): Money {
        if (!coins.enabled || requestedCoins <= 0) return Money.ZERO
        val capped = coins.maxRedeemPerOrder?.let { minOf(requestedCoins, it) } ?: requestedCoins
        return minOf(coins.valuePerCoin * capped, itemTotal)
    }
}

/**
 * How close this basket is to free delivery.
 *
 * @param alreadyFree delivery costs nothing on this order
 * @param reason why it is free, when it already is
 * @param threshold the spend that earns free delivery
 * @param remaining how much more to spend; 0 once [alreadyFree]
 * @param fraction 0f..1f progress toward the threshold, for the track
 */
data class FreeDeliveryProgress(
    val alreadyFree: Boolean,
    val reason: String?,
    val threshold: Money,
    val remaining: Money,
    val fraction: Float,
)

/**
 * Progress toward free delivery, derived entirely from the bill and the live
 * charge configuration.
 *
 * The supplied cart mockup hard-codes "Shop for ₹176 more" against a ₹499
 * target. Neither figure belongs to this app — the threshold is
 * [AppConfig.charges.freeDeliveryAbove] and it can change without a
 * release, so a literal here would start lying the first time ops moved it.
 *
 * The [alreadyFree] case matters as much as the progress one: when a promotion
 * or a free slot has already waived the fee, telling somebody to spend more to
 * unlock what they have would invent a hurdle to sell against.
 */
fun freeDeliveryProgress(bill: BillSummary): FreeDeliveryProgress {
    val threshold = AppConfig.charges.freeDeliveryAbove
    val free = bill.deliveryFee.isZero && bill.itemTotal.isPositive
    val remaining = (threshold - bill.itemTotal).coerceAtLeast(Money.ZERO)
    return FreeDeliveryProgress(
        alreadyFree = free,
        reason = bill.deliveryFeeReason,
        threshold = threshold,
        remaining = if (free) Money.ZERO else remaining,
        // Display-only progress bar fraction (0..1); not money, never fed back into any amount.
        fraction = if (!threshold.isPositive) 1f
        else (bill.itemTotal.paise.toFloat() / threshold.paise.toFloat()).coerceIn(0f, 1f),
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
fun priceBandLabel(price: Money): String? = when {
    !price.isPositive -> null
    price < Money.ofRupees(29) -> "Under ₹29"
    price < Money.ofRupees(49) -> "Under ₹49"
    price < Money.ofRupees(99) -> "Under ₹99"
    else -> null
}
