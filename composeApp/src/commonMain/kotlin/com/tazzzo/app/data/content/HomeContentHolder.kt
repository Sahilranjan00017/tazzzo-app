package com.tazzzo.app.data.content

import com.tazzzo.app.data.catalog.CatalogFailure
import com.tazzzo.app.data.catalog.CatalogNode
import com.tazzzo.app.data.catalog.CatalogProduct
import com.tazzzo.app.data.catalog.CatalogProductDetail
import com.tazzzo.app.data.catalog.PagedState
import com.tazzzo.app.data.catalog.Pincode
import com.tazzzo.app.data.catalog.RetryPolicy
import com.tazzzo.app.data.catalog.toCatalogFailure
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlin.time.Clock
import kotlin.time.ExperimentalTime

sealed interface HomeContentState {
    data object Idle : HomeContentState
    data object Loading : HomeContentState
    data class Content(val content: HomeContent) : HomeContentState
    /** Kept for diagnostics; the Home screen shows NOTHING for it — the catalogue sections stand on their own. */
    data class Failed(val failure: CatalogFailure) : HomeContentState
}

/**
 * Owns the published Home for the process.
 *
 * **Threading.** Every mutable field is confined to [scope], which MUST be single-threaded (the app gives it
 * `Dispatchers.Default.limitedParallelism(1)`, like the cart store; tests give it a test dispatcher). The public entry
 * points never touch that state on the caller's thread: they post a command into [scope].
 *
 * **When it re-reads.** One fetch at a time. [ensure] (Home shown, app back in the foreground) reads when nothing was
 * read yet, when the last success is older than [freshMs] (the backend advertises `max-age=60`), or after a failure once
 * its backoff has passed: [retryBaseMs], doubling per consecutive failure, capped at [freshMs], and never sooner than a
 * 429's `Retry-After` (itself capped at [RetryPolicy.MAX_RATE_LIMIT_WAIT_SECONDS], as everywhere in the app).
 * [refresh] (pull-to-refresh) reads now whatever the age — except inside a 429 window, which is announced on
 * [pullRefused] — and re-reads every rail card older than [pullCardMinAgeMs], so repeated pulls cost at most one full
 * card sweep per [pullCardMinAgeMs]. A stale copy stays on screen while it is re-read and survives a failed re-read.
 *
 * **Rails.** Cards come from the same per-product read the PDP uses (`GET /v1/products/{id}`, PIN-aware, 404 = not
 * shown) with bounded parallelism, through a per-PIN card cache: a re-read keeps every rail on screen, renders ids it
 * already knows at once and fetches only ids it has not got, or whose card is older than [cardFreshMs] (so a price
 * cannot stay stale for the life of the process). A PIN change drops the cache (prices and serviceability are
 * PIN-aware) and shows the rails loading again — old cards would describe the wrong PIN. A rail whose products are all
 * missing is simply absent; nothing is filled in.
 *
 * **Grids.** Published node ids are named through [resolveNodes] (see [CategoryNodeResolver]); an id that cannot be
 * named is a skipped tile, never a skipped grid. Grids on screen stay while a re-read resolves again, and a node named
 * before keeps its name if a later resolution could not read its branch.
 */
@OptIn(ExperimentalTime::class)
class HomeContentHolder(
    private val scope: CoroutineScope,
    private val load: suspend () -> HomeContent,
    private val product: suspend (productId: String, pin: Pincode?) -> CatalogProductDetail?,
    private val pin: StateFlow<Pincode>,
    private val nowMs: () -> Long = { Clock.System.now().toEpochMilliseconds() },
    private val freshMs: Long = 60_000L,
    private val parallelism: Int = 4,
    private val resolveNodes: suspend (Collection<String>) -> Map<String, CatalogNode> = { emptyMap() },
    private val retryBaseMs: Long = 10_000L,
    private val cardFreshMs: Long = 300_000L,
    private val pullCardMinAgeMs: Long = 30_000L
) {
    private val _state = MutableStateFlow<HomeContentState>(HomeContentState.Idle)
    val state: StateFlow<HomeContentState> = _state

    private val _rails = MutableStateFlow<Map<String, PagedState<CatalogProduct>>>(emptyMap())
    /** By rail block id. Absent = not requested yet. */
    val rails: StateFlow<Map<String, PagedState<CatalogProduct>>> = _rails

    private val _grids = MutableStateFlow<Map<String, List<CatalogNode>>>(emptyMap())
    /** By grid block id: the published nodes that could be named, in published order. Absent = not resolved yet. */
    val grids: StateFlow<Map<String, List<CatalogNode>>> = _grids

    private val _refreshing = MutableStateFlow(false)
    /** True while a customer's [refresh] is being answered (drives the pull-to-refresh indicator, nothing else). */
    val refreshing: StateFlow<Boolean> = _refreshing

    private val _pullRefused = MutableSharedFlow<CatalogFailure>(extraBufferCapacity = 1)
    /** A pull that sent nothing because the backend asked us to wait (429): the screen tells the customer so. */
    val pullRefused: SharedFlow<CatalogFailure> = _pullRefused

    // ---- confined to [scope] ----
    private var loading = false
    private var customerWaiting = false
    private var succeededAtMs = 0L          // 0 = never
    private var failures = 0                // consecutive
    private var retryNotBeforeMs = 0L
    private var rateLimitedUntilMs = 0L

    private class Card(val product: CatalogProduct?, val atMs: Long)
    private val cards = HashMap<String, Card>()
    private var cardsPin: Pincode? = null
    private var railJob: Job? = null
    private var railGen = 0
    private val nodeNames = HashMap<String, CatalogNode>()
    private var gridJob: Job? = null

    /** Read if due (see the class comment); a no-op while loading, while fresh, or inside a failure's backoff. */
    fun ensure() = command { if (!loading && due(nowMs())) start() }

    /** Pull-to-refresh: read now and re-read every card; joins a read already in flight; nothing inside a 429 window. */
    fun refresh() = command {
        if (nowMs() < rateLimitedUntilMs) { _pullRefused.tryEmit(CatalogFailure.RateLimited(null)); return@command }
        customerWaiting = true
        _refreshing.value = true
        if (!loading) start()
    }

    /** The PIN changed: cards are PIN-aware (price, serviceability), so the rails are reloaded for the current content. */
    fun onPinChanged() = command {
        val s = _state.value as? HomeContentState.Content ?: return@command
        if (pin.value != cardsPin) loadRails(s.content, refetchAll = false)
    }

    private fun command(block: () -> Unit) { scope.launch { block() } }

    private fun due(now: Long): Boolean = when {
        failures > 0 -> now >= retryNotBeforeMs
        succeededAtMs == 0L -> true
        else -> now - succeededAtMs >= freshMs
    }

    private fun start() {
        loading = true
        // a good copy stays on screen while it is re-read; only a first load shows Loading
        if (_state.value !is HomeContentState.Content) _state.value = HomeContentState.Loading
        scope.launch {
            val result: Result<HomeContent> = try {
                Result.success(load())
            } catch (e: CancellationException) {
                throw e
            } catch (e: Throwable) {
                Result.failure(e)
            }
            val now = nowMs()
            val forCustomer = customerWaiting
            loading = false
            customerWaiting = false
            _refreshing.value = false
            result.onSuccess { content ->
                succeededAtMs = now
                failures = 0
                rateLimitedUntilMs = 0L
                _state.value = HomeContentState.Content(content)
                loadRails(content, refetchAll = forCustomer)
                resolveGrids(content)
            }.onFailure { e ->
                val failure = e.toCatalogFailure()
                failures++
                val backoff = minOf(retryBaseMs shl minOf(failures - 1, 16), freshMs)
                // bounded like RetryPolicy: a hostile or broken Retry-After can neither park the Home for hours nor overflow
                val retryAfterMs = (failure as? CatalogFailure.RateLimited)?.retryAfterSeconds
                    ?.coerceIn(0L, RetryPolicy.MAX_RATE_LIMIT_WAIT_SECONDS.toLong())?.times(1000) ?: 0L
                rateLimitedUntilMs = if (failure is CatalogFailure.RateLimited) now + maxOf(retryAfterMs, retryBaseMs) else 0L
                retryNotBeforeMs = now + maxOf(backoff, retryAfterMs)
                // a stale copy is kept over a failed re-read; a first load records the failure (and shows nothing)
                if (_state.value !is HomeContentState.Content) _state.value = HomeContentState.Failed(failure)
            }
        }
    }

    /**
     * Shows every rail of [content] at once from what is already known, then reads what is not: unknown ids, and cards
     * older than [cardFreshMs] — or older than [pullCardMinAgeMs] for a customer's pull ([refetchAll]). Each card is kept
     * as it arrives, so a pull that supersedes a sweep in flight does not read the finished cards again.
     */
    private fun loadRails(content: HomeContent, refetchAll: Boolean) {
        railJob?.cancel()
        val gen = ++railGen
        val currentPin = pin.value
        val pinChanged = cardsPin != currentPin
        if (pinChanged) { cards.clear(); cardsPin = currentPin }
        val rails = content.blocks.filterIsInstance<HomeBlock.ProductRail>()
        cards.keys.retainAll(rails.flatMapTo(HashSet()) { it.productIds })
        val startedAt = nowMs()
        val maxAge = if (refetchAll) pullCardMinAgeMs else cardFreshMs
        fun needsRead(id: String): Boolean = cards[id].let { it == null || startedAt - it.atMs >= maxAge }

        val previous = _rails.value
        _rails.value = rails.associate { r ->
            r.blockId to when {
                r.productIds.all { it in cards } -> verdict(r)                                            // known: show now
                !pinChanged && previous[r.blockId] is PagedState.Content -> previous.getValue(r.blockId)   // keep while reading
                else -> PagedState.LoadingFirst
            }
        }
        if (rails.none { r -> r.productIds.any(::needsRead) }) return
        railJob = scope.launch {
            val gate = Semaphore(parallelism)
            val read = HashSet<String>()
            for (rail in rails) {
                val ids = rail.productIds.filter { it !in read && needsRead(it) }
                val got: List<Pair<String, CatalogProduct?>>? = if (ids.isEmpty()) emptyList() else try {
                    coroutineScope {
                        ids.map { id ->
                            async {
                                gate.withPermit {
                                    val p = product(id, currentPin)?.product
                                    if (gen == railGen) cards[id] = Card(p, nowMs())    // a superseded loader never writes
                                    id to p
                                }
                            }
                        }.awaitAll()
                    }
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Throwable) {
                    null
                }
                if (gen != railGen) return@launch          // superseded (PIN change or a newer content load)
                val shown = _rails.value[rail.blockId]
                val v = if (got == null) {
                    // a failed re-read keeps the rail already on screen; with nothing on screen the rail fails (not the Home)
                    if (shown is PagedState.Content) shown else PagedState.FirstPageFailed(CatalogFailure.Unknown)
                } else {
                    read += ids
                    verdict(rail)
                }
                _rails.value = _rails.value + (rail.blockId to v)
            }
        }
    }

    private fun verdict(rail: HomeBlock.ProductRail): PagedState<CatalogProduct> {
        val items = rail.productIds.mapNotNull { cards[it]?.product }
        return if (items.isEmpty()) PagedState.Empty else PagedState.Content(items, hasMore = false)
    }

    private fun resolveGrids(content: HomeContent) {
        gridJob?.cancel()
        val grids = content.blocks.filterIsInstance<HomeBlock.CategoryGrid>()
        val wanted = grids.flatMap { it.nodeIds }.distinct()
        nodeNames.keys.retainAll(wanted.toSet())
        if (grids.isEmpty()) { _grids.value = emptyMap(); return }
        gridJob = scope.launch {
            val named = try {
                resolveNodes(wanted)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Throwable) {
                emptyMap()
            }
            nodeNames.putAll(named)
            _grids.value = grids.associate { g -> g.blockId to g.nodeIds.mapNotNull { nodeNames[it] } }
        }
    }
}
