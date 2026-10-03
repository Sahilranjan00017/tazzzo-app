package com.tazzzo.app.image

import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.ImageBitmapConfig
import androidx.compose.ui.graphics.colorspace.ColorSpace
import androidx.compose.ui.graphics.colorspace.ColorSpaces
import com.tazzzo.app.ui.common.ProductImageState
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.MockRequestHandleScope
import io.ktor.client.engine.mock.respond
import io.ktor.client.request.HttpRequestData
import io.ktor.client.request.HttpResponseData
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import io.ktor.utils.io.ByteChannel
import io.ktor.utils.io.ByteReadChannel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.async
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.yield
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.seconds

/**
 * UI-03 hardening: the network edge of the image pipeline. A deterministic engine stands in for the CDN, so the two
 * release guarantees — never more than the byte cap in memory, never an http hop behind an https URL — are proven
 * without a network.
 */
class KtorImageFetcherTest {
    private val limit = 64 * 1024                      // a small cap keeps the tests fast; the rule is the same at 6 MB
    private val img = headersOf(HttpHeaders.ContentType, "image/jpeg")
    private val good = "https://media.example.test/a.jpg"

    private class Script(val handler: suspend MockRequestHandleScope.(HttpRequestData) -> HttpResponseData) {
        val requested = mutableListOf<String>()
    }

    private fun fetcher(script: Script) = KtorImageFetcher(
        maxBytes = limit,
        client = HttpClient(MockEngine { req -> script.requested += req.url.toString(); script.handler(this, req) }) { expectSuccess = false; followRedirects = false }
    )

    private fun bytes(n: Int) = ByteArray(n) { (it % 251).toByte() }

    private fun MockRequestHandleScope.redirect(to: String, code: HttpStatusCode = HttpStatusCode.Found) =
        respond("", code, headersOf(HttpHeaders.Location, to))

    // ---- size cap ------------------------------------------------------------------------------------------------------

    @Test fun anHttpsImageBelowTheLimitSucceeds() = runTest {
        val s = Script { respond(bytes(limit - 1), headers = img) }
        val out = fetcher(s).fetch(good)
        assertNotNull(out); assertEquals(limit - 1, out.size)
    }

    @Test fun aBodyExactlyAtTheLimitSucceedsAndOneByteOverIsRejected() = runTest {
        assertEquals(limit, fetcher(Script { respond(bytes(limit), headers = img) }).fetch(good)!!.size)
        assertNull(fetcher(Script { respond(ByteReadChannel(bytes(limit + 1)), HttpStatusCode.OK, img) }).fetch(good))
    }

    @Test fun aDeclaredContentLengthAboveTheLimitIsRejectedWithoutReadingTheBody() = runTest(timeout = 5.seconds) {
        // The body channel is never written to: if the fetcher tried to read it, this test would hang and time out.
        val neverWritten = ByteChannel()
        val s = Script {
            respond(neverWritten, HttpStatusCode.OK, headersOf(HttpHeaders.ContentType to listOf("image/jpeg"), HttpHeaders.ContentLength to listOf((limit + 1).toString())))
        }
        assertNull(fetcher(s).fetch(good))
    }

    @Test fun aStreamedBodyWithNoContentLengthIsCutOffAtTheLimit() = runTest {
        val s = Script { respond(ByteReadChannel(bytes(limit * 3)), HttpStatusCode.OK, img) }   // no Content-Length header
        assertNull(fetcher(s).fetch(good))
    }

    @Test fun aMisleadingContentLengthDoesNotLetAnOversizeBodyThrough() = runTest {
        val s = Script {
            respond(ByteReadChannel(bytes(limit * 3)), HttpStatusCode.OK, headersOf(HttpHeaders.ContentType to listOf("image/jpeg"), HttpHeaders.ContentLength to listOf("100")))
        }
        assertNull(fetcher(s).fetch(good))
    }

    @Test fun nonImageContentAndNon2xxStatusesAreUnavailable() = runTest {
        assertNull(fetcher(Script { respond("<html>", headers = headersOf(HttpHeaders.ContentType, "text/html")) }).fetch(good))
        assertNull(fetcher(Script { respond(bytes(10), HttpStatusCode.NotFound, img) }).fetch(good))
        assertNull(fetcher(Script { respond(bytes(10), HttpStatusCode.InternalServerError, img) }).fetch(good))
        assertNull(fetcher(Script { respond(ByteArray(0), HttpStatusCode.OK, img) }).fetch(good))
    }

    // ---- redirects ---------------------------------------------------------------------------------------------------

    @Test fun anHttpsToHttpsRedirectIsFollowedAcrossHosts() = runTest {
        val s = Script { req -> if (req.url.host == "media.example.test") redirect("https://cdn.example.test/a.jpg") else respond(bytes(10), headers = img) }
        assertEquals(10, fetcher(s).fetch(good)!!.size)
        assertEquals(listOf(good, "https://cdn.example.test/a.jpg"), s.requested)
    }

    @Test fun aRelativeRedirectResolvesAgainstTheCurrentUrlAndStaysHttps() = runTest {
        val s = Script { req -> if (req.url.encodedPath == "/a.jpg") redirect("/v2/a.jpg", HttpStatusCode.MovedPermanently) else respond(bytes(10), headers = img) }
        assertEquals(10, fetcher(s).fetch(good)!!.size)
        assertEquals("https://media.example.test/v2/a.jpg", s.requested[1])
        assertEquals("https://media.example.test/v2/a.jpg", KtorImageFetcher.resolveRedirect(good, "/v2/a.jpg"))
        assertEquals("https://media.example.test/b.jpg", KtorImageFetcher.resolveRedirect(good, "b.jpg"))
    }

    @Test fun anHttpsToHttpRedirectIsRejectedBeforeAnythingInsecureIsFetched() = runTest {
        val s = Script { req -> if (req.url.protocol.name == "https") redirect("http://media.example.test/a.jpg") else respond(bytes(10), headers = img) }
        assertNull(fetcher(s).fetch(good))
        assertEquals(listOf(good), s.requested)                                   // the http target was never requested
        assertNull(fetcher(Script { redirect("http://cdn.example.test/a.jpg", HttpStatusCode.TemporaryRedirect) }).fetch(good))
    }

    @Test fun aMalformedLocationIsRejected() = runTest {
        for (loc in listOf("", "   ", "https://bad host/a.jpg", "https://x\n.jpg", "ftp://media.example.test/a.jpg")) {
            assertNull(fetcher(Script { redirect(loc) }).fetch(good), loc)
        }
        assertNull(fetcher(Script { respond("", HttpStatusCode.Found) }).fetch(good))   // no Location at all
        assertNull(KtorImageFetcher.resolveRedirect(good, null))
        assertNull(KtorImageFetcher.resolveRedirect(good, "https://x y"))
    }

    @Test fun anExcessiveRedirectChainIsRejected() = runTest {
        val s = Script { req ->
            val n = req.url.encodedPath.removePrefix("/").removeSuffix(".jpg").toIntOrNull() ?: 0
            if (n < KtorImageFetcher.MAX_REDIRECTS + 3) redirect("https://media.example.test/${n + 1}.jpg") else respond(bytes(10), headers = img)
        }
        assertNull(fetcher(s).fetch("https://media.example.test/0.jpg"))
        assertEquals(KtorImageFetcher.MAX_REDIRECTS + 1, s.requested.size)

        // Exactly MAX_REDIRECTS hops is still fine.
        val ok = Script { req ->
            val n = req.url.encodedPath.removePrefix("/").removeSuffix(".jpg").toIntOrNull() ?: 0
            if (n < KtorImageFetcher.MAX_REDIRECTS) redirect("https://media.example.test/${n + 1}.jpg") else respond(bytes(10), headers = img)
        }
        assertEquals(10, fetcher(ok).fetch("https://media.example.test/0.jpg")!!.size)
    }

    @Test fun aRedirectLoopIsRejected() = runTest {
        val s = Script { req -> if (req.url.encodedPath == "/a.jpg") redirect("https://media.example.test/b.jpg") else redirect("https://media.example.test/a.jpg") }
        assertNull(fetcher(s).fetch(good))
        assertEquals(2, s.requested.size)
        assertNull(fetcher(Script { redirect(good) }).fetch(good))                 // self-redirect
    }

    // ---- cancellation and caching through the loader ------------------------------------------------------------------

    private class FakeBitmap(override val width: Int, override val height: Int) : ImageBitmap {
        override val config: ImageBitmapConfig get() = ImageBitmapConfig.Argb8888
        override val hasAlpha: Boolean get() = true
        override val colorSpace: ColorSpace get() = ColorSpaces.Srgb
        override fun prepareToDraw() {}
        override fun readPixels(buffer: IntArray, startX: Int, startY: Int, width: Int, height: Int, bufferOffset: Int, stride: Int) {}
    }

    private fun loader(f: ImageFetcher) = RemoteImageLoader(f, decode = { b, _ -> FakeBitmap(b.size, 1) }, maxDimension = 512)

    @Test fun cancellationFromTheCallerPropagatesAndLeavesNothingInFlight() = runTest {
        val gate = CompletableDeferred<Unit>()
        val s = Script { gate.await(); respond(bytes(10), headers = img) }
        val l = loader(fetcher(s))
        val job = async { l.load(good) }
        yield(); yield()
        job.cancel()
        assertFailsWith<CancellationException> { job.await() }
        gate.complete(Unit)
        assertIs<ProductImageState.Ready>(l.load(good))                            // a fresh load is not stuck behind the cancelled one
    }

    @Test fun oversizeAndFailedResponsesAreNeverCachedAndAreRetriedNextTime() = runTest {
        var calls = 0
        val s = Script { calls++; if (calls == 1) respond(ByteReadChannel(bytes(limit * 2)), HttpStatusCode.OK, img) else respond(bytes(10), headers = img) }
        val l = loader(fetcher(s))
        assertEquals(ProductImageState.Unavailable, l.load(good))
        assertTrue(l.cachedUrls.isEmpty())
        assertIs<ProductImageState.Ready>(l.load(good))
        assertEquals(2, calls)
        assertEquals(setOf(good), l.cachedUrls)
    }
}
