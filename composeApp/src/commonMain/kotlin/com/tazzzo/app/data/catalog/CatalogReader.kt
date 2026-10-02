package com.tazzzo.app.data.catalog

import com.tazzzo.app.data.remote.ApiError
import com.tazzzo.app.data.remote.ApiException
import com.tazzzo.app.data.remote.Conditional
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlin.time.Clock
import kotlin.time.ExperimentalTime

/**
 * Process-lifetime taxonomy cache with ETag revalidation.
 *
 * Root `GET /v1/categories` is expensive against the rate limiter (it costs the
 * sum of descendant verticals), so:
 *  - a copy younger than [freshMs] (the backend advertises `max-age=300`) is served with NO request;
 *  - an older copy is revalidated with `If-None-Match`; a `304` keeps it and refreshes its age;
 *  - one in-flight fetch at a time, so concurrent screens share a single request.
 * Memory only, deliberately: a disk cache is not needed for 04A. Errors are NOT
 * masked with a stale copy.
 */
@OptIn(ExperimentalTime::class)
class TaxonomyCache(
    private val source: RemoteCatalogDataSource,
    private val nowMs: () -> Long = { Clock.System.now().toEpochMilliseconds() },
    private val freshMs: Long = 300_000L
) {
    private class Entry(val page: TaxonomyPage, val etag: String?, val fetchedAtMs: Long)

    private val lock = Mutex()
    private val entries = HashMap<String, Entry>()
    private val names = HashMap<String, String>()

    /** The name of any node this process has loaded, so a screen can title itself without another request. */
    fun nameOf(nodeId: String): String? = names[nodeId]

    suspend fun categories(): TaxonomyPage = load(ROOT) { etag -> source.categories(etag) }

    suspend fun children(nodeId: String): TaxonomyPage = load(nodeId) { etag -> source.children(nodeId, etag) }

    /** The cached page without any network access, if there is one. */
    fun peek(key: String = ROOT): TaxonomyPage? = entries[key]?.page

    fun invalidate() { entries.clear() }

    private suspend fun load(key: String, fetch: suspend (String?) -> Conditional<TaxonomyPage>): TaxonomyPage =
        lock.withLock {
            val cached = entries[key]
            if (cached != null && nowMs() - cached.fetchedAtMs < freshMs) return cached.page
            when (val r = fetch(cached?.etag)) {
                is Conditional.NotModified -> {
                    // 304 only makes sense with a cached copy to keep.
                    val kept = cached ?: throw ApiException(ApiError.Decoding())
                    entries[key] = Entry(kept.page, r.etag ?: kept.etag, nowMs())
                    kept.page.items.forEach { names[it.id] = it.name }
                    kept.page
                }
                is Conditional.Modified -> {
                    entries[key] = Entry(r.response.body, r.response.etag, nowMs())
                    r.response.body.items.forEach { names[it.id] = it.name }
                    r.response.body
                }
            }
        }

    private companion object { const val ROOT = "" }
}

/**
 * What a future screen reads. Taxonomy comes from [TaxonomyCache]; product reads
 * are never cached here. A category with nothing to list is a `404` on the
 * backend, which this layer turns into an empty page — every OTHER failure
 * propagates, and nothing ever falls back to mock data.
 */
class CatalogReader(
    private val source: RemoteCatalogDataSource,
    val taxonomy: TaxonomyCache
) {
    suspend fun categories(): TaxonomyPage = taxonomy.categories()
    suspend fun children(nodeId: String): TaxonomyPage = taxonomy.children(nodeId)

    suspend fun productPage(nodeId: String, pin: Pincode?, cursor: String?, pageSize: Int = RemoteCatalogDataSource.DEFAULT_PAGE_SIZE): ProductPage =
        try {
            source.products(nodeId, pin, cursor, pageSize)
        } catch (e: ApiException) {
            // 404 on a list = unknown, unreachable or consumer-empty node: "nothing here".
            // (A 404 with a cursor would be unexpected; it is treated the same, not retried.)
            if ((e.error as? ApiError.Http)?.status == 404) ProductPage.empty() else throw e
        }

    /** The PDP; `null` when the product is unknown or not visible (404). */
    suspend fun product(productId: String, pin: Pincode?): CatalogProductDetail? =
        try {
            source.product(productId, pin)
        } catch (e: ApiException) {
            if ((e.error as? ApiError.Http)?.status == 404) null else throw e
        }
}
