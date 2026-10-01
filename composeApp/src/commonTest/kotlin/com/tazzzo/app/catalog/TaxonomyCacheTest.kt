package com.tazzzo.app.catalog

import com.tazzzo.app.data.catalog.TaxonomyCache
import com.tazzzo.app.data.remote.ApiException
import io.ktor.client.engine.mock.respond
import io.ktor.client.request.HttpRequestData
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull
import kotlin.test.assertSame
import kotlin.test.assertTrue

class TaxonomyCacheTest {
    private var now = 1_000_000L

    private class Server {
        val calls = mutableListOf<HttpRequestData>()
        var etag = "\"v1\""
        var names = listOf("TZS-000001" to "Staples")
        var failWith: HttpStatusCode? = null
    }

    private fun cache(s: Server) = dataSource { req ->
        s.calls += req
        s.failWith?.let { return@dataSource respond("""{"code":"SERVICE_UNAVAILABLE"}""", it, JSON) }
        if (req.headers[HttpHeaders.IfNoneMatch] == s.etag) respond("", HttpStatusCode.NotModified, headersOf(HttpHeaders.ETag, s.etag))
        else respond(nodeListJson(*s.names.toTypedArray()), HttpStatusCode.OK, etagHeaders(s.etag))
    }.let { TaxonomyCache(it, nowMs = { now }, freshMs = 300_000) }

    @Test fun firstLoadStoresTheEtagAndLaterRevalidationSendsIt() = runTest {
        val s = Server(); val c = cache(s)
        assertEquals("TZS-000001", c.categories().items.single().id)
        assertNull(s.calls[0].headers[HttpHeaders.IfNoneMatch], "nothing to revalidate on first load")
        now += 400_000 // stale
        c.categories()
        assertEquals("\"v1\"", s.calls[1].headers[HttpHeaders.IfNoneMatch])
    }

    @Test fun a304IsCachedSuccessNotAnException() = runTest {
        val s = Server(); val c = cache(s)
        val first = c.categories()
        now += 400_000
        val again = c.categories() // server answers 304
        assertSame(first, again); assertEquals(2, s.calls.size)
    }

    @Test fun a304RefreshesTheAgeSoTheNextReadIsFree() = runTest {
        val s = Server(); val c = cache(s)
        c.categories(); now += 400_000; c.categories()
        now += 100_000; c.categories()
        assertEquals(2, s.calls.size, "fresh again after the 304")
    }

    @Test fun aFreshCopyCostsNoRequest() = runTest {
        val s = Server(); val c = cache(s)
        repeat(5) { c.categories(); now += 10_000 }
        assertEquals(1, s.calls.size)
    }

    @Test fun changedContentReplacesTheCopyAndEtag() = runTest {
        val s = Server(); val c = cache(s)
        c.categories(); s.etag = "\"v2\""; s.names = listOf("TZS-000009" to "New"); now += 400_000
        assertEquals("TZS-000009", c.categories().items.single().id)
        now += 400_000; c.categories()
        assertEquals("\"v2\"", s.calls.last().headers[HttpHeaders.IfNoneMatch])
    }

    @Test fun concurrentFirstLoadsShareOneRequest() = runTest {
        val s = Server(); val c = cache(s)
        (1..5).map { async { c.categories() } }.awaitAll()
        assertEquals(1, s.calls.size)
    }

    @Test fun errorsAreNotMaskedByAStaleCopy() = runTest {
        val s = Server(); val c = cache(s)
        c.categories(); now += 400_000; s.failWith = HttpStatusCode.ServiceUnavailable
        assertFailsWith<ApiException> { c.categories() }
        assertTrue(c.peek() != null, "the copy is kept for a later revalidation, just not served as if current")
    }

    @Test fun childrenAreCachedPerNodeAndInvalidateClears() = runTest {
        val s = Server(); val c = cache(s)
        c.children("TZS-000001"); c.children("TZS-000001"); c.children("TZS-000002")
        assertEquals(2, s.calls.size)
        assertEquals("/v1/categories/TZS-000002/children", s.calls.last().url.encodedPath)
        c.invalidate(); c.children("TZS-000001"); assertEquals(3, s.calls.size)
    }

    @Test fun a304WithoutACachedCopyIsAnError() = runTest {
        val c = TaxonomyCache(dataSource { respond("", HttpStatusCode.NotModified) }, nowMs = { now })
        assertFailsWith<ApiException> { c.categories() }
    }
}
