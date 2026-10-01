package com.tazzzo.app.data.model

import kotlinx.serialization.Serializable

/*
 * Tazzzo Club — paid membership domain model.
 *
 * Every number that can change (price, discount %, thresholds, caps, expiry)
 * lives in ONE place: config/MembershipConfig.kt. Nothing in this file or in
 * any screen hard-codes ₹99, 5%, ₹500 or ₹5,000 — they are read from a
 * MembershipPlan, exactly the way delivery promises and coin rules already
 * work in config/AppConfig.kt. Change the plan, and every surface (landing
 * page, cart line, receipt, progress card) follows without a code change.
 */

/**
 * Lifecycle of a customer's relationship with Tazzzo Club.
 *
 * `PaymentPending`/`PaymentFailed` are distinct from `PurchasePending`: the
 * latter is "we have not yet heard back from the payment gateway at all"
 * (network interruption after Checkout opened), the former is a payment the
 * gateway has explicitly told us is pending or has explicitly failed. The
 * distinction matters because only `PaymentFailed` may show "Try again"
 * immediately — a genuinely pending payment must be reconciled with the
 * backend first, or a retry risks a second charge.
 */
@Serializable
enum class MembershipStatus {
    NOT_MEMBER,
    PURCHASE_PENDING,
    ACTIVE,
    EXPIRED,
    CANCELLED,
    PAYMENT_FAILED,
    PAYMENT_PENDING
}

/** One line of the benefits list on the Club landing page. Display-only. */
@Serializable
data class MembershipBenefit(
    val id: String,
    val emoji: String,
    val title: String,
    val description: String
)

/**
 * A percentage discount on eligible orders.
 *
 * @param minOrderValue the cart must reach this item total before the
 *   discount applies — "eligible order" throughout the UI means "meets this".
 * @param maxDiscount cap on the value of one discount; null =
 *   uncapped. Percent-only rules are dangerous on large baskets without one.
 */
@Serializable
data class MembershipDiscountRule(
    val percent: Int,
    val minOrderValue: Money,
    val maxDiscount: Money? = null
) {
    init {
        Money.requireNonNegative(minOrderValue, "minOrderValue")
        maxDiscount?.let { Money.requireNonNegative(it, "maxDiscount") }
    }

    /** Discount for one order, zero if the order does not qualify. Floor, in integer paise. */
    fun discountFor(itemTotal: Money): Money {
        if (itemTotal < minOrderValue) return Money.ZERO
        val raw = itemTotal.percentOf(percent)
        return maxDiscount?.let { minOf(raw, it) } ?: raw
    }
}

/**
 * Crossing this much CUMULATIVE spend while a member unlocks a better
 * [unlockedDiscount] for all subsequent orders. Evaluated by
 * [com.tazzzo.app.config.MembershipCalculator], never guessed in a screen.
 */
@Serializable
data class MembershipSpendMilestone(
    val id: String,
    val threshold: Money,
    val unlockedDiscount: MembershipDiscountRule,
    val title: String,
    val description: String
)

/** What an order-count milestone reward actually is. */
@Serializable
enum class MembershipRewardType { FREE_ITEM_CREDIT, DISCOUNT_VOUCHER, BONUS_COINS }

/**
 * Completing [requiredOrders] ELIGIBLE orders (orders where the club discount
 * applied) unlocks a configured reward.
 *
 * @param eligibleCategoryIds if non-empty, the reward's value applies only
 *   within these catalogue categories (ids from [com.tazzzo.app.data.model.Category]).
 *   Empty = any category.
 * @param expiryDaysAfterUnlock how long the customer has to redeem after the
 *   milestone is reached; null = no expiry.
 */
@Serializable
data class MembershipOrderMilestone(
    val id: String,
    val requiredOrders: Int,
    val rewardType: MembershipRewardType,
    val rewardTitle: String,
    val rewardValue: Money?,
    val eligibleCategoryIds: List<String> = emptyList(),
    val expiryDaysAfterUnlock: Int? = null,
    val campaignId: String? = null
)

/** Status of one unlocked order-count reward instance for a customer. */
@Serializable
enum class MembershipRewardStatus { LOCKED, UNLOCKED, REDEEMED, EXPIRED }

/**
 * A customer-facing instance of an [MembershipOrderMilestone] — the reward
 * they actually see, with real unlock/expiry dates, not just the rule.
 */
@Serializable
data class MembershipReward(
    val milestoneId: String,
    val status: MembershipRewardStatus,
    val unlockedAtLabel: String? = null,
    val expiresAtLabel: String? = null
)

/**
 * The full configuration for one membership tier.
 *
 * Only one plan exists today ([com.tazzzo.app.config.MembershipConfig.plan]),
 * but the model is a list-of-rules shape from the start so a future second
 * tier is a config change, not a rewrite.
 */
@Serializable
data class MembershipPlan(
    val id: String,
    val name: String,
    val tagline: String,
    val price: Money,
    /** null = active until cancelled; otherwise days from activation. */
    val periodDays: Int?,
    val discountRule: MembershipDiscountRule,
    val spendMilestones: List<MembershipSpendMilestone> = emptyList(),
    val orderMilestones: List<MembershipOrderMilestone> = emptyList(),
    val benefits: List<MembershipBenefit> = emptyList()
)

/**
 * Result of checking a cart/order against the customer's current plan and
 * spend — the ONE function screens call to decide what to show. Never
 * computed ad hoc in a composable.
 */
@Serializable
data class MembershipEligibility(
    val isMember: Boolean,
    val isEligible: Boolean,
    val discount: Money,
    /** How much MORE the cart needs to reach the active discount rule. 0 if already eligible or not a member. */
    val amountToUnlock: Money,
    val appliedRule: MembershipDiscountRule?
)

/** A completed (or attempted) membership purchase — the payment record. */
@Serializable
data class MembershipTransaction(
    val id: String,
    val planId: String,
    val amount: Money,
    val status: MembershipStatus,
    val paymentReference: String?,
    val isTestPayment: Boolean,
    val placedAtLabel: String
)

/**
 * The customer's live relationship with Tazzzo Club — persisted locally
 * (`PersistentStore`) and mirrored from the backend once one exists.
 *
 * `cumulativeSpend` and `eligibleOrderCount` only advance on orders
 * where the club discount actually applied — see BLOCKERS.md: this must
 * become server-authoritative before launch, same as coin crediting.
 */
@Serializable
data class MembershipState(
    val status: MembershipStatus = MembershipStatus.NOT_MEMBER,
    val planId: String? = null,
    val activatedAtLabel: String? = null,
    val expiresAtLabel: String? = null,
    val cumulativeSpend: Money = Money.ZERO,
    val cumulativeSavings: Money = Money.ZERO,
    val eligibleOrderCount: Int = 0,
    val unlockedSpendMilestoneIds: Set<String> = emptySet(),
    val rewards: List<MembershipReward> = emptyList(),
    val lastTransactionId: String? = null,
    /**
     * Order ids already counted toward progress. Persisted so that a replayed
     * order — a duplicate callback, a restored session, a retried network
     * call — cannot advance spend, savings or milestones a second time. Same
     * discipline as the order/coin replay guard in `OrderPlacement`.
     */
    val countedOrderIds: Set<String> = emptySet()
) {
    val isActive: Boolean get() = status == MembershipStatus.ACTIVE
}
