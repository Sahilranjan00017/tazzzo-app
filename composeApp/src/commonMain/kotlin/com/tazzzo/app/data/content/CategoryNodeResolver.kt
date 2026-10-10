package com.tazzzo.app.data.content

import com.tazzzo.app.data.catalog.CatalogFailure
import com.tazzzo.app.data.catalog.CatalogNode
import com.tazzzo.app.data.catalog.RetryPolicy
import com.tazzzo.app.data.catalog.toCatalogFailure
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlin.time.Clock
import kotlin.time.ExperimentalTime

/**
 * Names the taxonomy nodes a published CATEGORY_GRID lists, at any level (TZS / TZC / TZG / TZV), with ONE read per id:
 * `GET /v1/categories/{id}` (backend PR #109) through [node], which answers the node or `null` for a 404 (unknown, hidden
 * or consumer-empty — the backend does not tell them apart, and the app does not need to).
 *
 * Bounds, because every read is admission-charged:
 *  - **Cache.** A named node is served from memory for [hitTtlMs] (the route's `max-age=300`); a 404 is remembered for
 *    [missTtlMs]. A Home re-read inside those windows costs nothing.
 *  - **Sequential, budgeted.** At most [maxRequests] reads per call, one at a time, in published order (never a fan-out).
 *    Ids beyond the budget are not penalised: the next resolution reads them.
 *  - **Failure.** The first failed read ends the call: the failed id and every id not yet read are not read again for
 *    [failureTtlMs]. A 429 additionally stops EVERY read for its `Retry-After` (capped at
 *    [RetryPolicy.MAX_RATE_LIMIT_WAIT_SECONDS], as everywhere in the app), never less than [failureTtlMs].
 *
 * The answer maps an id to its node, or to `null` when the backend said 404 (the holder then drops a tile it showed
 * before). An id absent from the answer is simply not known right now — the holder keeps whatever it showed.
 */
@OptIn(ExperimentalTime::class)
class CategoryNodeResolver(
    private val node: suspend (nodeId: String) -> CatalogNode?,
    private val maxRequests: Int = DEFAULT_MAX_REQUESTS,
    private val nowMs: () -> Long = { Clock.System.now().toEpochMilliseconds() },
    private val hitTtlMs: Long = 300_000L,
    private val missTtlMs: Long = 300_000L,
    private val failureTtlMs: Long = 60_000L
) {
    private class Hit(val node: CatalogNode, val untilMs: Long)

    // ---- guarded by [lock] ----
    private val lock = Mutex()
    private val hits = HashMap<String, Hit>()
    /** 404: not visible; not read again until then. */
    private val goneUntil = HashMap<String, Long>()
    /** A failed read (or an id left unread after one): not read again until then. */
    private val failedUntil = HashMap<String, Long>()
    /** A 429: no read at all until then. */
    private var blockedUntilMs = 0L

    /** Never throws for a failed read; see the class comment for what the answer means. */
    suspend fun resolve(ids: Collection<String>): Map<String, CatalogNode?> = lock.withLock {
        val now = nowMs()
        hits.entries.removeAll { it.value.untilMs <= now }
        goneUntil.entries.removeAll { it.value <= now }
        failedUntil.entries.removeAll { it.value <= now }

        val out = LinkedHashMap<String, CatalogNode?>()
        val toRead = ArrayList<String>()
        for (id in ids.distinct()) {
            if (!NODE_ID.matches(id)) continue
            val hit = hits[id]
            when {
                hit != null -> out[id] = hit.node
                id in goneUntil -> out[id] = null
                id in failedUntil -> Unit
                else -> toRead += id
            }
        }
        if (now < blockedUntilMs) return@withLock out

        for ((i, id) in toRead.withIndex()) {
            if (i >= maxRequests) break
            val read: CatalogNode? = try {
                node(id)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Throwable) {
                val t = nowMs()
                val failure = e.toCatalogFailure()
                val waitMs = if (failure is CatalogFailure.RateLimited) RetryPolicy.waitSeconds(failure, 1) * 1000L else 0L
                val until = t + maxOf(failureTtlMs, waitMs)
                if (failure is CatalogFailure.RateLimited) blockedUntilMs = until
                // the rest of the budget is not spent against a backend that is refusing or failing
                for (left in toRead.subList(i, toRead.size)) failedUntil[left] = until
                break
            }
            val t = nowMs()
            if (read == null) goneUntil[id] = t + missTtlMs else hits[id] = Hit(read, t + hitTtlMs)
            out[id] = read
        }
        out
    }

    companion object {
        /** One full grid ([HomeBlock.MAX_GRID_IDS]) per resolution. */
        const val DEFAULT_MAX_REQUESTS = HomeBlock.MAX_GRID_IDS

        private val NODE_ID = Regex("^TZ[SCGV]-[0-9]{6}$")
    }
}
