package com.tazzzo.app.catalog

import com.tazzzo.app.data.catalog.ImageRole
import com.tazzzo.app.data.catalog.InstallationId
import com.tazzzo.app.data.catalog.Pincode
import com.tazzzo.app.data.catalog.StockState
import com.tazzzo.app.data.remote.Conditional
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

class CatalogContractTest {
    private val pin = Pincode.parse("560047")!!

    /** Every request must be anonymous, carry the install id and never carry lat/lng/release. */
    private fun HttpRequestData.assertPublicShape() {
        assertEquals(HttpMethod.Get, method)
        assertNull(headers[HttpHeaders.Authorization], "catalogue calls are anonymous")
        assertEquals(INSTALL_ID, headers[InstallationId.HEADER])
        for (forbidden in listOf("lat", "lng", "release")) assertFalse(url.parameters.contains(forbidden), forbidden)
    }

    @Test fun categoriesSuccess() = runTest {
        var seen: HttpRequestData? = null
        val ds = dataSource { seen = it; respond(nodeListJson("TZS-000001" to "Staples", "TZS-000002" to "Food"), HttpStatusCode.OK, etagHeaders("\"abc\"")) }
        val r = assertIs<Conditional.Modified<*>>(ds.categories())
        val page = r.response.body as com.tazzzo.app.data.catalog.TaxonomyPage
        assertEquals("/v1/categories", seen!!.url.encodedPath); seen!!.assertPublicShape()
        assertEquals("rel_1", page.resolvedReleaseId)
        assertEquals(listOf("TZS-000001" to "Staples", "TZS-000002" to "Food"), page.items.map { it.id to it.name })
        assertEquals("\"abc\"", r.response.etag)
    }

    @Test fun childrenSuccess() = runTest {
        var seen: HttpRequestData? = null
        val ds = dataSource { seen = it; respond(nodeListJson("TZC-000010" to "Rice"), HttpStatusCode.OK, JSON) }
        val page = (assertIs<Conditional.Modified<com.tazzzo.app.data.catalog.TaxonomyPage>>(ds.children("TZS-000001"))).response.body
        assertEquals("/v1/categories/TZS-000001/children", seen!!.url.encodedPath); seen!!.assertPublicShape()
        assertEquals("TZC-000010", page.items.single().id)
    }

    @Test fun anEmptyChildListIsValid() = runTest {
        val ds = dataSource { respond(nodeListJson(), HttpStatusCode.OK, JSON) }
        val page = (ds.children("TZV-000225") as Conditional.Modified).response.body
        assertTrue(page.items.isEmpty())
    }

    @Test fun productListSuccessSendsPageSizeCursorAndPin() = runTest {
        var seen: HttpRequestData? = null
        val ds = dataSource { seen = it; respond(pageJson(listOf(cardJson("TZP-1"), cardJson("TZP-2")), next = "CUR_opaque"), HttpStatusCode.OK, JSON) }
        val page = ds.products("TZC-000010", pin, cursor = "PREV", pageSize = 20)
        val u = seen!!.url
        assertEquals("/v1/categories/TZC-000010/products", u.encodedPath); seen!!.assertPublicShape()
        assertEquals("20", u.parameters["page_size"]); assertEquals("PREV", u.parameters["cursor"]); assertEquals("560047", u.parameters["pin"])
        assertEquals(listOf("TZP-1", "TZP-2"), page.items.map { it.productId })
        assertEquals("CUR_opaque", page.nextCursor); assertTrue(page.hasMore)
        assertEquals("SA-BLR-01", page.serviceArea!!.serviceAreaId); assertTrue(page.serviceArea!!.serviceable)
    }

    @Test fun anonymousListOmitsPinAndCursor() = runTest {
        var seen: HttpRequestData? = null
        val ds = dataSource { seen = it; respond(pageJson(listOf(cardJson("TZP-1", stock = "UNKNOWN", buyable = false, serviceable = "null", max = 0)), area = null), HttpStatusCode.OK, JSON) }
        val page = ds.products("TZC-000010", pin = null)
        assertFalse(seen!!.url.parameters.contains("pin")); assertFalse(seen!!.url.parameters.contains("cursor"))
        assertNull(page.serviceArea); assertNull(page.nextCursor); assertFalse(page.hasMore)
    }

    @Test fun lastPageHasNoCursorAndHasMoreFalse() = runTest {
        val page = dataSource { respond(pageJson(listOf(cardJson())), HttpStatusCode.OK, JSON) }.products("TZC-000010", pin)
        assertFalse(page.hasMore); assertNull(page.nextCursor)
    }

    @Test fun pdpSuccessMapsGalleryAndAttributes() = runTest {
        var seen: HttpRequestData? = null
        val ds = dataSource { seen = it; respond(detailJson("TZP-7"), HttpStatusCode.OK, JSON) }
        val d = ds.product("TZP-7", pin)
        assertEquals("/v1/products/TZP-7", seen!!.url.encodedPath); seen!!.assertPublicShape()
        assertEquals("560047", seen!!.url.parameters["pin"])
        assertEquals("TZP-7", d.product.productId); assertEquals("rel_1", d.resolvedReleaseId)
        // sorted by order; the non-HTTPS image is dropped, not rendered
        assertEquals(listOf(0, 2), d.gallery.map { it.order })
        assertEquals(ImageRole.PRIMARY, d.gallery[0].role); assertEquals("front", d.gallery[0].alt); assertEquals(800, d.gallery[0].width)
        assertEquals(ImageRole.GALLERY, d.gallery[1].role)
        assertEquals(listOf("weight", "veg"), d.attributes.map { it.key }); assertEquals("g", d.attributes[0].unit)
    }

    /** The platform's product-id grammar `TZP-[A-Za-z0-9-]{1,40}`: alphanumeric ids reach the path as ONE segment, unescaped. */
    @Test fun alphanumericProductIdsReachThePathAsOneSegment() = runTest {
        for (id in listOf("TZP-MED-3", "TZP-" + "A".repeat(40))) {
            var seen: HttpRequestData? = null
            val d = dataSource { seen = it; respond(detailJson(id), HttpStatusCode.OK, JSON) }.product(id, pin)
            assertEquals("/v1/products/$id", seen!!.url.encodedPath); seen!!.assertPublicShape()
            assertEquals(id, d.product.productId)
        }
    }

    @Test fun serviceabilityTrue() = runTest {
        var seen: HttpRequestData? = null
        val r = serviceabilitySource { seen = it; respond(serviceabilityJson(true), HttpStatusCode.OK, JSON) }.check(pin)
        assertEquals("/v1/serviceability", seen!!.url.encodedPath); seen!!.assertPublicShape()
        assertEquals("560047", seen!!.url.parameters["pin"])
        assertTrue(r.serviceable); assertEquals("SA-BLR-01", r.serviceAreaId); assertEquals(7L, r.serviceAreaVersion)
        assertNull(r.etaMinutesMin) // ETA is never required
    }

    @Test fun serviceabilityFalseWithAndWithoutAnArea() = runTest {
        val noArea = serviceabilitySource { respond(serviceabilityJson(false, area = false), HttpStatusCode.OK, JSON) }.check(pin)
        assertFalse(noArea.serviceable); assertNull(noArea.serviceAreaId)
        val inactive = serviceabilitySource { respond(serviceabilityJson(false, area = true), HttpStatusCode.OK, JSON) }.check(pin)
        assertFalse(inactive.serviceable); assertEquals("SA-BLR-01", inactive.serviceAreaId)
    }

    @Test fun etaIsMappedWhenPresentButNotRequired() = runTest {
        val r = serviceabilitySource { respond("""{"serviceable":true,"etaMinutesMin":20,"etaMinutesMax":35}""", HttpStatusCode.OK, JSON) }.check(pin)
        assertEquals(20 to 35, r.etaMinutesMin to r.etaMinutesMax)
    }

    @Test fun idsAreValidatedBeforeTheyReachThePath() = runTest {
        val ds = dataSource { respond("{}", HttpStatusCode.OK, JSON) }
        assertFailsWith<IllegalArgumentException> { ds.children("../auth") }
        assertFailsWith<IllegalArgumentException> { ds.products("TZC-1", pin) }
        assertFailsWith<IllegalArgumentException> { ds.product("TZP-1/../x", pin) }
        for (bad in listOf("", "TZP-", "TZP-" + "A".repeat(41), "TZP-../x", "TZP-a b", "TZP-1\n", "TZP-1?pin=1", "TZP-1%2F")) {
            assertFailsWith<IllegalArgumentException>("must refuse <$bad>") { ds.product(bad, pin) }
        }
        assertFailsWith<IllegalArgumentException> { ds.products("TZC-000010", pin, pageSize = 0) }
        assertFailsWith<IllegalArgumentException> { ds.products("TZC-000010", pin, pageSize = 51) }
    }

    @Test fun malformedBodiesAreDecodingErrors() = runTest {
        val ds = dataSource { respond("""{"items":"nope"}""", HttpStatusCode.OK, JSON) }
        val e = assertFailsWith<com.tazzzo.app.data.remote.ApiException> { ds.products("TZC-000010", pin) }
        assertIs<com.tazzzo.app.data.remote.ApiError.Decoding>(e.error)
        val pdp = dataSource { respond("""{"productId":"TZP-1"}""", HttpStatusCode.OK, JSON) }
        assertIs<com.tazzzo.app.data.remote.ApiError.Decoding>(assertFailsWith<com.tazzzo.app.data.remote.ApiException> { pdp.product("TZP-1", pin) }.error)
    }

    @Test fun unknownStockWireValueIsUnknownNotOutOfStock() {
        assertEquals(StockState.UNKNOWN, StockState.fromWire("BRAND_NEW_STATE"))
        assertEquals(StockState.UNKNOWN, StockState.fromWire(null))
    }
}
