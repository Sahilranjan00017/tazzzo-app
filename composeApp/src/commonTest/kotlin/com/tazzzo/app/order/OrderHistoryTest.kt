package com.tazzzo.app.order

import com.tazzzo.app.address.authedApi
import com.tazzzo.app.auth.JSON_HEADERS
import com.tazzzo.app.checkout.FakeCartAccess
import com.tazzzo.app.checkout.hx
import com.tazzzo.app.data.catalog.AppendState
import com.tazzzo.app.data.catalog.CatalogFailure
import com.tazzzo.app.data.catalog.PagedState
import com.tazzzo.app.data.checkout.Iso8601
import com.tazzzo.app.data.order.CustomerOrderStatus
import com.tazzzo.app.data.order.CustomerOrderSummary
import com.tazzzo.app.data.order.OrderPaymentCondition
import com.tazzzo.app.data.order.OrderState
import com.tazzzo.app.data.order.OrderStatusCopy
import com.tazzzo.app.data.order.OrderStore
import com.tazzzo.app.data.order.OrderTime
import com.tazzzo.app.data.order.RemoteOrderDataSource
import com.tazzzo.app.data.order.view
import com.tazzzo.app.data.remote.ApiError
import com.tazzzo.app.data.remote.ApiException
import com.tazzzo.app.ui.order.OrdersSurface
import com.tazzzo.app.ui.order.amountHeadline
import com.tazzzo.app.ui.order.ordersSurface
import io.ktor.client.engine.mock.respond
import io.ktor.client.request.HttpRequestData
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpMethod
import io.ktor.http.HttpStatusCode
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * The REAL order history (`GET /v1/customer/orders`) and the lifecycle statuses of `GET /v1/customer/orders/{id}`, against the
 * running backend's `CustomerOrderDto.Page` / `Summary` / `CustomerOrderDto` shapes.
 *
 * Mutation notes: sending any query parameter but page_size/cursor fails [theListSendsOnlyPageSizeAndCursorWithTheBearer];
 * mapping an absent payablePaise to ₹0 fails [aLegacySummaryHasNoPayableAndSaysSoNeverZero]; not resetting the pager on sign-out
 * fails [signOutForgetsTheRowsAndAStaleResponseIsDiscarded]; keeping history after a placement fails
 * [aSuccessfulPlacementMakesTheNextOpenReloadPageOne]; "due" copy on a cancelled order fails
 * [aCancelledOrderOwesNothingAndNeverSaysAmountDue]; a UTC (not IST) clock fails [orderTimesAreIndiaStandardTime].
 */
class OrderHistoryTest {

    // ---- data source: GET /v1/customer/orders ---------------------------------------------------------------------------

    private val pageJson = """{"items":[
        {"orderId":"ORD_new0001","status":"CONFIRMED","paymentMethod":"COD","itemCount":3,"subtotalPaise":15000,"payablePaise":14000,"createdAt":"2026-10-02T09:00:00.123456Z"},
        {"orderId":"ORD_old0001","status":"CANCELLED","paymentMethod":"COD","itemCount":1,"subtotalPaise":5000,"payablePaise":5000,"createdAt":"2026-10-01T09:00:00Z","cancelledAt":"2026-10-01T09:10:00Z",
         "deliverySlot":{"slotId":"blr-am~2026-10-02","label":"Thu 2 Oct, 7–9 am","startsAt":"2026-10-02T01:30:00Z","endsAt":"2026-10-02T03:30:00Z"}},
        {"orderId":"ORD_legacy01","status":"DELIVERED","paymentMethod":"COD","itemCount":2,"subtotalPaise":9900,"createdAt":"2026-09-01T09:00:00Z"}
        ],"nextCursor":"djF8MTc5MDkzMTYwMDAwMHxPUkRfbGVnYWN5MDE","requestId":"req_l"}"""

    private fun ds(seen: MutableList<HttpRequestData> = mutableListOf(), body: String = pageJson, status: HttpStatusCode = HttpStatusCode.OK) =
        RemoteOrderDataSource(authedApi { req -> seen += req; respond(body, status, JSON_HEADERS) }) to seen

    @Test fun theListSendsOnlyPageSizeAndCursorWithTheBearer() = runTest {
        val (s, seen) = ds()
        s.listOrders(null)
        s.listOrders("djF8abc", pageSize = 50)
        val first = seen[0]; val second = seen[1]
        assertEquals(HttpMethod.Get, first.method); assertEquals("/v1/customer/orders", first.url.encodedPath)
        assertEquals("Bearer acc1", first.headers[HttpHeaders.Authorization])
        assertEquals(setOf("page_size"), first.url.parameters.names()); assertEquals("20", first.url.parameters["page_size"])
        assertEquals(setOf("page_size", "cursor"), second.url.parameters.names())
        assertEquals("djF8abc", second.url.parameters["cursor"]); assertEquals("50", second.url.parameters["page_size"])
    }

    @Test fun anOutOfContractPageSizeOrCursorIsNeverSent() = runTest {
        val (s, seen) = ds()
        for (bad in listOf(0, 51, -1)) assertFailsWith<IllegalArgumentException> { s.listOrders(null, bad) }
        assertFailsWith<IllegalArgumentException> { s.listOrders("") }
        assertFailsWith<IllegalArgumentException> { s.listOrders("x".repeat(129)) }
        assertTrue(seen.isEmpty())
    }

    @Test fun thePageMapsExactlyAsSent() = runTest {
        val (s, _) = ds()
        val p = s.listOrders(null)
        assertEquals(listOf("ORD_new0001", "ORD_old0001", "ORD_legacy01"), p.items.map { it.orderId })
        assertTrue(p.hasMore); assertEquals("djF8MTc5MDkzMTYwMDAwMHxPUkRfbGVnYWN5MDE", p.nextCursor)
        val a = p.items[0]
        assertEquals(CustomerOrderStatus.CONFIRMED, a.status); assertEquals(3, a.itemCount); assertEquals(14_000L, a.payable!!.paise)
        assertEquals(15_000L, a.subtotal.paise); assertEquals(1_790_931_600_123L, a.createdAtMillis)
        val b = p.items[1]
        assertEquals(CustomerOrderStatus.CANCELLED, b.status); assertEquals("Thu 2 Oct, 7–9 am", b.deliverySlotLabel)
        assertEquals(CustomerOrderStatus.DELIVERED, p.items[2].status)
    }

    @Test fun aLegacySummaryHasNoPayableAndSaysSoNeverZero() = runTest {
        val legacy = ds().first.listOrders(null).items[2]
        assertNull(legacy.payable)
        val v = legacy.view()
        assertNull(v.amount); assertEquals("Amount details unavailable", v.caption)
    }

    @Test fun theLastPageHasNoCursorAndAnOversizedCursorEndsTheList() = runTest {
        val last = ds(body = """{"items":[],"nextCursor":null,"requestId":"r"}""").first.listOrders(null)
        assertFalse(last.hasMore); assertNull(last.nextCursor); assertTrue(last.items.isEmpty())
        val huge = ds(body = """{"items":[],"nextCursor":"${"y".repeat(200)}","requestId":"r"}""").first.listOrders(null)
        assertFalse(huge.hasMore)
    }

    @Test fun aMalformedRowIdIsAContractFailureNotARow() = runTest {
        val bad = ds(body = """{"items":[{"orderId":"../x","status":"CONFIRMED","paymentMethod":"COD","itemCount":1,"subtotalPaise":100}],"nextCursor":null}""").first
        val e = assertFailsWith<ApiException> { bad.listOrders(null) }
        assertIs<ApiError.Decoding>(e.error)
    }

    @Test fun lifecycleStatusesAndTimestampsMapFromTheDetail() = runTest {
        val body = """{"orderId":"ORD_abc123","status":"CANCELLED","paymentMethod":"COD",
            "items":[{"skuId":"TZP-1","title":"Atta 1kg","brandCode":null,"quantity":1,"unitPricePaise":9900,"lineTotalPaise":9900}],
            "itemCount":1,"subtotalPaise":9900,"currency":"INR","createdAt":"2026-10-02T09:00:00Z","confirmedAt":"2026-10-02T09:00:01Z",
            "money":{"merchandiseSubtotalPaise":9900,"benefitDiscountPaise":0,"payablePaise":9900},"cancelledAt":"2026-10-02T09:20:00Z","requestId":"r"}"""
        val o = ds(body = body).first.getOrder("ORD_abc123")
        assertEquals(CustomerOrderStatus.CANCELLED, o.status)
        assertEquals(OrderPaymentCondition.UNRECOGNIZED, o.paymentCondition)      // a cancelled order carries no payment condition
        assertEquals(Iso8601.parseMillis("2026-10-02T09:20:00Z"), o.cancelledAtMillis)
        for (raw in listOf("OUT_FOR_DELIVERY" to CustomerOrderStatus.OUT_FOR_DELIVERY, "DELIVERED" to CustomerOrderStatus.DELIVERED, "CREATED" to CustomerOrderStatus.UNRECOGNIZED))
            assertEquals(raw.second, CustomerOrderStatus.of(raw.first))
    }

    // ---- store ------------------------------------------------------------------------------------------------------------

    private class Rig(scope: TestScope, var authed: Boolean = true) {
        val server = FakeOrderSource()
        val store = OrderStore(scope.backgroundScope, server, FakeQuoteAccess(), FakeCartAccess(), FakePending(), { authed }, { true }, {})
        fun history() = store.history.value
    }

    private fun rows(n: Int) = (1..n).map { summaryOf(id = "ORD_row${it}abcd") }

    @Test fun signedOutOpensNothingAndFetchesNothing() = runTest {
        val r = Rig(this, authed = false); r.server.history += rows(3)
        r.store.openHistory(); runCurrent()
        assertEquals(PagedState.Idle, r.history()); assertTrue(r.server.listCalls.isEmpty())
        assertEquals(OrdersSurface.SignedOut, ordersSurface(false, r.history()))
    }

    @Test fun openLoadsPageOneOnceAndLoadMoreAppendsByCursor() = runTest {
        val r = Rig(this); r.server.history += rows(3)
        r.store.openHistory(); runCurrent()
        val first = assertIs<PagedState.Content<CustomerOrderSummary>>(r.history())
        assertEquals(2, first.items.size); assertTrue(first.hasMore)
        r.store.openHistory(); runCurrent()                                  // already loaded: no refetch
        assertEquals(listOf<String?>(null), r.server.listCalls)
        r.store.loadMoreHistory(); runCurrent()
        val all = assertIs<PagedState.Content<CustomerOrderSummary>>(r.history())
        assertEquals(rows(3).map { it.orderId }, all.items.map { it.orderId }); assertFalse(all.hasMore)
        assertEquals(listOf(null, "2"), r.server.listCalls)
    }

    @Test fun anEmptyHistoryIsNoOrdersYet() = runTest {
        val r = Rig(this)
        r.store.openHistory(); runCurrent()
        assertEquals(PagedState.Empty, r.history())
        assertEquals(OrdersSurface.NoOrdersYet, ordersSurface(true, r.history()))
    }

    @Test fun aFirstPageFailureIsShownAndRetryReloads() = runTest {
        val r = Rig(this); r.server.history += rows(1); r.server.listError = hx(503, "SERVICE_UNAVAILABLE")
        r.store.openHistory(); runCurrent()
        assertEquals(PagedState.FirstPageFailed(CatalogFailure.Unavailable), r.history())
        assertIs<OrdersSurface.Failed>(ordersSurface(true, r.history()))
        r.server.listError = null
        r.store.refreshHistory(); runCurrent()
        assertIs<PagedState.Content<CustomerOrderSummary>>(r.history())
    }

    @Test fun anAppendFailureKeepsTheRowsAndLoadMoreRetries() = runTest {
        val r = Rig(this); r.server.history += rows(3)
        r.store.openHistory(); runCurrent()
        r.server.listError = ApiException(ApiError.Network)
        r.store.loadMoreHistory(); runCurrent()
        val failed = assertIs<PagedState.Content<CustomerOrderSummary>>(r.history())
        assertEquals(2, failed.items.size); assertIs<AppendState.Failed>(failed.append)
        r.server.listError = null
        r.store.loadMoreHistory(); runCurrent()
        assertEquals(3, assertIs<PagedState.Content<CustomerOrderSummary>>(r.history()).items.size)
    }

    @Test fun signOutForgetsTheRowsAndAStaleResponseIsDiscarded() = runTest {
        val r = Rig(this); r.server.history += rows(2)
        r.store.openHistory(); runCurrent()
        assertIs<PagedState.Content<CustomerOrderSummary>>(r.history())
        r.store.signOut(); runCurrent()
        assertEquals(PagedState.Idle, r.history())
        // A response that lands after the session ended never repopulates the list.
        r.authed = true
        val gate = CompletableDeferred<Unit>(); r.server.listGate = gate
        r.store.openHistory(); runCurrent()
        r.store.signOut(); runCurrent()
        gate.complete(Unit); runCurrent()
        assertEquals(PagedState.Idle, r.history())
    }

    @Test fun aNewInteractiveSignInNeverShowsThePreviousCustomersRows() = runTest {
        val r = Rig(this); r.server.history += rows(2)
        r.store.openHistory(); runCurrent()
        r.store.onInteractiveSignIn(); runCurrent()
        assertEquals(PagedState.Idle, r.history())
    }

    @Test fun aSuccessfulPlacementMakesTheNextOpenReloadPageOne() = runTest {
        val r = Rig(this); r.server.history += rows(1)
        r.store.openHistory(); runCurrent()
        r.store.place(); runCurrent()
        assertIs<OrderState.Placed>(r.store.state.value)
        assertEquals(PagedState.Idle, r.history())                            // the Orders surface reloads when shown
        r.store.openHistory(); runCurrent()
        assertEquals(listOf<String?>(null, null), r.server.listCalls)
    }

    // ---- presentation -----------------------------------------------------------------------------------------------------

    @Test fun aCancelledOrderOwesNothingAndNeverSaysAmountDue() {
        val o = orderOf().copy(status = CustomerOrderStatus.CANCELLED, paymentCondition = OrderPaymentCondition.UNRECOGNIZED, cancelledAtMillis = 3L)
        val v = o.view()
        assertEquals("Order cancelled", v.title); assertEquals(OrderStatusCopy.CANCELLED_NOTHING_DUE, v.dueLine)
        assertFalse(v.moneyLines.any { it.label == "Amount due" }); assertEquals(OrderStatusCopy.ORDER_TOTAL, v.headlineMoney.label)
        assertFalse("due on delivery" in o.amountHeadline().caption)
        assertEquals("Cancelled", summaryOf(status = CustomerOrderStatus.CANCELLED).view().caption)
    }

    @Test fun aDeliveredCodOrderIsNeverCalledPaid() {
        val o = orderOf().copy(status = CustomerOrderStatus.DELIVERED, deliveredAtMillis = 5L)
        val text = (o.view().toString() + o.amountHeadline() + summaryOf(status = CustomerOrderStatus.DELIVERED).view()).lowercase()
        assertFalse("paid" in text); assertFalse("due on delivery" in o.amountHeadline().caption)
        assertEquals("Delivered", o.view().title)
    }

    @Test fun anOpenCodOrderStillReadsDueOnDelivery() {
        assertEquals("₹99 due on delivery", orderOf().view().dueLine)
        assertEquals("due on delivery", summaryOf().view().caption); assertEquals("₹99", summaryOf().view().amount)
        assertEquals("Nothing due on delivery", summaryOf(payable = 0).view().caption)
        assertEquals("Out for delivery", orderOf().copy(status = CustomerOrderStatus.OUT_FOR_DELIVERY).view().title)
    }

    @Test fun theTimelineHasOnlyRecordedStepsOldestFirst() {
        val o = orderOf().copy(createdAtMillis = 1_000L, confirmedAtMillis = 2_000L, outForDeliveryAtMillis = 3_000L, deliveredAtMillis = 4_000L)
        assertEquals(listOf("Order placed", "Out for delivery", "Delivered"), o.view().timeline.map { it.label })
        assertEquals(listOf("Order placed"), orderOf().view().timeline.map { it.label })
    }

    @Test fun orderTimesAreIndiaStandardTime() {
        assertEquals("2 Oct 2026, 2:30 pm", OrderTime.label(Iso8601.parseMillis("2026-10-02T09:00:00Z")!!))
        assertEquals("2 Oct 2026, 12:00 am", OrderTime.label(Iso8601.parseMillis("2026-10-01T18:30:00Z")!!))
        assertEquals("29 Feb 2028, 12:00 pm", OrderTime.label(Iso8601.parseMillis("2028-02-29T06:30:00Z")!!))
        assertEquals("1 Jan 2027, 5:29 am", OrderTime.label(Iso8601.parseMillis("2026-12-31T23:59:00Z")!!))
        assertEquals("Placed 2 Oct 2026, 2:30 pm", summaryOf(createdAt = Iso8601.parseMillis("2026-10-02T09:00:00Z")).view().placedLabel)
    }
}
