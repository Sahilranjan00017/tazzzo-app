package com.tazzzo.app.config

import com.tazzzo.app.data.model.MembershipDiscountRule
import com.tazzzo.app.data.model.MembershipEligibility
import com.tazzzo.app.data.model.MembershipOrderMilestone
import com.tazzzo.app.data.model.MembershipPlan
import com.tazzzo.app.data.model.MembershipReward
import com.tazzzo.app.data.model.MembershipRewardStatus
import com.tazzzo.app.data.model.MembershipState
import com.tazzzo.app.data.model.Money

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
    fun activeDiscountRule(plan: MembershipPlan, cumulativeSpend: Money): MembershipDiscountRule {
        val unlocked = plan.spendMilestones
            .filter { cumulativeSpend >= it.threshold }
            .maxByOrNull { it.threshold }
        return unlocked?.unlockedDiscount ?: plan.discountRule
    }

    /**
     * Evaluates one cart/order against the customer's plan and standing.
     *
     * Not a member → [MembershipEligibility.isMember] false and a real
     * `amountToUnlock` computed against the BASE rule, so a non-member
     * still sees an honest "join to save ₹X" number rather than a zero.
     */
    fun evaluate(
        itemTotal: Money,
        isMember: Boolean,
        plan: MembershipPlan,
        cumulativeSpend: Money = Money.ZERO
    ): MembershipEligibility {
        val rule = if (isMember) activeDiscountRule(plan, cumulativeSpend) else plan.discountRule
        val eligible = itemTotal >= rule.minOrderValue
        val discount = if (isMember) rule.discountFor(itemTotal) else Money.ZERO
        val amountToUnlock = if (eligible) Money.ZERO else (rule.minOrderValue - itemTotal).coerceAtLeast(Money.ZERO)
        return MembershipEligibility(
            isMember = isMember,
            isEligible = isMember && eligible,
            discount = discount,
            amountToUnlock = amountToUnlock,
            appliedRule = if (isMember) rule else null
        )
    }

    /**
     * Illustrative "why join" example on the landing page — an EXAMPLE, never
     * a promise. The landing screen is responsible for labelling it as such;
     * this function only does the maths against a sample order value.
     */
    fun exampleSavings(plan: MembershipPlan, exampleOrder: Money): Money =
        plan.discountRule.discountFor(exampleOrder)

    /** Progress toward the next unclaimed spend milestone, or null if all are unlocked. */
    fun nextSpendMilestone(
        plan: MembershipPlan,
        cumulativeSpend: Money
    ): Pair<com.tazzzo.app.data.model.MembershipSpendMilestone, Money>? {
        val next = plan.spendMilestones
            .filter { cumulativeSpend < it.threshold }
            .minByOrNull { it.threshold } ?: return null
        return next to (next.threshold - cumulativeSpend).coerceAtLeast(Money.ZERO)
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
        itemTotal: Money,
        discountApplied: Money,
        unlockedAtLabel: String
    ): MembershipState {
        val newSpend = state.cumulativeSpend + itemTotal
        val newSavings = state.cumulativeSavings + discountApplied
        val newOrderCount = state.eligibleOrderCount + 1

        val newlyUnlockedSpend = plan.spendMilestones
            .filter { it.threshold <= newSpend && it.id !in state.unlockedSpendMilestoneIds }
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
            cumulativeSpend = newSpend,
            cumulativeSavings = newSavings,
            eligibleOrderCount = newOrderCount,
            unlockedSpendMilestoneIds = unlockedSpendIds,
            rewards = state.rewards + newlyUnlockedRewards
        )
    }
}
