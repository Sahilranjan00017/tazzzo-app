package com.tazzzo.app.membership

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.tazzzo.app.analytics.Analytics
import com.tazzzo.app.analytics.AnalyticsEvents
import com.tazzzo.app.data.model.MembershipPlan
import com.tazzzo.app.data.model.MembershipState
import com.tazzzo.app.data.model.MembershipStatus
import com.tazzzo.app.data.model.MembershipTransaction
import com.tazzzo.app.data.model.PaymentOrder
import com.tazzzo.app.data.model.PaymentResult
import com.tazzzo.app.data.model.PaymentVerificationStatus
import com.tazzzo.app.data.repository.MembershipRepository
import com.tazzzo.app.data.repository.PaymentGateway
import kotlin.random.Random

/**
 * One attempt to buy Tazzzo Club, as a state machine the UI observes.
 *
 * [receiptId] is minted ONCE per session and reused on every retry, so a
 * customer who taps "Try again" after a failure is replaying one purchase
 * rather than starting a second. It is the membership analogue of
 * `CheckoutSession.idempotencyKey`, and it exists for the same reason: the
 * server must be able to treat repeated attempts as the same intent.
 */
class MembershipPurchaseSession(val plan: MembershipPlan) {

    val receiptId: String =
        "club-" + Random.nextLong(100_000_000_000L, 999_999_999_999L).toString()

    var phase by mutableStateOf<Phase>(Phase.Idle)

    /**
     * Every state the purchase can be in.
     *
     * [Pending] is deliberately NOT [Failed]. It means the payment may well
     * have succeeded and we simply do not know — a dropped connection during
     * verification, or a gateway that reported "pending". Telling a customer
     * their payment failed when their money may have moved is the single
     * worst thing this flow can do, so the type system keeps the two apart
     * and only [Failed] offers an immediate retry.
     */
    sealed interface Phase {
        data object Idle : Phase
        /** Asking our backend to create the gateway order. */
        data object CreatingOrder : Phase
        data class ReadyForCheckout(val order: PaymentOrder) : Phase
        data class CheckoutOpen(val order: PaymentOrder) : Phase
        /** Server-side signature verification — the only thing that authorises activation. */
        data object Verifying : Phase
        data class Success(val transaction: MembershipTransaction, val state: MembershipState) : Phase
        /** Unknown or gateway-pending. Must be reconciled, never retried blindly. */
        data class Pending(val message: String) : Phase
        data class Failed(val reason: String, val retryable: Boolean) : Phase
        data object Cancelled : Phase
    }

    val isBusy: Boolean
        get() = phase is Phase.CreatingOrder || phase is Phase.CheckoutOpen ||
            phase is Phase.Verifying || phase is Phase.ReadyForCheckout
}

/**
 * The ONE path through which Tazzzo Club is purchased and activated.
 *
 * The ordering here is not stylistic — it is Razorpay's documented integration
 * requirement, and the reason each step is a separate call:
 *
 *   1. `createOrder`   → SERVER creates the gateway order (needs the secret key).
 *   2. `launchCheckout`→ CLIENT opens Checkout with that server-issued order.
 *   3. `verifyPayment` → SERVER verifies payment_id + order_id + signature.
 *   4. `activate`      → only now does membership become real.
 *
 * A client-side success callback proves the SDK finished. It does not prove
 * the payment is genuine, and it does not prove it has not already been
 * fulfilled. So [PaymentVerificationStatus.VERIFIED] is the only value in this
 * file that reaches [MembershipRepository.activate], and activation itself is
 * idempotent on the transaction id.
 */
object MembershipPurchase {

    suspend fun start(
        session: MembershipPurchaseSession,
        gateway: PaymentGateway,
        memberships: MembershipRepository,
        placedAtLabel: String
    ) {
        // Double-tap guard, first of three layers (UI disables the button,
        // this refuses re-entry, the receipt id makes the server idempotent).
        if (session.isBusy) return

        Analytics.track(
            AnalyticsEvents.MEMBERSHIP_PAYMENT_STARTED,
            mapOf("plan" to session.plan.id, "test_mode" to gateway.isTestMode.toString())
        )

        val order = try {
            session.phase = MembershipPurchaseSession.Phase.CreatingOrder
            gateway.createOrder(session.plan.price, session.receiptId)
        } catch (t: Throwable) {
            // Nothing was charged: Checkout never opened.
            session.phase = MembershipPurchaseSession.Phase.Failed(
                "We couldn't start the payment. No money has left your account.",
                retryable = true
            )
            Analytics.track(AnalyticsEvents.MEMBERSHIP_PAYMENT_FAILED, mapOf("stage" to "create_order"))
            return
        }

        val result = try {
            session.phase = MembershipPurchaseSession.Phase.CheckoutOpen(order)
            gateway.launchCheckout(order)
        } catch (t: Throwable) {
            // Checkout was open when this failed, so a payment MAY exist.
            // Pending, not Failed — the backend must reconcile it.
            session.phase = MembershipPurchaseSession.Phase.Pending(
                "We're checking your payment."
            )
            Analytics.track(AnalyticsEvents.MEMBERSHIP_PAYMENT_PENDING, mapOf("stage" to "checkout"))
            return
        }

        when (result) {
            is PaymentResult.Cancelled -> {
                session.phase = MembershipPurchaseSession.Phase.Cancelled
                Analytics.track(AnalyticsEvents.MEMBERSHIP_PAYMENT_CANCELLED)
            }

            is PaymentResult.Failed -> {
                session.phase = MembershipPurchaseSession.Phase.Failed(result.reason, result.retryable)
                Analytics.track(
                    AnalyticsEvents.MEMBERSHIP_PAYMENT_FAILED,
                    mapOf("stage" to "checkout", "retryable" to result.retryable.toString())
                )
            }

            is PaymentResult.Success -> {
                val status = try {
                    session.phase = MembershipPurchaseSession.Phase.Verifying
                    gateway.verifyPayment(result)
                } catch (t: Throwable) {
                    PaymentVerificationStatus.UNKNOWN
                }
                applyVerification(session, memberships, result, status, placedAtLabel)
            }
        }
    }

    /**
     * Turns a verification outcome into membership state.
     *
     * Split out so a later "reconcile a pending payment on app restart" path
     * can reuse exactly this logic rather than reimplementing the rule that
     * only VERIFIED activates.
     */
    suspend fun applyVerification(
        session: MembershipPurchaseSession,
        memberships: MembershipRepository,
        result: PaymentResult.Success,
        status: PaymentVerificationStatus,
        placedAtLabel: String
    ) {
        when (status) {
            PaymentVerificationStatus.VERIFIED -> {
                val transaction = MembershipTransaction(
                    // The gateway payment id IS the transaction identity, so a
                    // replayed callback for one payment activates once.
                    id = result.paymentId,
                    planId = session.plan.id,
                    amount = session.plan.price,
                    status = MembershipStatus.ACTIVE,
                    paymentReference = result.paymentId,
                    isTestPayment = result.isTestMode,
                    placedAtLabel = placedAtLabel
                )
                val state = memberships.activate(transaction)
                session.phase = MembershipPurchaseSession.Phase.Success(transaction, state)
                Analytics.track(
                    AnalyticsEvents.MEMBERSHIP_PAYMENT_SUCCESS,
                    mapOf("test_mode" to result.isTestMode.toString())
                )
                Analytics.track(AnalyticsEvents.MEMBERSHIP_ACTIVATED, mapOf("plan" to session.plan.id))
            }

            PaymentVerificationStatus.PENDING, PaymentVerificationStatus.UNKNOWN -> {
                // Money may have moved. Do NOT say it failed, and do NOT offer
                // a blind retry that could charge a second time.
                memberships.setStatus(MembershipStatus.PAYMENT_PENDING)
                session.phase = MembershipPurchaseSession.Phase.Pending(
                    "We're checking your payment. This usually takes a moment."
                )
                Analytics.track(AnalyticsEvents.MEMBERSHIP_PAYMENT_PENDING, mapOf("stage" to "verify"))
            }

            PaymentVerificationStatus.FAILED -> {
                // The server rejected the signature. Nothing is activated.
                memberships.setStatus(MembershipStatus.PAYMENT_FAILED)
                session.phase = MembershipPurchaseSession.Phase.Failed(
                    "We couldn't confirm this payment. Your membership has not been activated.",
                    retryable = true
                )
                Analytics.track(AnalyticsEvents.MEMBERSHIP_PAYMENT_FAILED, mapOf("stage" to "verify"))
            }
        }
    }
}
