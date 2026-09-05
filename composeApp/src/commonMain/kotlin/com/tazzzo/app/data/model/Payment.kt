package com.tazzzo.app.data.model

/*
 * Payment abstraction. Razorpay-shaped, Razorpay-free.
 *
 * Nothing in this file — or anywhere in the app — may hold a Razorpay secret
 * key or sign a payment. Razorpay's own integration requirement is:
 *   1. SERVER creates the order (needs the secret key).
 *   2. Client opens Razorpay Checkout with the server-issued order id.
 *   3. Razorpay returns payment_id + order_id + signature to the client.
 *   4. Client sends those three values to the SERVER.
 *   5. SERVER verifies the signature (needs the secret key again) and only
 *      then fulfils the purchase.
 * A client-side "success" callback is never sufficient on its own — it proves
 * Razorpay's SDK finished, not that the payment is genuine or that it has not
 * already been fulfilled once. This model exists so every one of those five
 * steps has a place to live when the backend does, and so no shortcut through
 * them is even representable in the types.
 */

/** Which gateway this order/result belongs to. Only one is planned. */
enum class PaymentProvider { RAZORPAY }

/**
 * An order created by the SERVER (step 1 above). [orderId] is the gateway's
 * identifier, never minted by the client. [receiptId] is Tazzzo's own
 * idempotency key for this purchase attempt — the same key is reused across
 * retries so a repeated attempt is a REPLAY of one purchase, not a new one.
 */
data class PaymentOrder(
    val orderId: String,
    val amountRupees: Int,
    val provider: PaymentProvider,
    val receiptId: String,
    /** True when this order was created by MockPaymentGateway, not a real gateway. */
    val isTestMode: Boolean
)

/** What Razorpay Checkout hands back to the client (step 3 above). */
sealed interface PaymentResult {
    data class Success(
        val paymentId: String,
        val orderId: String,
        val signature: String,
        val isTestMode: Boolean
    ) : PaymentResult

    data class Failed(val reason: String, val retryable: Boolean) : PaymentResult

    /** The customer dismissed Checkout without paying. Not a failure. */
    data object Cancelled : PaymentResult
}

/**
 * Outcome of SERVER-SIDE signature verification (step 5 above) — the only
 * thing that may authorise fulfilment. [UNKNOWN] is deliberately distinct
 * from [FAILED]: it means the verification call itself did not complete (a
 * dropped connection after payment), and the customer must be told their
 * payment is being checked, never that it failed, until this resolves.
 */
enum class PaymentVerificationStatus { VERIFIED, PENDING, FAILED, UNKNOWN }
