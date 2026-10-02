package com.tazzzo.app.payable

import com.tazzzo.app.address.authedApi
import com.tazzzo.app.auth.JSON_HEADERS
import com.tazzzo.app.auth.errorJson
import com.tazzzo.app.data.checkout.CheckoutFailure
import com.tazzzo.app.data.checkout.RemoteCheckoutDataSource
import com.tazzzo.app.data.checkout.toCheckoutFailure
import com.tazzzo.app.data.order.OrderFailure
import com.tazzzo.app.data.order.RemoteOrderDataSource
import com.tazzzo.app.data.order.toOrderFailure
import com.tazzzo.app.data.remote.ApiException
import io.ktor.client.engine.mock.respond
import io.ktor.http.HttpStatusCode
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** The binding `moneyPreview` (quote) and authoritative `money` (order) as they come off the wire, against backend 3839f3d. */
class PayableWireTest {
    private fun money(s: Long, d: Long, p: Long) = """{"merchandiseSubtotalPaise":$s,"benefitDiscountPaise":$d,"payablePaise":$p}"""

    private fun quoteJson(subtotal: Long = 9_900, unit: Long = 4_950, qty: Int = 2, extra: String = "") =
        """{"quoteId":"CHKQ_abc123","cartVersion":7,"addressId":"ADDR_abcdef1",
        "items":[{"skuId":"TZP-1","quantity":$qty,"unitPricePaise":$unit,"lineTotalPaise":${unit * qty}}],
        "itemCount":$qty,"distinctItemCount":1,"subtotalPaise":$subtotal,"currency":"INR",
        "createdAt":"2026-10-02T09:00:00.000Z","expiresAt":"2026-10-02T09:05:00.000Z"$extra,"requestId":"req_q"}"""

    private fun orderJson(subtotal: Long = 9_900, extra: String = "") =
        """{"orderId":"ORD_abc123","status":"CONFIRMED","paymentMethod":"COD","paymentCondition":"COD_DUE",
        "items":[{"skuId":"TZP-1","title":"Atta 1kg","quantity":2,"unitPricePaise":4950,"lineTotalPaise":9900}],
        "itemCount":2,"subtotalPaise":$subtotal,"currency":"INR","createdAt":"2026-10-02T09:00:00.000Z",
        "confirmedAt":"2026-10-02T09:00:01.000Z"$extra,"requestId":"req_o"}"""

    private fun quotes(body: String, status: HttpStatusCode = HttpStatusCode.OK) =
        RemoteCheckoutDataSource(authedApi { respond(body, status, JSON_HEADERS) })

    private fun orders(body: String, status: HttpStatusCode = HttpStatusCode.OK) =
        RemoteOrderDataSource(authedApi { respond(body, status, JSON_HEADERS) })

    private suspend fun quoteFailure(body: String): CheckoutFailure =
        assertFailsWith<ApiException> { quotes(body).createQuote(7, "ADDR_abcdef1", "key-abcdefgh") }.toCheckoutFailure()

    // ---- quote: moneyPreview ------------------------------------------------------------------------------------------

    @Test fun aBindingMoneyPreviewIsMappedExactly() = runTest {
        val q = quotes(quoteJson(subtotal = 10_000, unit = 5_000, extra = ""","moneyPreview":${money(10_000, 1_000, 9_000)}"""))
            .createQuote(7, "ADDR_abcdef1", "key-abcdefgh")
        val m = assertNotNull(q.money)
        assertEquals(10_000L, m.merchandiseSubtotal.paise); assertEquals(1_000L, m.benefitDiscount.paise); assertEquals(9_000L, m.payable.paise)
        assertEquals(10_000L, q.subtotal.paise)
    }

    @Test fun fortyNineFiftyIsExactOnTheWire() = runTest {
        val q = quotes(quoteJson(subtotal = 4_950, unit = 4_950, qty = 1, extra = ""","moneyPreview":${money(4_950, 0, 4_950)}"""))
            .createQuote(7, "ADDR_abcdef1", "key-abcdefgh")
        assertEquals(4_950L, q.money!!.payable.paise); assertEquals("₹49.50", q.money!!.payable.format())
    }

    @Test fun aZeroPayableQuoteIsValid() = runTest {
        val q = quotes(quoteJson(extra = ""","moneyPreview":${money(9_900, 9_900, 0)}""")).createQuote(7, "ADDR_abcdef1", "key-abcdefgh")
        assertEquals(0L, q.money!!.payable.paise)
    }

    @Test fun anAbsentMoneyPreviewIsALegacyQuoteNeverAZeroAmount() = runTest {
        val q = quotes(quoteJson()).createQuote(7, "ADDR_abcdef1", "key-abcdefgh")
        assertNull(q.money)
    }

    @Test fun inconsistentMoneyIsAContractFailureNeverAQuote() = runTest {
        for (bad in listOf(
            money(-1, 0, -1), money(9_900, -1, 9_901), money(9_900, 9_901, 0),          // negative / over-discount
            money(9_900, 100, 9_900), money(9_900, 100, 9_700),                       // payable != subtotal - discount
            money(9_800, 0, 9_800)                                                    // disagrees with subtotalPaise (9_900)
        )) assertEquals(CheckoutFailure.ContractViolation, quoteFailure(quoteJson(extra = ""","moneyPreview":$bad""")), bad)
    }

    @Test fun aStructurallyInvalidMoneyBlockIsAContractFailure() = runTest {
        for (bad in listOf("""{"merchandiseSubtotalPaise":9900}""", "\"9900\"", """{"merchandiseSubtotalPaise":"x","benefitDiscountPaise":0,"payablePaise":9900}"""))
            assertEquals(CheckoutFailure.ContractViolation, quoteFailure(quoteJson(extra = ""","moneyPreview":$bad""")), bad)
    }

    @Test fun aContractFailureIsNotAmbiguousAndCarriesNoAmount() = runTest {
        val e = assertFailsWith<ApiException> {
            quotes(quoteJson(extra = ""","moneyPreview":${money(9_900, 100, 9_700)}""")).createQuote(7, "ADDR_abcdef1", "key-abcdefgh")
        }
        assertFalse(e.toCheckoutFailure().isAmbiguous)
        val text = "${e.message} $e ${e.cause}"
        for (d in listOf("9900", "9700", "99", "97")) assertFalse(d in text, d)
    }

    // ---- order: money ----------------------------------------------------------------------------------------------------

    @Test fun anOrdersAuthoritativeMoneyIsMappedExactly() = runTest {
        val o = orders(orderJson(extra = ""","money":${money(9_900, 900, 9_000)}""")).placeCodOrder("CHKQ_abc123")
        val m = assertNotNull(o.money)
        assertEquals(9_900L, m.merchandiseSubtotal.paise); assertEquals(900L, m.benefitDiscount.paise); assertEquals(9_000L, m.payable.paise)
    }

    @Test fun aZeroPayableOrderIsValid() = runTest {
        assertEquals(0L, orders(orderJson(extra = ""","money":${money(9_900, 9_900, 0)}""")).placeCodOrder("CHKQ_abc123").money!!.payable.paise)
    }

    @Test fun aLegacyOrderWithoutMoneyHasNoneNeverZero() = runTest {
        assertNull(orders(orderJson()).placeCodOrder("CHKQ_abc123").money)
        assertNull(orders(orderJson()).getOrder("ORD_abc123").money)
    }

    @Test fun inconsistentOrderMoneyOrADisagreeingSubtotalIsAContractFailure() = runTest {
        for (bad in listOf(money(9_900, 100, 9_900), money(9_900, 9_901, 0), money(-1, 0, -1), money(9_800, 0, 9_800))) {
            assertFailsWith<ApiException>(bad) { orders(orderJson(extra = ""","money":$bad""")).placeCodOrder("CHKQ_abc123") }
            assertFailsWith<ApiException>(bad) { orders(orderJson(extra = ""","money":$bad""")).getOrder("ORD_abc123") }
        }
    }

    // ---- PAYABLE_CHANGED -------------------------------------------------------------------------------------------------

    @Test fun payableChangedIsATypedDefinitiveFailureNeverAmbiguous() = runTest {
        val e = assertFailsWith<ApiException> { orders(errorJson("PAYABLE_CHANGED"), HttpStatusCode.Conflict).placeCodOrder("CHKQ_abc123") }
        val f = e.toOrderFailure()
        assertEquals(OrderFailure.PayableChanged, f)
        assertFalse(f.isAmbiguous)
    }

    @Test fun genuinelyAmbiguousAnswersStayAmbiguous() = runTest {
        for (s in listOf(HttpStatusCode.InternalServerError, HttpStatusCode.ServiceUnavailable, HttpStatusCode.BadGateway)) {
            val e = assertFailsWith<ApiException> { orders(errorJson("INTERNAL"), s).placeCodOrder("CHKQ_abc123") }
            assertTrue(e.toOrderFailure().isAmbiguous, s.toString())
        }
    }
}
