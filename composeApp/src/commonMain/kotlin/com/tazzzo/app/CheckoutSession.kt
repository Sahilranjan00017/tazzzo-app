package com.tazzzo.app

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.tazzzo.app.data.model.Address
import com.tazzzo.app.data.model.CartValidation
import com.tazzzo.app.data.model.DeliverySlot
import com.tazzzo.app.data.model.Money
import com.tazzzo.app.data.model.Order
import com.tazzzo.app.data.model.PaymentMethodKind
import kotlin.random.Random

/**
 * The checkout state machine.
 *
 * One session per checkout attempt. Holds step progression, the customer's
 * selections, revalidation results and placement status. Held in AppState so
 * process-internal navigation (back/forward) can never corrupt it; a new
 * session is created when checkout is entered fresh, and destroyed on success.
 *
 * The idempotency key is minted ONCE per session: every Place Order tap in
 * this session — including retries after failure — submits the same key, so
 * the backend can never create two orders for one intent.
 */
class CheckoutSession(
    /** Instructions already chosen in the cart, e.g. "no carry bag". Defaults
     *  to none so every existing caller — and every test — keeps compiling. */
    initialInstructionIds: Set<String> = emptySet(),
) {

    enum class Step(val title: String, val n: Int) {
        ADDRESS("Address", 1),
        SLOT("Delivery slot", 2),
        PAYMENT("Payment", 3),
        REVIEW("Review", 4)
    }

    val idempotencyKey: String =
        "chk-" + Random.nextLong(100_000_000_000L, 999_999_999_999L).toString()

    var step by mutableStateOf(Step.ADDRESS)
        private set

    var address by mutableStateOf<Address?>(null)
    var slot by mutableStateOf<DeliverySlot?>(null)
    var payment by mutableStateOf<PaymentMethodKind?>(null)

    /** Optional delivery instructions chosen at review. Ids from AppConfig.deliveryInstructions. */
    var instructionIds by mutableStateOf<Set<String>>(initialInstructionIds)

    /**
     * Tip for this order, in rupees. Zero means none chosen.
     *
     * Transient like the rest of the session: a tip is a decision about THIS
     * order, and silently carrying it into the next one would take money
     * nobody offered again.
     */
    var tip by mutableStateOf(Money.ZERO)
    fun toggleInstruction(id: String) {
        instructionIds = if (id in instructionIds) instructionIds - id else instructionIds + id
    }

    /** Last revalidation result; null = not yet validated in this session. */
    var validation by mutableStateOf<CartValidation?>(null)

    sealed interface Placement {
        data object Idle : Placement
        data object InFlight : Placement
        data class Failed(val reason: String, val retryable: Boolean) : Placement
        data class Done(val order: Order) : Placement
    }

    var placement by mutableStateOf<Placement>(Placement.Idle)

    // --- guarded transitions ------------------------------------------------

    /** True if the given step's requirements are met. */
    fun canAdvanceFrom(s: Step): Boolean = when (s) {
        Step.ADDRESS -> address?.isServiceable == true
        Step.SLOT -> slot?.available == true
        Step.PAYMENT -> payment != null
        Step.REVIEW -> false          // REVIEW exits via placeOrder, never next()
    }

    fun next() {
        if (!canAdvanceFrom(step)) return
        step = when (step) {
            Step.ADDRESS -> Step.SLOT
            Step.SLOT -> Step.PAYMENT
            Step.PAYMENT -> Step.REVIEW
            Step.REVIEW -> Step.REVIEW
        }
    }

    /** @return true if the back press was consumed inside checkout. */
    fun backStep(): Boolean {
        if (placement is Placement.InFlight) return true   // never navigate mid-flight
        val prev = when (step) {
            Step.ADDRESS -> return false                   // exit checkout, cart intact
            Step.SLOT -> Step.ADDRESS
            Step.PAYMENT -> Step.SLOT
            Step.REVIEW -> Step.PAYMENT
        }
        step = prev
        return true
    }

    /** Changing address invalidates the slot (serviceability differs per address). */
    fun selectAddress(a: Address) {
        if (address?.id != a.id) slot = null
        address = a
    }
}
