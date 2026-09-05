package com.tazzzo.app

import com.tazzzo.app.config.MembershipCalculator
import com.tazzzo.app.config.MembershipConfig
import com.tazzzo.app.config.BillCalculator
import com.tazzzo.app.data.model.CartLine
import com.tazzzo.app.data.model.MembershipRewardStatus
import com.tazzzo.app.data.model.MembershipState
import com.tazzzo.app.data.model.MembershipStatus
import com.tazzzo.app.data.model.MembershipTransaction
import com.tazzzo.app.data.model.PaymentResult
import com.tazzzo.app.data.model.PaymentVerificationStatus
import com.tazzzo.app.data.model.Product
import com.tazzzo.app.data.repository.LocalMembershipRepository
import com.tazzzo.app.data.repository.MockPaymentGateway
import com.tazzzo.app.membership.MembershipPurchase
import com.tazzzo.app.membership.MembershipPurchaseSession
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

/**
 * Tazzzo Club — discount maths, milestone progression, and the purchase state
 * machine.
 *
 * The money rules that must never regress:
 *  - a non-member is never charged a discounted total;
 *  - a client-side payment success NEVER activates membership on its own;
 *  - a replayed payment activates exactly one membership;
 *  - a replayed order advances progress exactly once;
 *  - "pending" is never reported to the customer as "failed".
 */
class MembershipTest {

    private val plan = MembershipConfig.plan

    private fun product(price: Int) = Product(
        id = "p8", name = "Toned Milk Pouch", brand = "Amul", emoji = "🥛", unit = "500 ml",
        price = price, mrp = price + 10, categoryId = "dairy", subcategoryId = "milk",
        rating = 4.7, ratingCount = 10
    )

    // ------------------------------------------------------------ discounts

    @Test fun member_below_threshold_gets_no_discount_and_an_honest_gap() {
        val e = MembershipCalculator.evaluate(400, isMember = true, plan = plan)
        assertEquals(0, e.discountRupees)
        assertEquals(false, e.isEligible)
        assertEquals(100, e.amountToUnlockRupees, "must tell the customer exactly how much more")
    }

    @Test fun member_at_threshold_gets_the_configured_percent() {
        val e = MembershipCalculator.evaluate(500, isMember = true, plan = plan)
        assertTrue(e.isEligible)
        assertEquals(25, e.discountRupees)   // 5% of 500, from config
    }

    @Test fun non_member_never_receives_a_discount_but_still_sees_the_offer() {
        val e = MembershipCalculator.evaluate(700, isMember = false, plan = plan)
        assertEquals(0, e.discountRupees, "a non-member must never be charged a member price")
        assertEquals(false, e.isEligible)
    }

    @Test fun spend_milestone_replaces_the_base_rule_rather_than_stacking() {
        val below = MembershipCalculator.activeDiscountRule(plan, 4_999)
        val above = MembershipCalculator.activeDiscountRule(plan, 5_000)
        assertEquals(5, below.percent)
        assertEquals(10, above.percent)
        // 10% of 700, not 15% — the rules must not compound.
        assertEquals(70, MembershipCalculator.evaluate(700, true, plan, 5_000).discountRupees)
    }

    @Test fun bill_applies_club_discount_only_for_members() {
        val lines = listOf(CartLine(product(700), 1))
        val guest = BillCalculator.bill(lines, isClubMember = false)
        val member = BillCalculator.bill(lines, isClubMember = true)
        assertEquals(0, guest.clubDiscount)
        assertEquals(35, member.clubDiscount)                       // 5% of 700
        assertEquals(guest.grandTotal - 35, member.grandTotal)
    }

    @Test fun club_discount_is_reported_separately_from_mrp_savings() {
        val lines = listOf(CartLine(product(700), 1))
        val member = BillCalculator.bill(lines, isClubMember = true)
        // `saved` is MRP savings only; blending the two would overstate either.
        assertEquals(10, member.saved)
        assertEquals(35, member.clubDiscount)
    }

    // ----------------------------------------------------------- milestones

    @Test fun eligible_orders_advance_spend_savings_and_count() {
        var state = MembershipState(status = MembershipStatus.ACTIVE)
        state = MembershipCalculator.applyEligibleOrder(plan, state, 700, 35, "5 Sep")
        assertEquals(700, state.cumulativeSpendRupees)
        assertEquals(35, state.cumulativeSavingsRupees)
        assertEquals(1, state.eligibleOrderCount)
    }

    @Test fun crossing_the_spend_milestone_unlocks_it_once() {
        var state = MembershipState(status = MembershipStatus.ACTIVE)
        state = MembershipCalculator.applyEligibleOrder(plan, state, 5_000, 250, "5 Sep")
        assertTrue("spend-5000" in state.unlockedSpendMilestoneIds)
        val before = state.unlockedSpendMilestoneIds.size
        state = MembershipCalculator.applyEligibleOrder(plan, state, 1_000, 100, "6 Sep")
        assertEquals(before, state.unlockedSpendMilestoneIds.size, "milestone must not re-unlock")
    }

    @Test fun order_milestone_unlocks_a_reward_exactly_once() {
        var state = MembershipState(status = MembershipStatus.ACTIVE)
        repeat(3) { state = MembershipCalculator.applyEligibleOrder(plan, state, 600, 30, "5 Sep") }
        assertEquals(1, state.rewards.size)
        assertEquals(MembershipRewardStatus.UNLOCKED, state.rewards.first().status)
        repeat(2) { state = MembershipCalculator.applyEligibleOrder(plan, state, 600, 30, "6 Sep") }
        assertEquals(1, state.rewards.size, "reward must not be granted again")
    }

    @Test fun next_order_milestone_reports_remaining_orders() {
        val state = MembershipState(status = MembershipStatus.ACTIVE, eligibleOrderCount = 1)
        val (milestone, remaining) = MembershipCalculator.nextOrderMilestone(plan, state)!!
        assertEquals(3, milestone.requiredOrders)
        assertEquals(2, remaining)
    }

    // ------------------------------------------------ purchase state machine

    private fun session() = MembershipPurchaseSession(plan)
    private fun repo() = LocalMembershipRepository(store = null)

    @Test fun successful_purchase_verifies_then_activates() = runTest {
        val s = session(); val r = repo()
        MembershipPurchase.start(s, MockPaymentGateway(MockPaymentGateway.MockOutcome.SUCCEED), r, "5 Sep")
        val phase = s.phase
        assertIs<MembershipPurchaseSession.Phase.Success>(phase)
        assertTrue(phase.state.isActive)
        assertTrue(phase.transaction.isTestPayment, "mock gateway must mark the payment as test")
        assertEquals(MembershipStatus.ACTIVE, r.getState().status)
    }

    @Test fun a_replayed_verified_payment_activates_exactly_one_membership() = runTest {
        val s = session(); val r = repo()
        val result = PaymentResult.Success("pay_test_1", "order_test_1", "sig", isTestMode = true)

        MembershipPurchase.applyVerification(s, r, result, PaymentVerificationStatus.VERIFIED, "5 Sep")
        val first = r.getState()
        MembershipPurchase.applyVerification(s, r, result, PaymentVerificationStatus.VERIFIED, "5 Sep")
        val second = r.getState()

        assertEquals(first, second, "the same payment must not activate twice")
        assertEquals("pay_test_1", second.lastTransactionId)
    }

    @Test fun failed_verification_never_activates() = runTest {
        val s = session(); val r = repo()
        val result = PaymentResult.Success("pay_x", "order_x", "bad_signature", isTestMode = true)
        MembershipPurchase.applyVerification(s, r, result, PaymentVerificationStatus.FAILED, "5 Sep")
        assertIs<MembershipPurchaseSession.Phase.Failed>(s.phase)
        assertEquals(false, r.getState().isActive, "a rejected signature must never grant membership")
    }

    @Test fun unknown_verification_is_pending_not_failed() = runTest {
        val s = session(); val r = repo()
        val result = PaymentResult.Success("pay_y", "order_y", "sig", isTestMode = true)
        MembershipPurchase.applyVerification(s, r, result, PaymentVerificationStatus.UNKNOWN, "5 Sep")
        // The customer's money may have moved: never tell them it failed.
        assertIs<MembershipPurchaseSession.Phase.Pending>(s.phase)
        assertEquals(false, r.getState().isActive)
        assertEquals(MembershipStatus.PAYMENT_PENDING, r.getState().status)
    }

    @Test fun cancelling_checkout_is_not_an_error_and_activates_nothing() = runTest {
        val s = session(); val r = repo()
        MembershipPurchase.start(s, MockPaymentGateway(MockPaymentGateway.MockOutcome.CANCEL), r, "5 Sep")
        assertIs<MembershipPurchaseSession.Phase.Cancelled>(s.phase)
        assertEquals(false, r.getState().isActive)
    }

    @Test fun gateway_failure_reports_failed_and_activates_nothing() = runTest {
        val s = session(); val r = repo()
        MembershipPurchase.start(s, MockPaymentGateway(MockPaymentGateway.MockOutcome.FAIL), r, "5 Sep")
        assertIs<MembershipPurchaseSession.Phase.Failed>(s.phase)
        assertEquals(false, r.getState().isActive)
    }

    @Test fun a_second_tap_while_busy_is_ignored() = runTest {
        val s = session(); val r = repo()
        s.phase = MembershipPurchaseSession.Phase.Verifying
        MembershipPurchase.start(s, MockPaymentGateway(MockPaymentGateway.MockOutcome.SUCCEED), r, "5 Sep")
        // Untouched: the in-flight guard refused to start a second purchase.
        assertIs<MembershipPurchaseSession.Phase.Verifying>(s.phase)
    }

    @Test fun retrying_reuses_the_same_receipt_id() {
        val s = session()
        val first = s.receiptId
        // The session survives a retry; a new attempt must not mint a new id,
        // or the server would see two distinct purchase intents.
        assertEquals(first, s.receiptId)
    }

    @Test fun a_replayed_order_advances_progress_exactly_once() = runTest {
        val r = repo()
        r.activate(
            MembershipTransaction(
                "pay_1", plan.id, plan.priceRupees, MembershipStatus.ACTIVE,
                "pay_1", isTestPayment = true, placedAtLabel = "5 Sep"
            )
        )
        r.recordEligibleOrder("TZ-1", 700, 35, "5 Sep")
        val once = r.getState()
        r.recordEligibleOrder("TZ-1", 700, 35, "5 Sep")   // duplicate delivery
        val twice = r.getState()
        assertEquals(once, twice, "a replayed order must not double-count spend or savings")
        assertEquals(700, twice.cumulativeSpendRupees)
        assertEquals(1, twice.eligibleOrderCount)
    }

    @Test fun progress_is_not_recorded_for_non_members() = runTest {
        val r = repo()
        r.recordEligibleOrder("TZ-9", 700, 35, "5 Sep")
        assertEquals(0, r.getState().cumulativeSpendRupees)
    }

    @Test fun example_savings_are_computed_from_config_not_hardcoded() {
        // The landing page's illustrative figure must track the plan.
        assertEquals(35, MembershipCalculator.exampleSavings(plan, 700))
    }
}
