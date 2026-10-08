package com.tazzzo.app.data.content

import com.tazzzo.app.data.catalog.CatalogNode
import com.tazzzo.app.data.catalog.TaxonomyPage
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlin.time.Clock
import kotlin.time.ExperimentalTime

/**
 * Names the taxonomy nodes a published CATEGORY_GRID lists. The public API has no "node by id" read: a node's name is only
 * in its parent's list (`GET /v1/categories` for the TZS sections, `GET /v1/categories/{id}/children` below them). So a
 * grid id is resolved by walking DOWN from the root, level by level, only as deep as the deepest id still unresolved
 * (TZS → TZC → TZG → TZV, the backend's fixed four levels), stopping as soon as every id is named.
 *
 * Cost is the reason for the bounds. Each children read is admission-charged by the size of its subtree, so the walk is
 * sequential (never a fan-out) and makes at most [maxRequests] children reads per call. With the reads going through the
 * process taxonomy cache (fresh for 300 s), a repeated resolution of FOUND ids costs nothing. The first failed read
 * (a 429 above all) ends the walk at once: the rest of the budget is not spent against a backend that is refusing or
 * failing. Ids the walk could not name are remembered as unresolved — for [missTtlMs] after a complete walk or an
 * exhausted budget, for [failureTtlMs] after a failed read — and are not walked for again until then, so re-reading the
 * Home does not repeat an expensive, futile walk. The grid simply skips their tiles. Today's taxonomy (7 TZS, ~50 TZC,
 * ~108 TZG) means TZS and TZC ids always resolve; TZG/TZV ids resolve only when found within the budget.
 */
@OptIn(ExperimentalTime::class)
class CategoryNodeResolver(
    private val root: suspend () -> TaxonomyPage,
    private val children: suspend (nodeId: String) -> TaxonomyPage,
    private val maxRequests: Int = DEFAULT_MAX_REQUESTS,
    private val nowMs: () -> Long = { Clock.System.now().toEpochMilliseconds() },
    private val missTtlMs: Long = 300_000L,
    private val failureTtlMs: Long = 60_000L
) {
    private val lock = Mutex()
    /** Id → time until which it is not walked for again. Guarded by [lock]. */
    private val unresolvedUntil = HashMap<String, Long>()

    /** The nodes found for [ids], by id. Never throws for a failed read (the affected ids stay unresolved). */
    suspend fun resolve(ids: Collection<String>): Map<String, CatalogNode> = lock.withLock {
        val now = nowMs()
        unresolvedUntil.entries.removeAll { it.value <= now }
        val wanted = ids.filter { levelOf(it) > 0 && it !in unresolvedUntil }.toSet()
        val walk = walk(wanted)
        val left = wanted - walk.found.keys
        val until = nowMs() + if (walk.failed) failureTtlMs else missTtlMs
        for (id in left) unresolvedUntil[id] = until
        walk.found
    }

    private class Walk(val found: Map<String, CatalogNode>, val failed: Boolean)

    private suspend fun walk(ids: Set<String>): Walk {
        val remaining = ids.toMutableSet()
        val found = LinkedHashMap<String, CatalogNode>()
        if (remaining.isEmpty()) return Walk(found, failed = false)
        var frontier = readOrNull { root() }?.items ?: return Walk(found, failed = true)
        var level = 1
        var requests = 0
        while (true) {
            for (n in frontier) if (remaining.remove(n.id)) found[n.id] = n
            if (remaining.isEmpty() || remaining.none { levelOf(it) > level }) return Walk(found, failed = false)
            val next = ArrayList<CatalogNode>()
            for (n in frontier) {
                if (levelOf(n.id) != level) continue          // only a node of this level has children of the next one
                if (requests >= maxRequests) return Walk(found, failed = false)
                requests++
                // a failed read (429, outage, network) ends the walk: the remaining budget is not spent on a refusing backend
                val page = readOrNull { children(n.id) } ?: return Walk(found, failed = true)
                for (c in page.items) if (remaining.remove(c.id)) found[c.id] = c
                if (remaining.isEmpty()) return Walk(found, failed = false)
                next += page.items
            }
            if (next.isEmpty()) return Walk(found, failed = false)
            frontier = next
            level++
        }
    }

    private suspend fun readOrNull(read: suspend () -> TaxonomyPage): TaxonomyPage? = try {
        read()
    } catch (e: CancellationException) {
        throw e
    } catch (e: Throwable) {
        null
    }

    companion object {
        /** Enough for every TZS section's children (7 today) plus a few TZC expansions. */
        const val DEFAULT_MAX_REQUESTS = 12

        /** 1 = TZS section, 2 = TZC category, 3 = TZG sub-category, 4 = TZV vertical; 0 = not a node id. */
        fun levelOf(id: String): Int = if (!NODE_ID.matches(id)) 0 else when (id[2]) {
            'S' -> 1; 'C' -> 2; 'G' -> 3; 'V' -> 4; else -> 0
        }

        private val NODE_ID = Regex("^TZ[SCGV]-[0-9]{6}$")
    }
}
