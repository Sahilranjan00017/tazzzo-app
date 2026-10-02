package com.tazzzo.app.checkout

import com.tazzzo.app.address.authedApi
import com.tazzzo.app.auth.JSON_HEADERS
import com.tazzzo.app.auth.bodyText
import com.tazzzo.app.data.checkout.BenefitPreviewState
import com.tazzzo.app.data.checkout.CheckoutFailure
import com.tazzzo.app.data.checkout.ItemRejection
import com.tazzzo.app.data.checkout.RemoteCheckoutDataSource
import com.tazzzo.app.data.checkout.toCheckoutFailure
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
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue

class RemoteCheckoutDataSourceTest {
    private fun quoteJson(
        unit: Long = 4_950, qty: Int = 2, subtotal: Long = 9_900, created: String = "2026-10-02T09:00:00.000Z", expires: String = "2026-10-02T09:05:00.000Z",
        benefit: String = "", cartVersion: Long = 7
    ) = """{"quoteId":"CHKQ_abc123","cartVersion":$cartVersion,"addressId":"ADDR_abcdef1",
        "items":[{"skuId":"TZP-1","quantity":$qty,"unitPricePaise":$unit,"lineTotalPaise":${unit * qty}}],
        "itemCount":$qty,"distinctItemCount":1,"subtotalPaise":$subtotal,"currency":"INR","createdAt":"$created","expiresAt":"$expires"$benefit,"requestId":"req_q"}"""

    private fun ds(seen: MutableList<HttpRequestData> = mutableListOf(), body: String = quoteJson(), status: HttpStatusCode = HttpStatusCode.OK) =
        RemoteCheckoutDataSource(authedApi { req -> seen += req; respond(body, status, JSON_HEADERS) }) to seen

    // ---- request shape -------------------------------------------------------------------------------------------------

    @Test fun postSendsOnlyTheBearerTheCartIfMatchTheKeyAndTheAddressId() = runTest {
        val (s, seen) = ds()
        s.createQuote(7, "ADDR_abcdef1", "key-abcdefgh")
        val r = seen.single()
        assertEquals(HttpMethod.Post, r.method); assertEquals("/v1/customer/checkout/quote", r.url.encodedPath)
        assertEquals("Bearer acc1", r.headers[HttpHeaders.Authorization])
        assertEquals("\"cart-7\"", r.headers[HttpHeaders.IfMatch])
        assertEquals("key-abcdefgh", r.headers["Idempotency-Key"])
        assertEquals("""{"addressId":"ADDR_abcdef1"}""", r.bodyText().replace(" ", ""))
        assertTrue(r.url.parameters.isEmpty())
    }

    @Test fun theBodyNeverCarriesPaymentSlotCouponMembershipCoinsPinCoordinatesOrATotal() = runTest {
        val (s, seen) = ds(); s.createQuote(7, "ADDR_abcdef1", "key-abcdefgh")
        val body = seen.single().bodyText().lowercase() + seen.single().url.toString().lowercase()
        for (f in listOf("payment", "slot", "coupon", "member", "coin", "pin", "postal", "lat", "lng", "fulfillment", "total", "price")) assertFalse(f in body, f)
    }

    @Test fun getSendsNoIdempotencyKeyIfMatchOrBody() = runTest {
        val (s, seen) = ds(); s.getQuote("CHKQ_abc123")
        val r = seen.single()
        assertEquals(HttpMethod.Get, r.method); assertEquals("/v1/customer/checkout/quotes/CHKQ_abc123", r.url.encodedPath)
        assertNull(r.headers["Idempotency-Key"]); assertNull(r.headers[HttpHeaders.IfMatch]); assertEquals("", r.bodyText())
    }

    @Test fun malformedIdsAndKeysAreNeverSent() = runTest {
        val (s, seen) = ds()
        assertFailsWith<IllegalArgumentException> { s.createQuote(7, "560047", "key-abcdefgh") }
        assertFailsWith<IllegalArgumentException> { s.createQuote(7, "ADDR_abcdef1", "short") }
        assertFailsWith<IllegalArgumentException> { s.createQuote(7, "ADDR_abcdef1", "has spaces and !!") }
        assertFailsWith<IllegalArgumentException> { s.createQuote(-1, "ADDR_abcdef1", "key-abcdefgh") }
        assertFailsWith<IllegalArgumentException> { s.getQuote("../x") }
        assertFailsWith<IllegalArgumentException> { s.getQuote("TZP-1") }
        assertTrue(seen.isEmpty())
    }

    // ---- mapping / money -----------------------------------------------------------------------------------------------

    @Test fun moneyIsExactPaiseWithNoRounding49_50() = runTest {
        val (s, _) = ds(body = quoteJson(unit = 4_950, qty = 3, subtotal = 14_850))
        val q = s.createQuote(7, "ADDR_abcdef1", "key-abcdefgh")
        assertEquals(4_950L, q.items.single().unitPrice.paise); assertEquals("₹49.50", q.items.single().unitPrice.format())
        assertEquals(14_850L, q.items.single().lineTotal.paise); assertEquals(14_850L, q.subtotal.paise)
    }

    @Test fun theLifetimeIsDerivedFromTheServersOwnTimestamps() = runTest {
        val (s, _) = ds()
        assertEquals(300, s.createQuote(7, "ADDR_abcdef1", "key-abcdefgh").lifetime.inWholeSeconds)
        val (s2, _) = ds(body = quoteJson(expires = "2026-10-02T09:01:30.500Z"))
        assertEquals(90_500, s2.createQuote(7, "ADDR_abcdef1", "key-abcdefgh").lifetime.inWholeMilliseconds)
    }

    @Test fun negativeMoneyOrAnInvertedLifetimeIsAContractViolation() = runTest {
        for (bad in listOf(quoteJson(unit = -1), quoteJson(subtotal = -5), quoteJson(expires = "2026-10-02T08:00:00.000Z"), quoteJson(created = "garbage"), quoteJson(qty = 0))) {
            val (s, _) = ds(body = bad)
            assertFailsWith<ApiException> { s.createQuote(7, "ADDR_abcdef1", "key-abcdefgh") }
        }
    }

    @Test fun theQuoteExposesNoPayableTotalFeeTaxOrAddressSnapshot() = runTest {
        // Structural, not reflective (iOS-safe): the printed form of a mapped quote has nothing but counts, and
        // the only money on it is the item subtotal and the per-line prices the server sent.
        val (s, _) = ds(); val q = s.createQuote(7, "ADDR_abcdef1", "key-abcdefgh")
        assertEquals("CheckoutQuote(1 lines)", q.toString())
        assertEquals(9_900L, q.subtotal.paise)                              // the ONLY aggregate: no fee/tax/discount is added to it
    }

    // ---- benefit preview ---------------------------------------------------------------------------------------------------

    @Test fun benefitPreviewAbsentIsALegacyQuote() = runTest {
        val (s, _) = ds(); assertEquals(BenefitPreviewState.Legacy, s.createQuote(7, "ADDR_abcdef1", "key-abcdefgh").benefit)
    }

    @Test fun benefitPreviewNotAppliedIsDistinctFromAbsent() = runTest {
        val (s, _) = ds(body = quoteJson(benefit = ""","benefitPreview":{"applied":false}"""))
        assertEquals(BenefitPreviewState.NotApplied, s.createQuote(7, "ADDR_abcdef1", "key-abcdefgh").benefit)
    }

    @Test fun benefitPreviewAppliedCarriesPaiseAndBpsAndNeverTouchesTheSubtotal() = runTest {
        val (s, _) = ds(body = quoteJson(benefit = ""","benefitPreview":{"applied":true,"discountPaise":495,"discountBps":500}"""))
        val q = s.createQuote(7, "ADDR_abcdef1", "key-abcdefgh")
        val b = assertIs<BenefitPreviewState.Applied>(q.benefit)
        assertEquals(495L, b.discount.paise); assertEquals(500, b.discountBps)
        assertEquals(9_900L, q.subtotal.paise)                              // untouched: advisory only
    }

    @Test fun aBenefitPreviewThatBreaksTheContractIsUnreadableAndNeverABenefit() = runTest {
        for (j in listOf(
            """{"applied":true}""", """{"applied":true,"discountPaise":0,"discountBps":500}""", """{"applied":true,"discountPaise":5,"discountBps":10001}""",
            """{"applied":false,"discountPaise":5}""", """{"applied":false,"discountBps":5}"""
        )) {
            val (s, _) = ds(body = quoteJson(benefit = ""","benefitPreview":$j"""))
            assertEquals(BenefitPreviewState.Unreadable, s.createQuote(7, "ADDR_abcdef1", "key-abcdefgh").benefit, j)
        }
    }

    // ---- errors ------------------------------------------------------------------------------------------------------------

    private suspend fun failureFor(status: Int, code: String, extra: String = ""): CheckoutFailure {
        val api = authedApi { respond("""{"code":"$code","message":"internal detail","requestId":"req_e"$extra}""", HttpStatusCode.fromValue(status), JSON_HEADERS) }
        return runCatching { RemoteCheckoutDataSource(api).createQuote(7, "ADDR_abcdef1", "key-abcdefgh") }.exceptionOrNull()!!.toCheckoutFailure()
    }

    @Test fun everyDocumentedStatusMapsToATypedFailure() = runTest {
        assertEquals(CheckoutFailure.CartChanged, failureFor(412, "PRECONDITION_FAILED"))
        assertEquals(CheckoutFailure.CartEmpty, failureFor(409, "CHECKOUT_CART_EMPTY"))
        assertEquals(CheckoutFailure.Unserviceable, failureFor(409, "CHECKOUT_UNSERVICEABLE"))
        assertEquals(CheckoutFailure.KeyConflict, failureFor(409, "IDEMPOTENCY_CONFLICT"))
        assertEquals(CheckoutFailure.QuoteExpired, failureFor(410, "QUOTE_EXPIRED"))
        assertEquals(CheckoutFailure.NotFound, failureFor(404, "NOT_FOUND"))
        assertEquals(CheckoutFailure.ClientBug, failureFor(428, "PRECONDITION_REQUIRED"))
        assertEquals(CheckoutFailure.ClientBug, failureFor(428, "IDEMPOTENCY_REQUIRED"))
        assertEquals(CheckoutFailure.ClientBug, failureFor(400, "INVALID_REQUEST"))
        assertEquals(CheckoutFailure.ClientBug, failureFor(415, "UNSUPPORTED_MEDIA_TYPE"))
        assertEquals(CheckoutFailure.Unavailable, failureFor(503, "SERVICE_UNAVAILABLE"))
        assertEquals(CheckoutFailure.Server, failureFor(500, "INTERNAL"))
        assertEquals(CheckoutFailure.RateLimited(7), failureFor(429, "RATE_LIMITED", ""","retryAfterSeconds":7"""))
    }

    @Test fun anItemRejectionBodyKeepsEverySkuAndReason() = runTest {
        val f = failureFor(409, "CHECKOUT_ITEM_UNAVAILABLE", ""","items":[{"skuId":"TZP-1","reason":"INSUFFICIENT_STOCK"},{"skuId":"TZP-2","reason":"PRODUCT_UNAVAILABLE"},{"skuId":"TZP-3","reason":"WHAT"}]""")
        val items = assertIs<CheckoutFailure.ItemsUnavailable>(f).items
        assertEquals(ItemRejection.Known("TZP-1", ItemRejection.Reason.INSUFFICIENT_STOCK), items[0])
        assertEquals(ItemRejection.Known("TZP-2", ItemRejection.Reason.PRODUCT_UNAVAILABLE), items[1])
        assertEquals(ItemRejection.Unrecognized("TZP-3"), items[2])
    }

    @Test fun ambiguityIsExactlyTheCasesWhereTheRequestMayHaveLanded() {
        for (f in listOf(CheckoutFailure.Network, CheckoutFailure.Timeout, CheckoutFailure.Server, CheckoutFailure.Unknown, CheckoutFailure.Unavailable, CheckoutFailure.RateLimited(1))) assertTrue(f.isAmbiguous)
        for (f in listOf(CheckoutFailure.CartChanged, CheckoutFailure.CartEmpty, CheckoutFailure.Unserviceable, CheckoutFailure.KeyConflict, CheckoutFailure.QuoteExpired, CheckoutFailure.NotFound, CheckoutFailure.ClientBug)) assertFalse(f.isAmbiguous)
    }

    @Test fun anUnauthenticatedCallIsNotSentWithoutAToken() = runTest {
        val seen = mutableListOf<HttpRequestData>()
        val api = authedApi(token = null) { req -> seen += req; respond(quoteJson(), HttpStatusCode.OK, JSON_HEADERS) }
        runCatching { RemoteCheckoutDataSource(api).createQuote(7, "ADDR_abcdef1", "key-abcdefgh") }
        assertTrue(seen.isEmpty() || seen.single().headers[HttpHeaders.Authorization] == null)
    }
}
