package com.tazzzo.app.data.repository

import com.tazzzo.app.config.MembershipCalculator
import com.tazzzo.app.config.MembershipConfig
import com.tazzzo.app.data.local.PersistentStore
import com.tazzzo.app.data.model.MembershipState
import com.tazzzo.app.data.model.MembershipStatus
import com.tazzzo.app.data.model.MembershipTransaction

/**
 * The customer's Tazzzo Club standing.
 *
 * Every mutation here is IDEMPOTENT on a transaction id or an order id. That
 * is not defensive style, it is the lesson from the coin-credit defect this
 * project already shipped once: a replayed success callback must never grant
 * a second membership, a second milestone, or a second reward. The types make
 * the safe path the only path — [activate] takes a transaction and returns
 * the same state if it has already been applied.
 */
interface MembershipRepository {
    suspend fun getState(): MembershipState

    /**
     * Activates membership for a VERIFIED payment.
     *
     * Idempotent: calling twice with the same [MembershipTransaction.id] is a
     * no-op that returns the already-active state. Callers must only reach
     * here after server-side signature verification succeeded.
     */
    suspend fun activate(transaction: MembershipTransaction): MembershipState

    /**
     * Records one completed order against the member's progress.
     *
     * Idempotent on [orderId]: re-delivering the same order (a replayed
     * placement, a restored session, a retried network call) must not advance
     * spend, savings, order count or milestones twice.
     */
    suspend fun recordEligibleOrder(
        orderId: String,
        itemTotalRupees: Int,
        discountAppliedRupees: Int,
        placedAtLabel: String
    ): MembershipState

    suspend fun setStatus(status: MembershipStatus): MembershipState
}

/**
 * Local implementation, persisted through [PersistentStore].
 *
 * Becomes a thin cache in front of the backend once one exists; the
 * idempotency guarantees above must then be enforced SERVER-side too, since a
 * local guard protects a single device only.
 */
class LocalMembershipRepository(
    private val store: PersistentStore?
) : MembershipRepository {

    private var cached: MembershipState? = null

    /** Order ids already counted. Persisted inside the state so a restart cannot double-count. */
    private fun MembershipState.hasCounted(orderId: String) = orderId in countedOrderIds

    override suspend fun getState(): MembershipState =
        cached ?: (store?.loadMembership() ?: MembershipState()).also { cached = it }

    private fun persist(state: MembershipState): MembershipState {
        cached = state
        store?.saveMembership(state)
        return state
    }

    override suspend fun activate(transaction: MembershipTransaction): MembershipState {
        val current = getState()
        // Replay guard: the same verified payment can arrive more than once
        // (retry after a dropped response, restored session, duplicate
        // callback). One payment, one membership.
        if (current.lastTransactionId == transaction.id && current.isActive) return current
        return persist(
            current.copy(
                status = MembershipStatus.ACTIVE,
                planId = transaction.planId,
                activatedAtLabel = transaction.placedAtLabel,
                lastTransactionId = transaction.id
            )
        )
    }

    override suspend fun recordEligibleOrder(
        orderId: String,
        itemTotalRupees: Int,
        discountAppliedRupees: Int,
        placedAtLabel: String
    ): MembershipState {
        val current = getState()
        if (!current.isActive) return current
        if (current.hasCounted(orderId)) return current   // replay guard
        val advanced = MembershipCalculator.applyEligibleOrder(
            plan = MembershipConfig.plan,
            state = current,
            itemTotalRupees = itemTotalRupees,
            discountAppliedRupees = discountAppliedRupees,
            unlockedAtLabel = placedAtLabel
        )
        return persist(advanced.copy(countedOrderIds = current.countedOrderIds + orderId))
    }

    override suspend fun setStatus(status: MembershipStatus): MembershipState =
        persist(getState().copy(status = status))
}
