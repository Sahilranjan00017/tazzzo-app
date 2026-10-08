package com.tazzzo.app.content

import com.tazzzo.app.catalog.INSTALL_ID
import com.tazzzo.app.catalog.JSON
import com.tazzzo.app.catalog.errorFlat
import com.tazzzo.app.catalog.installationId
import com.tazzzo.app.catalog.mockApi
import com.tazzzo.app.data.catalog.CatalogFailure
import com.tazzzo.app.data.catalog.InstallationId
import com.tazzzo.app.data.catalog.toCatalogFailure
import com.tazzzo.app.data.content.ContentLink
import com.tazzzo.app.data.content.HomeBlock
import com.tazzzo.app.data.content.RemoteContentDataSource
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
import kotlin.test.assertNull
import kotlin.test.assertTrue

class RemoteContentDataSourceTest {
    private val home = """{"blocks":[
        {"blockId":"B1","type":"BANNER","title":"Festive staples","imageUrl":"https://cdn.example.test/festive.jpg","link":"category:TZV-000037"},
        {"blockId":"R1","type":"PRODUCT_RAIL","title":"Weekly picks","ids":["TZP-1","TZP-2"]},
        {"blockId":"G1","type":"CATEGORY_GRID","title":"Aisles","ids":["TZS-000001"]},
        {"blockId":"F1","type":"FAQ","title":"ignored"},
        {"blockId":"N1","type":"PRODUCT_RAIL","title":null,"ids":null}],"requestId":"req_c"}"""

    private fun source(seen: MutableList<HttpRequestData> = mutableListOf(), body: String = home, status: HttpStatusCode = HttpStatusCode.OK) =
        RemoteContentDataSource(mockApi { req -> seen += req; respond(body, status, JSON) }) { installationId() } to seen

    @Test fun getsTheHomeForTheAppChannelAnonymouslyWithTheInstallationId() = runTest {
        val (s, seen) = source()
        val content = s.home()
        val r = seen.single()
        assertEquals(HttpMethod.Get, r.method); assertEquals("/v1/content/home", r.url.encodedPath)
        assertEquals("app", r.url.parameters["channel"]); assertEquals(1, r.url.parameters.names().size)
        assertNull(r.headers[HttpHeaders.Authorization]); assertEquals(INSTALL_ID, r.headers[InstallationId.HEADER])
        assertEquals(listOf("B1", "R1", "G1"), content.blocks.map { it.blockId })
        val b = content.blocks[0] as HomeBlock.Banner
        assertEquals(ContentLink.Category("TZV-000037"), b.link)
        assertEquals(listOf("TZP-1", "TZP-2"), (content.blocks[1] as HomeBlock.ProductRail).productIds)
    }

    @Test fun aSignedInCustomerStillReadsTheHomeAnonymously() = runTest {
        // A client that HAS a session token (the address/cart client) must not attach it: the Home is public content.
        val seen = mutableListOf<HttpRequestData>()
        val s = RemoteContentDataSource(com.tazzzo.app.address.authedApi("acc1") { req -> seen += req; respond(home, HttpStatusCode.OK, JSON) }) { installationId() }
        s.home()
        assertNull(seen.single().headers[HttpHeaders.Authorization])
    }

    @Test fun theAppRendersTheMobileImageAndReadsSubtitleAndAltTextFromTheBannerModel() = runTest {
        val (s, _) = source(body = """{"blocks":[{"blockId":"B1","type":"BANNER","title":"Festive","subtitle":"Up to 20% off",
            "altText":"A basket of rice and dal","imageUrl":"https://cdn.example.test/mobile.jpg",
            "desktopImageUrl":"https://cdn.example.test/desktop.jpg","link":"search:atta"},
            {"blockId":"Z1","type":"CAROUSEL","title":"later"}],"requestId":"req_c"}""")
        val b = s.home().blocks.single() as HomeBlock.Banner
        assertEquals("https://cdn.example.test/mobile.jpg", b.imageUrl, "desktopImageUrl is the website's; the app keeps imageUrl")
        assertEquals("Up to 20% off", b.subtitle); assertEquals("A basket of rice and dal", b.altText)
        assertEquals(ContentLink.Search("atta"), b.link)
    }

    @Test fun anEmptyHomeIsEmptyNotAnError() = runTest {
        val (s, _) = source(body = """{"blocks":[],"requestId":"req_c"}""")
        assertTrue(s.home().blocks.isEmpty())
    }

    @Test fun aBackendThatDoesNotKnowTheChannelParameterYetIsAnInvalidRequestFailureNotContent() = runTest {
        // Before the multichannel backend is deployed, `channel` is an unknown parameter and the backend answers 400.
        val (s, _) = source(body = errorFlat("INVALID_REQUEST"), status = HttpStatusCode.BadRequest)
        val e = assertFailsWith<ApiException> { s.home() }
        assertEquals(CatalogFailure.InvalidRequest, e.toCatalogFailure())
    }

    @Test fun rateLimitAndOutageMapLikeEveryOtherPublicRead() = runTest {
        val (s1, _) = source(body = errorFlat("RATE_LIMITED", retryable = true, retryAfter = 7), status = HttpStatusCode.TooManyRequests)
        assertEquals(CatalogFailure.RateLimited(7), assertFailsWith<ApiException> { s1.home() }.toCatalogFailure())
        val (s2, _) = source(body = errorFlat("SERVICE_UNAVAILABLE"), status = HttpStatusCode.ServiceUnavailable)
        assertEquals(CatalogFailure.Unavailable, assertFailsWith<ApiException> { s2.home() }.toCatalogFailure())
    }
}
