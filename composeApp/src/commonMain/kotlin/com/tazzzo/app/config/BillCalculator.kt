package com.tazzzo.app.config

import com.tazzzo.app.data.model.BillSummary
import com.tazzzo.app.data.model.CartLine

/**
 * The single source of truth for order money maths.
 *
 * Pure and dependency-free so it is trivially unit-tested; AppState and the
 * checkout both delegate here. If a number appears on a bill, it comes from
 * this file and nowhere else.
 */
object BillCalculator {

    fun bill(
        lines: List<CartLine>,
        charges: ChargeRules = AppConfig.charges,
        coins: CoinRules = AppConfig.coins,
        redeemCoins: Int = 0
    ): BillSummary {
        val itemTotal = lines.sumOf { it.lineTotal }
        val mrpTotal = lines.sumOf { it.lineMrp }
        val delivery =
            if (itemTotal >= charges.freeDeliveryAboveRupees || itemTotal == 0) 0
            else charges.deliveryFeeRupees
        val handling = if (itemTotal == 0) 0 else charges.handlingFeeRupees
        val earned = if (coins.enabled) coins.coinsFor(itemTotal) else 0
        val redemption = redeemableValue(redeemCoins, itemTotal, coins)
        return BillSummary(
            itemTotal = itemTotal,
            itemMrpTotal = mrpTotal,
            deliveryFee = delivery,
            handlingCharge = handling,
            coinsEarned = earned,
            grandTotal = itemTotal + delivery + handling - redemption
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
