package com.tazzzo.app.config

import com.tazzzo.app.data.model.MembershipDiscountRule
import com.tazzzo.app.data.model.MembershipEligibility
import com.tazzzo.app.data.model.MembershipOrderMilestone
import com.tazzzo.app.data.model.MembershipPlan
import com.tazzzo.app.data.model.MembershipReward
import com.tazzzo.app.data.model.MembershipRewardStatus
import com.tazzzo.app.data.model.MembershipState

/**
 * Pure membership maths — no I/O, no Compose, trivially unit-tested.
 *
 * Mirrors [BillCalculator]'s own rule for itself: if a club number appears on
 * screen, it was computed here, from a [MembershipPlan], and nowhere else.
 */
object MembershipCalculator {

    /**
     * Which discount rule currently applies to a member, given their
     * cumulative spend. A spend milestone's [MembershipSpendMilestone] rule
     * REPLACES the plan's base rule once crossed — it does not stack with it.
     */
    fun activeDiscountRule(plan: MembershipPlan, cumulativeSpendRupees: Int): MembershipDiscountRule {
        val unlocked = plan.spendMilestones
            .filter { cumulativeSpendRupees >= it.thresholdRupees }
            .maxByOrNull { it.thresholdRupees }
        return unlocked?.unlockedDiscount ?: plan.discountRule
    }

    /**
     * Evaluates one cart/order against the customer's plan and standing.
     *
     * Not a member → [MembershipEligibility.isMember] false and a real
     * `amountToUnlockRupees` computed against the BASE rule, so a non-member
     * still sees an honest "join to save ₹X" number rather than a zero.
     */
    fun evaluate(
        itemTotalRupees: Int,
        isMember: Boolean,
        plan: MembershipPlan,
        cumulativeSpendRupees: Int = 0
    ): MembershipEligibility {
        val rule = if (isMember) activeDiscountRule(plan, cumulativeSpendRupees) else plan.discountRule
        val eligible = itemTotalRupees >= rule.minOrderValueRupees
        val discount = if (isMember) rule.discountFor(itemTotalRupees) else 0
        val amountToUnlock = if (eligible) 0 else (rule.minOrderValueRupees - itemTotalRupees).coerceAtLeast(0)
        return MembershipEligibility(
            isMember = isMember,
            isEligible = isMember && eligible,
            discountRupees = discount,
            amountToUnlockRupees = amountToUnlock,
            appliedRule = if (isMember) rule else null
        )
    }

    /**
     * Illustrative "why join" example on the landing page — an EXAMPLE, never
     * a promise. The landing screen is responsible for labelling it as such;
     * this function only does the maths against a sample order value.
     */
    fun exampleSavings(plan: MembershipPlan, exampleOrderRupees: Int): Int =
        plan.discountRule.discountFor(exampleOrderRupees)

    /** Progress toward the next unclaimed spend milestone, or null if all are unlocked. */
    fun nextSpendMilestone(
        plan: MembershipPlan,
        cumulativeSpendRupees: Int
    ): Pair<com.tazzzo.app.data.model.MembershipSpendMilestone, Int>? {
        val next = plan.spendMilestones
            .filter { cumulativeSpendRupees < it.thresholdRupees }
            .minByOrNull { it.thresholdRupees } ?: return null
        return next to (next.thresholdRupees - cumulativeSpendRupees).coerceAtLeast(0)
    }

    /** Progress toward the next order-count milestone whose reward has not yet been unlocked. */
    fun nextOrderMilestone(
        plan: MembershipPlan,
        state: MembershipState
    ): Pair<MembershipOrderMilestone, Int>? {
        val unlockedIds = state.rewards.map { it.milestoneId }.toSet()
        val next = plan.orderMilestones
            .filter { it.id !in unlockedIds }
            .minByOrNull { it.requiredOrders } ?: return null
        val remaining = (next.requiredOrders - state.eligibleOrderCount).coerceAtLeast(0)
        return next to remaining
    }

    /**
     * Applies one eligible order's outcome to the member's running state:
     * accrues spend and savings, advances the order count, unlocks any spend
     * milestone just crossed, and unlocks any order-count reward just earned.
     *
     * Pure — takes a state, returns the next one. The caller decides whether
     * and how to persist it and whether to show a milestone-unlocked moment
     * (compare [MembershipState.unlockedSpendMilestoneIds] and `.rewards`
     * before/after this call to know exactly what changed).
     */
    fun applyEligibleOrder(
        plan: MembershipPlan,
        state: MembershipState,
        itemTotalRupees: Int,
        discountAppliedRupees: Int,
        unlockedAtLabel: String
    ): MembershipState {
        val newSpend = state.cumulativeSpendRupees + itemTotalRupees
        val newSavings = state.cumulativeSavingsRupees + discountAppliedRupees
        val newOrderCount = state.eligibleOrderCount + 1

        val newlyUnlockedSpend = plan.spendMilestones
            .filter { it.thresholdRupees <= newSpend && it.id !in state.unlockedSpendMilestoneIds }
            .map { it.id }
        val unlockedSpendIds = state.unlockedSpendMilestoneIds + newlyUnlockedSpend

        val alreadyUnlockedRewardIds = state.rewards.map { it.milestoneId }.toSet()
        val newlyUnlockedRewards = plan.orderMilestones
            .filter { it.requiredOrders <= newOrderCount && it.id !in alreadyUnlockedRewardIds }
            .map { milestone ->
                MembershipReward(
                    milestoneId = milestone.id,
                    status = MembershipRewardStatus.UNLOCKED,
                    unlockedAtLabel = unlockedAtLabel,
                    expiresAtLabel = null   // caller may compute a real date from expiryDaysAfterUnlock
                )
            }

        return state.copy(
            cumulativeSpendRupees = newSpend,
            cumulativeSavingsRupees = newSavings,
            eligibleOrderCount = newOrderCount,
            unlockedSpendMilestoneIds = unlockedSpendIds,
            rewards = state.rewards + newlyUnlockedRewards
        )
    }
}
