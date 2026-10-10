package com.tazzzo.app.order

import com.tazzzo.app.checkout.bindingOf
import com.tazzzo.app.checkout.hx
import com.tazzzo.app.checkout.stampOf
import com.tazzzo.app.data.checkout.BenefitPreviewState
import com.tazzzo.app.data.checkout.CheckoutQuote
import com.tazzzo.app.data.checkout.CheckoutQuoteAccess
import com.tazzzo.app.data.checkout.CheckoutQuoteItem
import com.tazzzo.app.data.checkout.CheckoutSource
import com.tazzzo.app.data.checkout.CheckoutState
import com.tazzzo.app.data.checkout.StaleReason
import com.tazzzo.app.data.model.PayableMoney
import com.tazzzo.app.data.model.Money
import com.tazzzo.app.data.order.CustomerOrder
import com.tazzzo.app.data.order.CustomerOrderItem
import com.tazzzo.app.data.catalog.Page
import com.tazzzo.app.data.order.CustomerOrderStatus
import com.tazzzo.app.data.order.CustomerOrderSummary
import com.tazzzo.app.data.order.CustomerPaymentMethod
import com.tazzzo.app.data.order.OrderDeliveryAddress
import com.tazzzo.app.data.order.OrderPaymentCondition
import com.tazzzo.app.data.order.OrderSource
import com.tazzzo.app.data.order.PendingOrderStore
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/** A Ready quote with BINDING money by default; `money = null` is a legacy quote. */
fun readyQuote(quoteId: String = "CHKQ_abc123", money: PayableMoney? = bindingOf(Money.ofPaise(9_900))) = CheckoutQuote(
    quoteId, 5, "ADDR_abcdef1", listOf(CheckoutQuoteItem("TZP-1", 2, Money.ofPaise(4_950), Money.ofPaise(9_900))), 2, 1,
    Money.ofPaise(9_900), "INR", 0, 300_000, BenefitPreviewState.NotApplied, money, "req"
)

fun readyState(quoteId: String = "CHKQ_abc123", money: PayableMoney? = bindingOf(Money.ofPaise(9_900))) =
    CheckoutState.Ready(readyQuote(quoteId, money), CheckoutSource(5, stampOf()))

/** An order with AUTHORITATIVE money (no discount) by default; `money = null` is a legacy order. */
fun orderOf(
    id: String = "ORD_abc123", subtotal: Long = 9_900, condition: OrderPaymentCondition = OrderPaymentCondition.COD_DUE,
    money: PayableMoney? = bindingOf(Money.ofPaise(subtotal))
) = CustomerOrder(
    id, CustomerOrderStatus.CONFIRMED, CustomerPaymentMethod.COD, condition,
    listOf(CustomerOrderItem("TZP-1", "Atta 1kg", null, 2, Money.ofPaise(4_950), Money.ofPaise(9_900))), 2, Money.ofPaise(subtotal), "INR", money,
    OrderDeliveryAddress("HOME", "Asha Rao", "+919876543210", "22, 14th Main", null, null, "Bengaluru", "Karnataka", "560102"), 1L, 2L
)

/** The quote store as the order flow sees it. */
class FakeQuoteAccess(initial: CheckoutState = readyState()) : CheckoutQuoteAccess {
    val flow = MutableStateFlow(initial)
    var resets = 0
    var onReset: () -> Unit = {}
    /** Every invalidation asked for, in order (quote id to reason). */
    val invalidations = mutableListOf<Pair<String, StaleReason>>()
    override val state: StateFlow<CheckoutState> = flow
    override fun resetNow() { resets++; onReset(); flow.value = CheckoutState.Idle }
    override fun invalidateNow(quoteId: String, reason: StaleReason) {
        invalidations += quoteId to reason
        val cur = flow.value
        if (cur is CheckoutState.Ready && cur.quote.quoteId == quoteId) flow.value = CheckoutState.Stale(reason)
    }
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
    var onCall: () -> Unit = {}
    /** A definitive rejection for any quote that has no order yet (e.g. expired). */
    var rejectNew: Throwable? = null
    private var n = 0

    /** Fail the next NEW placement; [applied] = the order was nevertheless created (a lost response). */
    fun failNext(e: Throwable, applied: Boolean = false) { failures.addLast(e to applied) }
    fun ordersCreated() = orders.size

    /** The order's AUTHORITATIVE payable in paise (null = the default, equal to the ready quote's preview of ₹99). */
    var orderPaise: Long? = null

    private fun create(quoteId: String): CustomerOrder =
        (orderPaise?.let { orderOf("ORD_${++n}abcdef", subtotal = it) } ?: orderOf("ORD_${++n}abcdef")).also { orders[quoteId] = it }

    override suspend fun placeCodOrder(quoteId: String): CustomerOrder {
        calls += quoteId
        onCall()
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

    /** The history, newest first, as `listOrders` pages it ([pageSize] rows per page, cursor = the next index). */
    val history = mutableListOf<CustomerOrderSummary>()
    val listCalls = mutableListOf<String?>()
    var listError: Throwable? = null
    var listGate: CompletableDeferred<Unit>? = null
    var pageSize = 2

    override suspend fun listOrders(cursor: String?, pageSize: Int): Page<CustomerOrderSummary> {
        listCalls += cursor
        listGate?.await()
        listError?.let { throw it }
        val from = cursor?.toInt() ?: 0
        val rows = history.drop(from).take(this.pageSize)
        val next = (from + rows.size).takeIf { it < history.size }?.toString()
        return Page(rows, next, next != null)
    }
}

fun summaryOf(
    id: String = "ORD_abc123", status: CustomerOrderStatus = CustomerOrderStatus.CONFIRMED, payable: Long? = 9_900, items: Int = 2,
    createdAt: Long? = 1_790_931_600_000L
) = CustomerOrderSummary(id, status, CustomerPaymentMethod.COD, items, Money.ofPaise(payable ?: 9_900), payable?.let { Money.ofPaise(it) }, createdAt)
