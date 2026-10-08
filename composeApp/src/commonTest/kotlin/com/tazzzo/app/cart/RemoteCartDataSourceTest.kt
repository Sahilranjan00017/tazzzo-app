package com.tazzzo.app.cart

import com.tazzzo.app.address.authedApi
import com.tazzzo.app.auth.JSON_HEADERS
import com.tazzzo.app.auth.bodyText
import com.tazzzo.app.auth.errorJson
import com.tazzzo.app.data.cart.CartFailure
import com.tazzzo.app.data.cart.KnownIssue
import com.tazzzo.app.data.cart.LineIssue
import com.tazzzo.app.data.cart.RemoteCartDataSource
import com.tazzzo.app.data.cart.toCartFailure
import com.tazzzo.app.data.catalog.StockState
import com.tazzzo.app.data.remote.ApiException
import io.ktor.client.engine.mock.respond
import io.ktor.client.request.HttpRequestData
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpMethod
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue

class RemoteCartDataSourceTest {
    private fun cartJson(version: Long = 3, items: String = "", count: Int = 0, subtotal: Long = 0) =
        """{"version":$version,"items":[$items],"itemCount":$count,"distinctItemCount":${if (items.isBlank()) 0 else 1},"subtotalPaise":$subtotal,"expiresAt":null,"requestId":"req_c"}"""

    private val itemJson = """{"skuId":"TZP-7","quantity":2,"addedAt":"2026-01-01T00:00:00Z","updatedAt":"2026-01-01T00:00:00Z",
        "product":{"title":"Atta 1kg","brandCode":"BRD_1","imageUrl":"https://cdn.example.test/a.jpg"},
        "price":{"unitPricePaise":5000,"mrpPaise":6000,"currency":"INR"},
        "availability":{"stockState":"LOW_STOCK","maxOrderQuantity":5,"serviceable":true},
        "lineTotalPaise":10000,"buyable":true,"issues":[]}"""

    private fun source(handler: (HttpRequestData) -> String, status: HttpStatusCode = HttpStatusCode.OK, seen: MutableList<HttpRequestData> = mutableListOf()) =
        RemoteCartDataSource(authedApi { req -> seen += req; respond(handler(req), status, JSON_HEADERS) }) to seen

    @Test fun getIsAnAuthenticatedGetWithNoLocationWhenThereIsNoAddress() = runTest {
        val (s, seen) = source({ cartJson() })
        s.get(null)
        val r = seen.single()
        assertEquals(HttpMethod.Get, r.method); assertEquals("/v1/customer/cart", r.url.encodedPath)
        assertEquals("Bearer acc1", r.headers[HttpHeaders.Authorization])
        assertTrue(r.url.parameters.isEmpty())
    }

    @Test fun theAddressIdIsTheOnlyLocationParameter() = runTest {
        val (s, seen) = source({ cartJson() })
        s.get("ADDR_abcdef1")
        assertEquals(setOf("addressId"), seen.single().url.parameters.names())
        assertEquals("ADDR_abcdef1", seen.single().url.parameters["addressId"])
        val raw = seen.single().url.toString().lowercase()
        for (forbidden in listOf("pin", "postal", "lat", "lng", "fulfillment")) assertFalse(forbidden in raw, forbidden)
    }

    @Test fun aMalformedAddressIdIsNeverSent() = runTest {
        val (s, seen) = source({ cartJson() })
        assertFailsWith<IllegalArgumentException> { s.get("../../x") }
        assertFailsWith<IllegalArgumentException> { s.get("560047") }
        assertTrue(seen.isEmpty())
    }

    @Test fun putIsAnAbsoluteQuantityWithQuotedIfMatch() = runTest {
        val (s, seen) = source({ cartJson(version = 5) })
        val cart = s.setQuantity("TZP-7", 3, version = 4, addressId = null)
        val r = seen.single()
        assertEquals(HttpMethod.Put, r.method); assertEquals("/v1/customer/cart/items/TZP-7", r.url.encodedPath)
        assertEquals("\"cart-4\"", r.headers[HttpHeaders.IfMatch])
        assertEquals("""{"quantity":3}""", r.bodyText().replace(" ", ""))
        assertEquals(5, cart.version)
    }

    @Test fun deleteItemAndDeleteCartCarryIfMatch() = runTest {
        val (s, seen) = source({ cartJson() })
        s.removeItem("TZP-7", 9, null); s.clear(10, null)
        assertEquals(HttpMethod.Delete, seen[0].method); assertEquals("/v1/customer/cart/items/TZP-7", seen[0].url.encodedPath)
        assertEquals("\"cart-9\"", seen[0].headers[HttpHeaders.IfMatch])
        assertEquals("/v1/customer/cart", seen[1].url.encodedPath); assertEquals("\"cart-10\"", seen[1].headers[HttpHeaders.IfMatch])
    }

    @Test fun zeroOrNegativeQuantityIsNeverSentAsAPut() = runTest {
        val (s, seen) = source({ cartJson() })
        assertFailsWith<IllegalArgumentException> { s.setQuantity("TZP-7", 0, 1, null) }
        assertFailsWith<IllegalArgumentException> { s.setQuantity("TZP-7", -1, 1, null) }
        assertTrue(seen.isEmpty())
    }

    @Test fun aSkuThatIsNotATzpIdNeverReachesAPath() = runTest {
        val (s, seen) = source({ cartJson() })
        val tooLong = "TZP-" + "A".repeat(41)
        for (bad in listOf("TZP-", "tzp-1", "TZP-1/..", "TZP-1?x=1", "TZP-1 2", "", "TZP-../x", "TZP-a b", "TZP-a/b", "TZP-1.2", "TZP-1%2F", tooLong)) {
            assertFailsWith<IllegalArgumentException>(bad) { s.removeItem(bad, 1, null) }
            assertFailsWith<IllegalArgumentException>(bad) { s.setQuantity(bad, 1, 1, null) }
            assertFalse(RemoteCartDataSource.isValidSku(bad), bad)
        }
        assertTrue(seen.isEmpty())
    }

    /** The platform product-id grammar `TZP-[A-Za-z0-9-]{1,40}` (backend cart `skuId` since #110), numeric ids included. */
    @Test fun aPlatformProductIdIsOneUnescapedPathSegmentForAddAndRemove() = runTest {
        val (s, seen) = source({ cartJson() })
        val max = "TZP-" + "A".repeat(40)
        val ok = listOf("TZP-MED-3", "TZP-1234567890123456789", "TZP-7", max)
        for (id in ok) {
            assertTrue(RemoteCartDataSource.isValidSku(id), id)
            s.setQuantity(id, 1, 1, null); s.removeItem(id, 2, null)
        }
        assertEquals(ok.size * 2, seen.size)
        for ((i, id) in ok.withIndex()) {
            assertEquals(HttpMethod.Put, seen[2 * i].method); assertEquals(HttpMethod.Delete, seen[2 * i + 1].method)
            for (r in seen.subList(2 * i, 2 * i + 2)) {
                assertEquals("/v1/customer/cart/items/$id", r.url.encodedPath)
                assertEquals(listOf("v1", "customer", "cart", "items", id), r.url.segments)
            }
        }
    }

    @Test fun theResponseMapsToTheDomainInPaiseWithNoProductId() = runTest {
        val (s, _) = source({ cartJson(version = 8, items = itemJson, count = 2, subtotal = 10_000) })
        val c = s.get(null)
        assertEquals(8, c.version); assertEquals(2, c.itemCount); assertEquals(10_000L, c.subtotal.paise)
        val l = c.item("TZP-7")!!
        assertEquals(5_000L, l.unitPrice!!.paise); assertEquals(10_000L, l.lineTotal!!.paise)
        assertEquals(StockState.LOW_STOCK, l.stockState); assertEquals(5, l.maxOrderQuantity); assertEquals(true, l.serviceable)
        assertFalse(l.isBlocked)
    }

    @Test fun everyIssueCodeBlocksTheLineAndAnUnknownCodeIsKeptAndBlocks() = runTest {
        for (k in KnownIssue.entries) {
            val j = itemJson.replace("\"issues\":[]", "\"issues\":[\"${k.wire}\"]")
            val (s, _) = source({ cartJson(items = j, count = 2) })
            val l = s.get(null).items.single()
            assertEquals(listOf<LineIssue>(LineIssue.Known(k)), l.issues); assertTrue(l.isBlocked, k.name)
        }
        val (s, _) = source({ cartJson(items = itemJson.replace("\"issues\":[]", "\"issues\":[\"BRAND_NEW\"]"), count = 2) })
        val l = s.get(null).items.single()
        assertEquals(listOf<LineIssue>(LineIssue.Unrecognized("BRAND_NEW")), l.issues); assertTrue(l.isBlocked)
    }

    @Test fun aServerThatSaysNotBuyableIsNeverOverriddenByStock() = runTest {
        val (s, _) = source({ cartJson(items = itemJson.replace("\"buyable\":true", "\"buyable\":false"), count = 2) })
        assertTrue(s.get(null).items.single().isBlocked)
    }

    @Test fun aNegativeAmountIsAContractViolationNotAPrice() = runTest {
        val (s, _) = source({ cartJson(items = itemJson.replace("5000", "-5000"), count = 2) })
        assertFailsWith<ApiException> { s.get(null) }
        val (s2, _) = source({ cartJson(subtotal = -1) })
        assertFailsWith<ApiException> { s2.get(null) }
    }

    @Test fun subtotalIsAnItemSubtotalNotATotal() = runTest {
        val (s, _) = source({ cartJson(items = itemJson, count = 2, subtotal = 10_000) })
        assertEquals(10_000L, s.get(null).subtotal.paise)               // no delivery / tax / offers are ever added
    }

    @Test fun statusCodesMapToFailuresWithoutCarryingServerText() = runTest {
        suspend fun failureFor(status: Int, code: String): CartFailure {
            val api = authedApi { respond(errorJson(code), HttpStatusCode.fromValue(status), JSON_HEADERS) }
            val e = runCatching { RemoteCartDataSource(api).get(null) }.exceptionOrNull()!!
            return e.toCartFailure()
        }
        assertEquals(CartFailure.PreconditionFailed, failureFor(412, "PRECONDITION_FAILED"))
        assertEquals(CartFailure.ClientBug, failureFor(428, "PRECONDITION_REQUIRED"))
        assertEquals(CartFailure.ClientBug, failureFor(415, "UNSUPPORTED_MEDIA_TYPE"))
        assertEquals(CartFailure.ItemLimitReached, failureFor(409, "CART_ITEM_LIMIT_REACHED"))
        assertEquals(CartFailure.NotFound, failureFor(404, "NOT_FOUND"))
        assertEquals(CartFailure.InvalidRequest, failureFor(400, "INVALID_REQUEST"))
        assertEquals(CartFailure.Unavailable, failureFor(503, "X"))
        assertEquals(CartFailure.Server, failureFor(500, "X"))
        assertTrue(CartFailure.Server.isAmbiguous && CartFailure.Timeout.isAmbiguous && CartFailure.Network.isAmbiguous)
        assertFalse(CartFailure.PreconditionFailed.isAmbiguous || CartFailure.Unavailable.isAmbiguous || CartFailure.InvalidRequest.isAmbiguous)
    }

    @Test fun cartModelsNeverPrintTheirContents() {
        val c = cartOf(3, line("TZP-1", 2, title = "Secret Item"))
        assertFalse("Secret" in c.toString()); assertFalse("Secret" in c.items.single().toString()); assertFalse("TZP-1" in c.toString())
    }

    @Test fun anUnauthenticatedGetIsNotSentWithoutAToken() = runTest {
        val seen = mutableListOf<HttpRequestData>()
        val api = authedApi(token = null) { req -> seen += req; respond(cartJson(), HttpStatusCode.OK, JSON_HEADERS) }
        runCatching { RemoteCartDataSource(api).get(null) }
        assertTrue(seen.isEmpty() || seen.single().headers[HttpHeaders.Authorization] == null)
    }
}
