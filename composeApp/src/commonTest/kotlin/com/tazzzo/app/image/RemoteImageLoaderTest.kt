package com.tazzzo.app.image

import androidx.compose.ui.graphics.colorspace.ColorSpace
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.ImageBitmapConfig
import androidx.compose.ui.graphics.colorspace.ColorSpaces
import com.tazzzo.app.ui.common.ProductImageState
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.async
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertSame
import kotlin.test.assertTrue

/** UI-03: the production image pipeline, exercised without a network or a platform decoder. */
class RemoteImageLoaderTest {

    /** A stand-in bitmap: the loader only reads width/height for its cache accounting. */
    private class FakeBitmap(override val width: Int, override val height: Int) : ImageBitmap {
        override val config: ImageBitmapConfig get() = ImageBitmapConfig.Argb8888
        override val hasAlpha: Boolean get() = true
        override val colorSpace: ColorSpace get() = ColorSpaces.Srgb
        override fun prepareToDraw() {}
        override fun readPixels(buffer: IntArray, startX: Int, startY: Int, width: Int, height: Int, bufferOffset: Int, stride: Int) {}
    }

    private class Fetcher : ImageFetcher {
        val calls = mutableListOf<String>()
        val answers = HashMap<String, ByteArray?>()
        var gate: CompletableDeferred<Unit>? = null
        override suspend fun fetch(url: String): ByteArray? { calls += url; gate?.await(); return answers[url] }
    }

    private fun loader(f: Fetcher, maxCacheBytes: Long = 1L shl 30, decode: (ByteArray, Int) -> ImageBitmap? = { b, _ -> if (b.isEmpty()) null else FakeBitmap(b[0].toInt(), b[0].toInt()) }) =
        RemoteImageLoader(f, decode = decode, maxDimension = 512, maxCacheBytes = maxCacheBytes)

    private val good = "https://media.example.test/a.jpg"

    @Test fun aGoodUrlIsFetchedDecodedAndReady() = runTest {
        val f = Fetcher().apply { answers[good] = byteArrayOf(10) }
        val s = loader(f).load(good)
        assertIs<ProductImageState.Ready>(s)
        assertEquals(10, s.bitmap.width)
        assertEquals(listOf(good), f.calls)
    }

    @Test fun nullBlankHttpAndMalformedUrlsAreUnavailableWithoutARequest() = runTest {
        val f = Fetcher()
        val l = loader(f)
        for (u in listOf("", "   ", "http://media.example.test/a.jpg", "https://", "https://x y.jpg", "ftp://a/b", "https://a\n.jpg")) {
            assertEquals(ProductImageState.Unavailable, l.load(u), u)
        }
        assertTrue(f.calls.isEmpty())
        assertFalse(RemoteImageLoader.isAcceptableUrl(null))
        assertFalse(RemoteImageLoader.isAcceptableUrl("https://" + "a".repeat(RemoteImageLoader.MAX_URL_LENGTH)))
        assertTrue(RemoteImageLoader.isAcceptableUrl(good))
    }

    @Test fun aFailedFetchIsUnavailableAndIsRetriedNextTime() = runTest {
        val f = Fetcher()                                  // no answer = failed request
        val l = loader(f)
        assertEquals(ProductImageState.Unavailable, l.load(good))
        f.answers[good] = byteArrayOf(8)
        assertIs<ProductImageState.Ready>(l.load(good))    // not cached as failed
        assertEquals(2, f.calls.size)
    }

    @Test fun anUndecodableBodyIsUnavailableNotACrash() = runTest {
        val f = Fetcher().apply { answers[good] = byteArrayOf(1, 2, 3) }
        val l = loader(f, decode = { _, _ -> null })
        assertEquals(ProductImageState.Unavailable, l.load(good))
        val throwing = loader(f, decode = { _, _ -> error("corrupt") })
        assertEquals(ProductImageState.Unavailable, throwing.load(good))
    }

    @Test fun aSecondLoadOfTheSameUrlIsACacheHit() = runTest {
        val f = Fetcher().apply { answers[good] = byteArrayOf(10) }
        val l = loader(f)
        val a = assertIs<ProductImageState.Ready>(l.load(good))
        val b = assertIs<ProductImageState.Ready>(l.load(good))
        assertSame(a.bitmap, b.bitmap)
        assertEquals(1, f.calls.size)
        assertEquals(setOf(good), l.cachedUrls)
    }

    @Test fun concurrentRequestsForOneUrlShareOneFetch() = runTest {
        val f = Fetcher().apply { answers[good] = byteArrayOf(10); gate = CompletableDeferred() }
        val l = loader(f)
        val a = async { l.load(good) }
        val b = async { l.load(good) }
        kotlinx.coroutines.yield(); kotlinx.coroutines.yield()
        f.gate!!.complete(Unit)
        assertIs<ProductImageState.Ready>(a.await()); assertIs<ProductImageState.Ready>(b.await())
        assertEquals(1, f.calls.size)
    }

    @Test fun theCacheIsBoundedAndEvictsLeastRecentlyUsed() = runTest {
        val u = (1..3).map { "https://media.example.test/$it.jpg" }
        val f = Fetcher().apply { u.forEach { answers[it] = byteArrayOf(10) } }   // 10×10×4 = 400 bytes each
        val l = loader(f, maxCacheBytes = 900)
        l.load(u[0]); l.load(u[1])
        l.load(u[0])                                       // touch 1 → 2 is now least recent
        l.load(u[2])                                       // over budget: evict 2
        assertEquals(setOf(u[0], u[2]), l.cachedUrls)
        l.load(u[1])
        assertEquals(2, f.calls.count { it == u[1] })
    }

    @Test fun oneImageLargerThanTheWholeBudgetIsServedButNeverCached() = runTest {
        val f = Fetcher().apply { answers[good] = byteArrayOf(100) }   // 40 KB
        val l = loader(f, maxCacheBytes = 1_000)
        assertIs<ProductImageState.Ready>(l.load(good))
        assertTrue(l.cachedUrls.isEmpty())
    }
}
