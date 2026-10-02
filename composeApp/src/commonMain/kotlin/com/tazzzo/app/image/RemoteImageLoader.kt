package com.tazzzo.app.image

import androidx.compose.ui.graphics.ImageBitmap
import com.tazzzo.app.ui.common.ProductImageLoader
import com.tazzzo.app.ui.common.ProductImageState
import io.ktor.client.HttpClient
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.request.get
import io.ktor.client.statement.bodyAsBytes
import io.ktor.http.HttpHeaders
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

/*
 * The production product-image pipeline (UI-03). One loader, provided at the app root through
 * [com.tazzzo.app.ui.common.LocalProductImageLoader], feeds every product surface: the Home rail, the PLP grid, search
 * results and, later, the PDP. Nothing is logged, nothing is persisted: a URL is fetched, decoded off the main thread
 * at a bounded size, kept in a bounded in-memory LRU, and handed to the composable as an [ImageBitmap].
 *
 * Trust boundary: only `https://` URLs the DTO sanitiser already accepted ([com.tazzzo.app.data.catalog.safeImageUrl])
 * reach here, and this layer re-checks them. A response that is not 2xx, not an image, larger than [maxBytes] or not
 * decodable is "unavailable" — never an exception in a grid, never raw text to a customer.
 */

/** Fetches the raw bytes of an image URL. Separate from the decoder so the loader is unit-tested without a network. */
fun interface ImageFetcher {
    /** The encoded bytes, or null when the request did not produce a usable image. Throws only on cancellation. */
    suspend fun fetch(url: String): ByteArray?
}

/** Ktor-backed fetcher. Its own client: no JSON negotiation, no auth, no base URL — image hosts are not the gateway. */
class KtorImageFetcher(
    private val maxBytes: Int = RemoteImageLoader.DEFAULT_MAX_BYTES,
    private val client: HttpClient = HttpClient {
        expectSuccess = false
        followRedirects = true
        install(HttpTimeout) { connectTimeoutMillis = 10_000; requestTimeoutMillis = 20_000; socketTimeoutMillis = 15_000 }
    }
) : ImageFetcher {
    override suspend fun fetch(url: String): ByteArray? = try {
        val response = client.get(url)
        val declared = response.headers[HttpHeaders.ContentLength]?.toLongOrNull()
        val type = response.headers[HttpHeaders.ContentType]?.lowercase()
        when {
            response.status.value !in 200..299 -> null
            declared != null && declared > maxBytes -> null
            type != null && !type.startsWith("image/") && !type.startsWith("application/octet-stream") -> null
            else -> response.bodyAsBytes().takeIf { it.isNotEmpty() && it.size <= maxBytes }
        }
    } catch (e: CancellationException) {
        throw e
    } catch (e: Throwable) {
        null   // network, timeout, TLS, malformed response: unavailable
    }
}

/**
 * Memory-cached, de-duplicated image loader.
 *
 *  - **URL guard.** Null, blank, non-`https`, whitespace-containing or over-long URLs are refused without a request.
 *  - **Cache.** Decoded bitmaps in an LRU bounded by estimated pixel bytes ([maxCacheBytes]); a hit costs no I/O.
 *  - **In-flight de-dup.** Two cards asking for the same URL at once share one fetch and one decode.
 *  - **Failures are not cached.** A failed load returns [ProductImageState.Unavailable]; the next composition that asks
 *    (a retry, a scroll back) tries again, so a transient network blip does not pin a blank well for the session.
 *  - **Off the main thread.** Decoding runs on [Dispatchers.Default].
 */
class RemoteImageLoader(
    private val fetcher: ImageFetcher,
    private val decode: (ByteArray, Int) -> ImageBitmap? = ::decodeImageBytes,
    private val maxDimension: Int = DEFAULT_MAX_DIMENSION,
    private val maxCacheBytes: Long = DEFAULT_CACHE_BYTES
) : ProductImageLoader {

    private val lock = Mutex()
    private val cache = LinkedHashMap<String, ImageBitmap>()   // insertion order; a hit re-inserts to make it most recent
    private var cacheBytes = 0L
    private val inFlight = HashMap<String, CompletableDeferred<ImageBitmap?>>()

    /** Cached entries, for tests and diagnostics. */
    val cachedUrls: Set<String> get() = cache.keys.toSet()

    override suspend fun load(url: String): ProductImageState {
        if (!isAcceptableUrl(url)) return ProductImageState.Unavailable
        val bitmap = loadBitmap(url) ?: return ProductImageState.Unavailable
        return ProductImageState.Ready(bitmap)
    }

    private suspend fun loadBitmap(url: String): ImageBitmap? {
        val (hit, waitFor, owner) = lock.withLock {
            cache.remove(url)?.let { cache[url] = it; return@withLock Triple(it, null, null) }
            inFlight[url]?.let { return@withLock Triple(null, it, null) }
            val d = CompletableDeferred<ImageBitmap?>()
            inFlight[url] = d
            Triple(null, null, d)
        }
        if (hit != null) return hit
        if (waitFor != null) return waitFor.await()
        val result = try {
            val bytes = fetcher.fetch(url)
            if (bytes == null || bytes.isEmpty()) null
            else withContext(Dispatchers.Default) { decode(bytes, maxDimension) }
        } catch (e: CancellationException) {
            lock.withLock { inFlight.remove(url) }
            owner!!.cancel()
            throw e
        } catch (t: Throwable) {
            null
        }
        lock.withLock {
            inFlight.remove(url)
            if (result != null) put(url, result)
        }
        owner!!.complete(result)
        return result
    }

    private fun put(url: String, bitmap: ImageBitmap) {
        val size = bitmap.width.toLong() * bitmap.height.toLong() * 4L
        if (size > maxCacheBytes) return                         // never let one image empty the cache
        cache.remove(url)?.let { cacheBytes -= it.width.toLong() * it.height.toLong() * 4L }
        cache[url] = bitmap
        cacheBytes += size
        val it = cache.entries.iterator()
        while (cacheBytes > maxCacheBytes && it.hasNext()) {
            val e = it.next()
            cacheBytes -= e.value.width.toLong() * e.value.height.toLong() * 4L
            it.remove()
        }
    }

    companion object {
        const val DEFAULT_MAX_DIMENSION = 1024
        const val DEFAULT_MAX_BYTES = 6 * 1024 * 1024
        const val DEFAULT_CACHE_BYTES = 48L * 1024 * 1024
        const val MAX_URL_LENGTH = 2048

        /** The same rule as the DTO sanitiser, re-applied at the network edge. */
        fun isAcceptableUrl(url: String?): Boolean {
            if (url == null) return false
            val u = url.trim()
            return u.length > "https://".length && u.length <= MAX_URL_LENGTH && u.startsWith("https://") &&
                u.none { it.isWhitespace() || it.isISOControl() }
        }
    }
}

/** The app-wide pipeline instance: one cache shared by every product surface. */
object ProductImagePipeline {
    val loader: RemoteImageLoader by lazy { RemoteImageLoader(KtorImageFetcher()) }
}
