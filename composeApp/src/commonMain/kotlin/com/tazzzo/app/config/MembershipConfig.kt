package com.tazzzo.app.config

import com.tazzzo.app.data.model.MembershipBenefit
import com.tazzzo.app.data.model.MembershipDiscountRule
import com.tazzzo.app.data.model.Money
import com.tazzzo.app.data.model.MembershipOrderMilestone
import com.tazzzo.app.data.model.MembershipPlan
import com.tazzzo.app.data.model.MembershipRewardType
import com.tazzzo.app.data.model.MembershipSpendMilestone

/**
 * Tazzzo Club — the ONE place its numbers live.
 *
 * ₹99, 5%, ₹500, ₹5,000, 10%, 3 orders — none of these appear anywhere else
 * in source. Every screen (landing page, cart line, receipt, progress card)
 * renders from [MembershipConfig.plan], the same discipline `AppConfig`
 * already applies to delivery promises and coin rules.
 *
 * [DECISION — business proposal, not yet a signed launch policy] These are
 * the values the founder specified when commissioning this wave (2026-09-05):
 * ₹99 membership; 5% on orders ≥ ₹500; 10% after ₹5,000 cumulative spend;
 * a reward after 3 eligible orders. Same status as D4/D5/D6 in BLOCKERS.md —
 * live and usable for the pre-backend build, but still config, not something
 * baked into a screen, precisely so a real policy change costs one edit here.
 */
object MembershipConfig {

    private val baseDiscount = MembershipDiscountRule(
        percent = 5,
        minOrderValue = Money.ofRupees(500),
        maxDiscount = null
    )

    private val higherDiscount = MembershipDiscountRule(
        percent = 10,
        minOrderValue = Money.ofRupees(500),
        maxDiscount = null
    )

    private val spendMilestone5000 = MembershipSpendMilestone(
        id = "spend-5000",
        threshold = Money.ofRupees(5_000),
        unlockedDiscount = higherDiscount,
        title = "Higher savings unlocked",
        description = "10% off eligible orders ₹500+, for the rest of your membership."
    )

    /** "Fresh fruits up to ₹100" — the example reward named in the business proposal. */
    private val orderMilestone3 = MembershipOrderMilestone(
        id = "orders-3",
        requiredOrders = 3,
        rewardType = MembershipRewardType.FREE_ITEM_CREDIT,
        rewardTitle = "Fresh fruits up to ₹100",
        rewardValue = Money.ofRupees(100),
        eligibleCategoryIds = listOf("fruits"),   // Category id from MockCatalog — a real aisle, not invented.
        expiryDaysAfterUnlock = 14
    )

    private val benefits = listOf(
        MembershipBenefit(
            id = "discount",
            emoji = "🏷️",
            title = "5% OFF",
            description = "On eligible orders ₹500+"
        ),
        MembershipBenefit(
            id = "milestone",
            emoji = "📈",
            title = "Grow your savings",
            description = "10% off after ₹5,000 spent"
        ),
        MembershipBenefit(
            id = "rewards",
            emoji = "🎁",
            title = "Rewards",
            description = "Unlock rewards as you shop"
        ),
        MembershipBenefit(
            id = "member-offers",
            emoji = "✨",
            title = "Member-only offers",
            description = "Extra value on selected products and festivals"
        )
    )

    /**
     * Annual by default. Exact renewal/expiry behaviour is business policy,
     * not yet finalised — same caveat as D4/D5/D6: usable for this build,
     * pending sign-off before launch.
     */
    val plan: MembershipPlan = MembershipPlan(
        id = "club-99",
        name = "Tazzzo Club",
        tagline = "More you shop. More you save. More you unlock.",
        price = Money.ofRupees(99),
        periodDays = 365,
        discountRule = baseDiscount,
        spendMilestones = listOf(spendMilestone5000),
        orderMilestones = listOf(orderMilestone3),
        benefits = benefits
    )
}
