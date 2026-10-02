package com.tazzzo.app.order

import com.tazzzo.app.checkout.hx
import com.tazzzo.app.checkout.stampOf
import com.tazzzo.app.data.checkout.BenefitPreviewState
import com.tazzzo.app.data.checkout.CheckoutQuote
import com.tazzzo.app.data.checkout.CheckoutQuoteAccess
import com.tazzzo.app.data.checkout.CheckoutQuoteItem
import com.tazzzo.app.data.checkout.CheckoutSource
import com.tazzzo.app.data.checkout.CheckoutState
import com.tazzzo.app.data.model.Money
import com.tazzzo.app.data.order.CustomerOrder
import com.tazzzo.app.data.order.CustomerOrderItem
import com.tazzzo.app.data.order.CustomerOrderStatus
import com.tazzzo.app.data.order.CustomerPaymentMethod
import com.tazzzo.app.data.order.OrderDeliveryAddress
import com.tazzzo.app.data.order.OrderPaymentCondition
import com.tazzzo.app.data.order.OrderSource
import com.tazzzo.app.data.order.PendingOrderStore
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

fun readyQuote(quoteId: String = "CHKQ_abc123") = CheckoutQuote(
    quoteId, 5, "ADDR_abcdef1", listOf(CheckoutQuoteItem("TZP-1", 2, Money.ofPaise(4_950), Money.ofPaise(9_900))), 2, 1,
    Money.ofPaise(9_900), "INR", 0, 300_000, BenefitPreviewState.NotApplied, "req"
)

fun readyState(quoteId: String = "CHKQ_abc123") = CheckoutState.Ready(readyQuote(quoteId), CheckoutSource(5, stampOf()))

fun orderOf(id: String = "ORD_abc123", subtotal: Long = 9_900, condition: OrderPaymentCondition = OrderPaymentCondition.COD_DUE) = CustomerOrder(
    id, CustomerOrderStatus.CONFIRMED, CustomerPaymentMethod.COD, condition,
    listOf(CustomerOrderItem("TZP-1", "Atta 1kg", null, 2, Money.ofPaise(4_950), Money.ofPaise(9_900))), 2, Money.ofPaise(subtotal), "INR",
    OrderDeliveryAddress("HOME", "Asha Rao", "+919876543210", "22, 14th Main", null, null, "Bengaluru", "Karnataka", "560102"), 1L, 2L
)

/** The quote store as the order flow sees it. */
class FakeQuoteAccess(initial: CheckoutState = readyState()) : CheckoutQuoteAccess {
    val flow = MutableStateFlow(initial)
    var resets = 0
    var onReset: () -> Unit = {}
    override val state: StateFlow<CheckoutState> = flow
    override fun resetNow() { resets++; onReset(); flow.value = CheckoutState.Idle }
}

class FakePending : PendingOrderStore {
    var value: String? = null
    val saves = mutableListOf<String>()
    var clears = 0
    override fun load() = value
    override fun save(quoteId: String) { saves += quoteId; value = quoteId }
    override fun clear() { clears++; value = null }
}

/**
 * A scripted backend with the REAL rules: structurally one order per quote, the replay lookup runs BEFORE everything else
 * (so a replay returns the order even after the quote expired), definitive rejections store nothing.
 */
class FakeOrderSource : OrderSource {
    val orders = HashMap<String, CustomerOrder>()
    val calls = mutableListOf<String>()
    val gets = mutableListOf<String>()
    private val failures = ArrayDeque<Pair<Throwable, Boolean>>()
    var gate: CompletableDeferred<Unit>? = null
    /** A definitive rejection for any quote that has no order yet (e.g. expired). */
    var rejectNew: Throwable? = null
    private var n = 0

    /** Fail the next NEW placement; [applied] = the order was nevertheless created (a lost response). */
    fun failNext(e: Throwable, applied: Boolean = false) { failures.addLast(e to applied) }
    fun ordersCreated() = orders.size

    private fun create(quoteId: String): CustomerOrder = orderOf("ORD_${++n}abcdef").also { orders[quoteId] = it }

    override suspend fun placeCodOrder(quoteId: String): CustomerOrder {
        calls += quoteId
        gate?.await()
        orders[quoteId]?.let { return it }                       // replay first: before expiry and every other check
        val f = failures.removeFirstOrNull()
        if (f != null) { if (f.second) create(quoteId); throw f.first }
        rejectNew?.let { throw it }
        return create(quoteId)
    }

    override suspend fun getOrder(orderId: String): CustomerOrder {
        gets += orderId
        return orders.values.firstOrNull { it.orderId == orderId } ?: throw hx(404, "NOT_FOUND")
    }
}
