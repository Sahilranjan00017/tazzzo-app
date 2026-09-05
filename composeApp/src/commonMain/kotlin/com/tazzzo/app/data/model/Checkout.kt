package com.tazzzo.app.data.model

import kotlinx.serialization.Serializable

/**
 * Checkout domain model.
 *
 * Design rule: the checkout cannot silently do the wrong thing. Every risky
 * transition (stale stock, changed price, duplicate submission, unserviceable
 * address) is represented as data the UI must handle, not as an assumption.
 */

@Serializable
data class Address(
    val id: String,
    val label: String,            // "Home", "Work"
    val line1: String,
    val line2: String,
    val pincode: String,
    val isServiceable: Boolean    // resolved by serviceability, not guessed
) {
    /** Minimal client-side validation; the server revalidates. */
    companion object {
        fun validate(label: String, line1: String, pincode: String): String? = when {
            label.isBlank() -> "Give this address a name (Home, Work…)"
            line1.trim().length < 8 -> "Enter the full address"
            pincode.length != 6 || pincode.any { !it.isDigit() } -> "Enter a valid 6-digit pincode"
            else -> null
        }
    }
}

@Serializable
data class DeliverySlot(
    val id: String,
    val label: String,            // "Today, 6–8 PM"
    val available: Boolean,
    val etaMinutes: Int? = null,  // only when backed by serviceability data
    /** Fee for THIS slot. 0 = free. Overrides the flat rule when a slot is chosen. */
    val feeRupees: Int = 0,
    /** Customer-facing reason for the fee, e.g. "Peak-hour slot". Null when free or unexplained. */
    val feeReason: String? = null,
    /** Section header: "Next available", "Today", "Tomorrow". Null = ungrouped. */
    val group: String? = null,
    /** Server-recommended (soonest reliable). Rendered as a small tag, never auto-selected silently. */
    val recommended: Boolean = false
)

/**
 * Optional delivery instructions the customer can attach. The set of options
 * is configuration (`AppConfig.deliveryInstructions`) so a market can add
 * "Leave with security" or remove "Ring the bell" without a code change.
 */
@Serializable
data class DeliveryInstruction(val id: String, val label: String)

@Serializable
enum class PaymentMethodKind { COD, UPI, CARD }

@Serializable
data class PaymentMethod(
    val kind: PaymentMethodKind,
    val label: String,
    val enabled: Boolean,
    val note: String? = null      // e.g. "Coming soon"
)

/** Result of revalidating the cart against current stock and prices. */
@Serializable
data class CartValidation(
    val issues: List<CartIssue>
) {
    val ok: Boolean get() = issues.isEmpty()
}

@Serializable(with = CartIssueSerializer::class)
sealed interface CartIssue {
    val productId: String
    val productName: String

    data class OutOfStock(
        override val productId: String,
        override val productName: String
    ) : CartIssue

    data class QuantityReduced(
        override val productId: String,
        override val productName: String,
        val requested: Int,
        val available: Int
    ) : CartIssue

    data class PriceChanged(
        override val productId: String,
        override val productName: String,
        val oldPrice: Int,
        val newPrice: Int
    ) : CartIssue
}

/** What the customer asked us to do, exactly once. */
@Serializable
data class OrderRequest(
    val idempotencyKey: String,
    val lines: List<CartLine>,
    val bill: BillSummary,
    val addressId: String,
    val addressText: String,
    val slotId: String,
    val payment: PaymentMethodKind,
    /** The chosen slot itself, so the order can echo it without a lookup. */
    val slot: DeliverySlot? = null,
    val instructionIds: List<String> = emptyList()
)

/**
 * Deliberately NOT @Serializable. This is the client's interpretation of a
 * placement attempt, not a wire type: `Placed` is built from the order payload,
 * `Rejected` from a validation payload and `Failed` from a transport or 5xx
 * outcome that has no body at all. The response envelope that maps onto it is
 * the repository's job (docs/BACKEND_INTEGRATION_READINESS.md §7); inventing an
 * envelope here would be inventing a contract.
 */
sealed interface PlaceOrderResult {
    data class Placed(val order: Order, val replayed: Boolean) : PlaceOrderResult
    data class Rejected(val validation: CartValidation) : PlaceOrderResult
    data class Failed(val reason: String, val retryable: Boolean) : PlaceOrderResult
}
