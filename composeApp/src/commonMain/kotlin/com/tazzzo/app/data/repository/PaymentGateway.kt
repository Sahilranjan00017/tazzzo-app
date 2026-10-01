package com.tazzzo.app.data.repository

import com.tazzzo.app.data.model.Money

import com.tazzzo.app.data.model.PaymentOrder
import com.tazzzo.app.data.model.PaymentProvider
import com.tazzzo.app.data.model.PaymentResult
import com.tazzzo.app.data.model.PaymentVerificationStatus
import kotlinx.coroutines.delay
import kotlin.random.Random

/**
 * The seam a real Razorpay integration lands behind.
 *
 * [createOrder] and [verifyPayment] are SERVER calls in a real implementation
 * — the client only ever asks its own backend to do them, never talks to
 * Razorpay directly for either. [launchCheckout] is the one client-side step:
 * opening the Razorpay Checkout SDK with the order [createOrder] returned.
 *
 * Today only [MockPaymentGateway] exists — connecting Razorpay for real means
 * adding a `RemotePaymentGateway` that calls the backend's
 * `POST /payments/v1/orders` and `POST /payments/v1/verify` (contract to be
 * written in docs/BACKEND_INTEGRATION_READINESS.md when that backend exists)
 * and swapping it in at [ServiceLocator], the same pattern every other
 * repository in this file already follows. No screen calls a gateway
 * implementation directly.
 */
interface PaymentGateway {
    val provider: PaymentProvider
    val isTestMode: Boolean

    /** Server-side in production. Never mints an order id on the client for real money. */
    suspend fun createOrder(amount: Money, receiptId: String): PaymentOrder

    /** The one genuinely client-side step: hand the order to the gateway's checkout UI. */
    suspend fun launchCheckout(order: PaymentOrder): PaymentResult

    /** Server-side signature verification in production. A client "success" alone never authorises fulfilment. */
    suspend fun verifyPayment(result: PaymentResult.Success): PaymentVerificationStatus
}

/**
 * Development/demo gateway. Simulates every step with a small delay so the
 * UI states (creating order, checkout open, verifying) are genuinely
 * exercised, and NEVER silently returns success — a scenario must be chosen.
 *
 * [isTestMode] is `true` on every [PaymentOrder] and [PaymentResult] this
 * produces, and the UI is required to show that distinction (§32: "Clearly
 * distinguish TEST PAYMENT from LIVE PAYMENT"). This class must never be
 * reachable from a release build once a real gateway exists — that is
 * `AppEnvironment.allowsDevTooling`'s job, the same gate DemoTour uses.
 */
class MockPaymentGateway(
    /** Deterministic outcome for tests; random (weighted toward success) otherwise. */
    private val forcedOutcome: MockOutcome? = null
) : PaymentGateway {

    enum class MockOutcome { SUCCEED, FAIL, CANCEL, HANG_PENDING }

    override val provider: PaymentProvider = PaymentProvider.RAZORPAY
    override val isTestMode: Boolean = true

    override suspend fun createOrder(amount: Money, receiptId: String): PaymentOrder {
        delay(500)
        return PaymentOrder(
            orderId = "order_test_$receiptId",
            amount = amount,
            provider = provider,
            receiptId = receiptId,
            isTestMode = true
        )
    }

    override suspend fun launchCheckout(order: PaymentOrder): PaymentResult {
        delay(900)
        return when (forcedOutcome ?: MockOutcome.SUCCEED) {
            MockOutcome.SUCCEED -> PaymentResult.Success(
                paymentId = "pay_test_${order.receiptId}",
                orderId = order.orderId,
                signature = "test_signature_${order.receiptId}",
                isTestMode = true
            )
            MockOutcome.FAIL -> PaymentResult.Failed(
                "The test gateway declined this payment.", retryable = true
            )
            MockOutcome.CANCEL -> PaymentResult.Cancelled
            MockOutcome.HANG_PENDING -> PaymentResult.Failed(
                "Connection lost before the test gateway responded.", retryable = false
            )
        }
    }

    override suspend fun verifyPayment(result: PaymentResult.Success): PaymentVerificationStatus {
        delay(500)
        // A real server checks the signature against Razorpay's secret. The
        // mock checks the shape it generated itself, so a fabricated
        // signature from anywhere else in the app still fails verification —
        // this class is not a way to skip the check, only to simulate it.
        val expected = "test_signature_" + result.paymentId.removePrefix("pay_test_")
        return if (result.signature == expected) PaymentVerificationStatus.VERIFIED
        else PaymentVerificationStatus.FAILED
    }

    companion object {
        /** One receipt id per purchase attempt; reused across retries by the caller. */
        fun newReceiptId(): String = "club-" + Random.nextLong(100_000_000_000L, 999_999_999_999L)
    }
}
