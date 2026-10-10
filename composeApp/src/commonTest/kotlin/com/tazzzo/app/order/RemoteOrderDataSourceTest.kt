package com.tazzzo.app.order

import com.tazzzo.app.address.authedApi
import com.tazzzo.app.auth.JSON_HEADERS
import com.tazzzo.app.auth.bodyText
import com.tazzzo.app.auth.errorJson
import com.tazzzo.app.data.order.CustomerOrderStatus
import com.tazzzo.app.data.order.CustomerPaymentMethod
import com.tazzzo.app.data.order.OrderFailure
import com.tazzzo.app.data.order.OrderPaymentCondition
import com.tazzzo.app.data.order.RemoteOrderDataSource
import com.tazzzo.app.data.order.toOrderFailure
import com.tazzzo.app.data.remote.ApiException
import io.ktor.client.engine.mock.respond
import io.ktor.client.request.HttpRequestData
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpMethod
import io.ktor.http.HttpStatusCode
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class RemoteOrderDataSourceTest {
    private fun orderJson(
        unit: Long = 4_950, qty: Int = 2, subtotal: Long = 9_900, status: String = "CONFIRMED", method: String = "COD", condition: String = "COD_DUE",
        address: String = ""","deliveryAddress":{"label":"HOME","recipientName":"Asha Rao","recipientPhone":"+919876543210","addressLine1":"22, 14th Main","city":"Bengaluru","state":"Karnataka","postalCode":"560102"}"""
    ) = """{"orderId":"ORD_abc123","status":"$status","paymentMethod":"$method","paymentCondition":"$condition",
        "items":[{"skuId":"TZP-1","title":"Atta 1kg","brandCode":null,"quantity":$qty,"unitPricePaise":$unit,"lineTotalPaise":${unit * qty}}],
        "itemCount":$qty,"subtotalPaise":$subtotal,"currency":"INR"$address,"createdAt":"2026-10-02T09:00:00.000Z","confirmedAt":"2026-10-02T09:00:01.000Z","requestId":"req_o"}"""

    private fun ds(seen: MutableList<HttpRequestData> = mutableListOf(), body: String = orderJson(), status: HttpStatusCode = HttpStatusCode.OK) =
        RemoteOrderDataSource(authedApi { req -> seen += req; respond(body, status, JSON_HEADERS) }) to seen

    // ---- request shape -------------------------------------------------------------------------------------------------

    @Test fun postSendsExactlyTheQuoteIdAndCodWithOnlyTheBearer() = runTest {
        val (s, seen) = ds(); s.placeCodOrder("CHKQ_abc123")
        val r = seen.single()
        assertEquals(HttpMethod.Post, r.method); assertEquals("/v1/customer/orders", r.url.encodedPath)
        assertEquals("Bearer acc1", r.headers[HttpHeaders.Authorization])
        assertEquals("""{"quoteId":"CHKQ_abc123","paymentMethod":"COD"}""", r.bodyText().replace(" ", ""))
        assertNull(r.headers["Idempotency-Key"]); assertNull(r.headers[HttpHeaders.IfMatch]); assertTrue(r.url.parameters.isEmpty())
    }

    @Test fun theBodyCarriesNoAddressCartVersionTotalSlotCouponCoinsOrPaymentChoice() = runTest {
        val (s, seen) = ds(); s.placeCodOrder("CHKQ_abc123")
        val body = seen.single().bodyText().lowercase()
        for (f in listOf("address", "cart", "total", "price", "subtotal", "payable", "slot", "coupon", "coin", "member", "pin", "lat", "lng", "fulfillment")) assertFalse(f in body, f)
    }

    @Test fun getReadsOneOrderByIdWithoutAnyExtraHeaders() = runTest {
        val (s, seen) = ds(); s.getOrder("ORD_abc123")
        val r = seen.single()
        assertEquals(HttpMethod.Get, r.method); assertEquals("/v1/customer/orders/ORD_abc123", r.url.encodedPath)
        assertNull(r.headers["Idempotency-Key"]); assertNull(r.headers[HttpHeaders.IfMatch]); assertEquals("", r.bodyText())
    }

    @Test fun malformedIdsAreNeverSent() = runTest {
        val (s, seen) = ds()
        for (bad in listOf("", "TZP-1", "CHKQ_", "../x", "CHKQ_abc 123", "chkq_abc123")) assertFailsWith<IllegalArgumentException>(bad) { s.placeCodOrder(bad) }
        for (bad in listOf("", "CHKQ_abc123", "ORD_", "../x", "ORD_abc/123")) assertFailsWith<IllegalArgumentException>(bad) { s.getOrder(bad) }
        assertTrue(seen.isEmpty())
    }

    // ---- mapping -------------------------------------------------------------------------------------------------------

    @Test fun theOrderMapsExactlyAsSentWithMoneyInPaise() = runTest {
        val (s, _) = ds(); val o = s.placeCodOrder("CHKQ_abc123")
        assertEquals("ORD_abc123", o.orderId); assertEquals(CustomerOrderStatus.CONFIRMED, o.status)
        assertEquals(CustomerPaymentMethod.COD, o.paymentMethod); assertEquals(OrderPaymentCondition.COD_DUE, o.paymentCondition)
        val i = o.items.single()
        assertEquals(4_950L, i.unitPrice.paise); assertEquals("₹49.50", i.unitPrice.format()); assertEquals(9_900L, i.lineTotal.paise)
        assertEquals(9_900L, o.subtotal.paise); assertEquals("INR", o.currency); assertEquals(2, o.itemCount)
        assertEquals("Asha Rao", o.deliveryAddress!!.recipientName); assertEquals("560102", o.deliveryAddress!!.postalCode)
        assertEquals(1_790_931_600_000L, o.createdAtMillis)
    }

    @Test fun unknownStatusMethodAndPaymentConditionFailClosedToUnrecognized() = runTest {
        val (s, _) = ds(body = orderJson(status = "SHIPPED", method = "UPI", condition = "PAID"))
        val o = s.placeCodOrder("CHKQ_abc123")
        assertEquals(CustomerOrderStatus.UNRECOGNIZED, o.status); assertEquals(CustomerPaymentMethod.UNRECOGNIZED, o.paymentMethod)
        assertEquals(OrderPaymentCondition.UNRECOGNIZED, o.paymentCondition)
    }

    @Test fun anOrderWithoutAnAddressSnapshotStillMaps() = runTest {
        val (s, _) = ds(body = orderJson(address = "")); assertNull(s.placeCodOrder("CHKQ_abc123").deliveryAddress)
    }

    @Test fun negativeMoneyZeroQuantityAMalformedIdOrGarbageIsAContractViolation() = runTest {
        for (bad in listOf(orderJson(unit = -1), orderJson(subtotal = -5), orderJson(qty = 0), orderJson().replace("ORD_abc123", "nope"), "{}", "garbage")) {
            val (s, _) = ds(body = bad)
            assertFailsWith<ApiException> { s.placeCodOrder("CHKQ_abc123") }
        }
    }

    @Test fun theOrderHasNoPayableFeeTaxOrDiscountToMap() = runTest {
        val (s, _) = ds(); val o = s.placeCodOrder("CHKQ_abc123")
        assertEquals("CustomerOrder(1 lines)", o.toString())
        assertEquals(9_900L, o.subtotal.paise)                              // the ONLY aggregate; nothing is added to it
    }

    // ---- errors --------------------------------------------------------------------------------------------------------

    private suspend fun failureFor(status: Int, code: String): OrderFailure {
        val api = authedApi { respond(errorJson(code), HttpStatusCode.fromValue(status), JSON_HEADERS) }
        return runCatching { RemoteOrderDataSource(api).placeCodOrder("CHKQ_abc123") }.exceptionOrNull()!!.toOrderFailure()
    }

    @Test fun everyDocumentedBackendCodeMapsToATypedFailure() = runTest {
        assertEquals(OrderFailure.QuoteExpired, failureFor(410, "QUOTE_EXPIRED"))
        assertEquals(OrderFailure.NotFound, failureFor(404, "NOT_FOUND"))
        assertEquals(OrderFailure.AddressChanged, failureFor(409, "ADDRESS_CHANGED"))
        assertEquals(OrderFailure.NotServiceable, failureFor(409, "NOT_SERVICEABLE"))
        assertEquals(OrderFailure.PriceChanged, failureFor(409, "PRICE_CHANGED"))
        assertEquals(OrderFailure.ProductUnavailable, failureFor(409, "PRODUCT_UNAVAILABLE"))
        assertEquals(OrderFailure.StockUnavailable, failureFor(409, "STOCK_UNAVAILABLE"))
        assertEquals(OrderFailure.ReservationExpired, failureFor(409, "RESERVATION_EXPIRED"))
        assertEquals(OrderFailure.CartAlreadyPurchased, failureFor(409, "CART_VERSION_ALREADY_PURCHASED"))
        assertEquals(OrderFailure.ClientBug, failureFor(400, "PAYMENT_METHOD_UNSUPPORTED"))
        assertEquals(OrderFailure.ClientBug, failureFor(400, "INVALID_REQUEST"))
        assertEquals(OrderFailure.ClientBug, failureFor(415, "UNSUPPORTED_MEDIA_TYPE"))
        assertEquals(OrderFailure.Unauthenticated, failureFor(401, "UNAUTHENTICATED"))
        assertEquals(OrderFailure.Unavailable, failureFor(503, "SERVICE_UNAVAILABLE"))
        assertEquals(OrderFailure.Server, failureFor(500, "INTERNAL"))
        assertEquals(OrderFailure.Unknown, failureFor(409, "NEW_CODE"))
        assertEquals(OrderFailure.Unknown, failureFor(429, "RATE_LIMITED"))
    }

    @Test fun theOrderConflictCodesAndPayloadTooLargeAreDefiniteNeverUnknown() = runTest {
        val table = listOf(
            failureFor(409, "DELIVERY_SLOT_UNAVAILABLE") to OrderFailure.SlotUnavailable,
            failureFor(409, "STALE_VERSION") to OrderFailure.StaleVersion,
            failureFor(409, "INVALID_TRANSITION") to OrderFailure.InvalidTransition,
            failureFor(409, "ORDER_NOT_CANCELLABLE") to OrderFailure.NotCancellable,
            failureFor(409, "CANCELLATION_WINDOW_CLOSED") to OrderFailure.CancellationWindowClosed,
            failureFor(413, "PAYLOAD_TOO_LARGE") to OrderFailure.ClientBug
        )
        for ((actual, expected) in table) {
            assertEquals(expected, actual)
            assertFalse(actual == OrderFailure.Unknown, expected.toString())
            assertFalse(actual.isAmbiguous, expected.toString())
        }
    }

    @Test fun anUnrecognisedConflictCodeStillMapsToUnknown() = runTest {
        assertEquals(OrderFailure.Unknown, failureFor(409, "SOME_FUTURE_CODE"))
        assertEquals(OrderFailure.Unknown, failureFor(409, "PAYLOAD_TOO_LARGE"))
    }

    @Test fun ambiguityIsExactlyTheCasesWhereAnOrderMayExist() {
        for (f in listOf(OrderFailure.Unavailable, OrderFailure.Server, OrderFailure.Network, OrderFailure.Timeout, OrderFailure.Unknown)) assertTrue(f.isAmbiguous, f.toString())
        for (f in listOf(OrderFailure.QuoteExpired, OrderFailure.NotFound, OrderFailure.AddressChanged, OrderFailure.NotServiceable, OrderFailure.PriceChanged,
            OrderFailure.ProductUnavailable, OrderFailure.StockUnavailable, OrderFailure.ReservationExpired, OrderFailure.CartAlreadyPurchased, OrderFailure.ClientBug,
            OrderFailure.SlotUnavailable, OrderFailure.StaleVersion, OrderFailure.InvalidTransition, OrderFailure.NotCancellable, OrderFailure.CancellationWindowClosed,
            OrderFailure.Unauthenticated, OrderFailure.NotLaunched, OrderFailure.QuoteNotReady)) assertFalse(f.isAmbiguous, f.toString())
    }

    @Test fun anUnreadable2xxAfterAPostIsAmbiguousNotAFailure() = runTest {
        val (s, _) = ds(body = "not json")
        val f = runCatching { s.placeCodOrder("CHKQ_abc123") }.exceptionOrNull()!!.toOrderFailure()
        assertTrue(f.isAmbiguous)
    }

    @Test fun anUnauthenticatedCallIsNotSentWithoutAToken() = runTest {
        val seen = mutableListOf<HttpRequestData>()
        val api = authedApi(token = null) { req -> seen += req; respond(orderJson(), HttpStatusCode.OK, JSON_HEADERS) }
        runCatching { RemoteOrderDataSource(api).placeCodOrder("CHKQ_abc123") }
        assertTrue(seen.isEmpty() || seen.single().headers[HttpHeaders.Authorization] == null)
    }
}
