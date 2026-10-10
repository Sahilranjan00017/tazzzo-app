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
 * [refresh] (pull-to-refresh) reads now whatever the age — except inside a 429 window (the content call's, which lasts
 * its `Retry-After` but at least [retryBaseMs] = 10 s; or the product reads', below), announced on [pullRefused] — and
 * re-reads every rail card older than [pullCardMinAgeMs], so repeated pulls cost at most one full card sweep per
 * [pullCardMinAgeMs]. A stale copy stays on screen while it is re-read and survives a failed re-read.
 *
 * **Rails.** Cards come from the same per-product read the PDP uses (`GET /v1/products/{id}`, PIN-aware, 404 = not
 * shown) with bounded parallelism, through a per-PIN card cache: a re-read keeps every rail on screen, renders ids it
 * already knows at once and fetches only ids it has not got, or whose card is older than [cardFreshMs] (so a price
 * cannot stay stale for the life of the process). A PIN change drops the cache (prices and serviceability are
 * PIN-aware) and shows the rails loading again — old cards would describe the wrong PIN. A rail whose products are all
 * missing is simply absent; nothing is filled in.
 *
 * **Product-read budget (every card read is admission-charged).** A sweep reads at most [maxCardReads] distinct ids
 * (default 120; the Home can publish 20 blocks x 20 ids), in published order; the rest are read by the next sweep, not
 * penalised, and a rail with nothing to show yet stays loading until then. The first failed read of a sweep ends that
 * batch, and the ids it did not get are not asked again for [cardFailureMs] (60 s, like [CategoryNodeResolver]), so
 * another rail does not re-ask them. A **429** from a product read additionally ends the whole sweep at once and opens
 * ONE window for ALL product reads for its `Retry-After` (capped at [RetryPolicy.MAX_RATE_LIMIT_WAIT_SECONDS]), never
 * under [cardFailureMs]: inside it a pull is refused (announced on [pullRefused]) and a re-read or a PIN change sends no
 * product read at all. Rails that already show cards stay on screen; the others show nothing.
 *
 * **Grids.** Published node ids are named through [resolveNodes] (see [CategoryNodeResolver]); an id that cannot be
 * named is a skipped tile, never a skipped grid. Grids on screen stay while a re-read resolves again; a node named
 * before keeps its name if a later resolution could not read it, and loses its tile once the backend answers 404 for it.
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
    private val resolveNodes: suspend (Collection<String>) -> Map<String, CatalogNode?> = { emptyMap() },
    private val retryBaseMs: Long = 10_000L,
    private val cardFreshMs: Long = 300_000L,
    private val pullCardMinAgeMs: Long = 30_000L,
    private val maxCardReads: Int = DEFAULT_MAX_CARD_READS,
    private val cardFailureMs: Long = 60_000L
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
    private var rateLimitedUntilMs = 0L     // the content call's 429 window
    private var cardsBlockedUntilMs = 0L    // a product read's 429 window: no product read at all until then
    private val cardFailedUntil = HashMap<String, Long>()   // ids whose read failed (or was cut short): not re-asked until then

    private class Card(val product: CatalogProduct?, val atMs: Long)
    private val cards = HashMap<String, Card>()
    private var cardsPin: Pincode? = null
    private var railJob: Job? = null
    private var railGen = 0
    private val nodeNames = HashMap<String, CatalogNode>()
    private var gridJob: Job? = null

    /** Read if due (see the class comment); a no-op while loading, while fresh, or inside a failure's backoff. */
    fun ensure() = command { if (!loading && due(nowMs())) start() }

    /** Pull-to-refresh: read now and re-read every card; joins a read already in flight; nothing inside a 429 window (content or cards). */
    fun refresh() = command {
        if (nowMs() < maxOf(rateLimitedUntilMs, cardsBlockedUntilMs)) { _pullRefused.tryEmit(CatalogFailure.RateLimited(null)); return@command }
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
        cardFailedUntil.entries.removeAll { it.value <= startedAt }
        if (pinChanged) cardFailedUntil.clear()     // a failure for the old PIN says nothing about the new one (a 429 window stays)
        val blocked = startedAt < cardsBlockedUntilMs
        val maxAge = if (refetchAll) pullCardMinAgeMs else cardFreshMs
        fun needsRead(id: String): Boolean = cards[id].let { it == null || startedAt - it.atMs >= maxAge }

        val previous = _rails.value
        _rails.value = rails.associate { r ->
            r.blockId to when {
                r.productIds.all { it in cards } -> verdict(r)                                            // known: show now
                !pinChanged && previous[r.blockId] is PagedState.Content -> previous.getValue(r.blockId)   // keep while reading
                blocked -> PagedState.FirstPageFailed(CatalogFailure.RateLimited(null))                    // shows nothing, not a skeleton
                else -> PagedState.LoadingFirst
            }
        }
        if (blocked || rails.none { r -> r.productIds.any(::needsRead) }) return
        railJob = scope.launch {
            val gate = Semaphore(parallelism)
            val read = HashSet<String>()
            var budget = maxCardReads
            var limited: CatalogFailure? = null     // the 429 that ended this sweep
            for (rail in rails) {
                val wanted = rail.productIds.distinct().filter { it !in read && needsRead(it) }
                val remembered = wanted.any { it in cardFailedUntil }
                val ids = if (limited != null) emptyList() else wanted.filter { it !in cardFailedUntil }.take(budget.coerceAtLeast(0))
                budget -= ids.size
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
                    if (gen == railGen) {
                        val failure = e.toCatalogFailure()
                        val t = nowMs()
                        if (failure is CatalogFailure.RateLimited) {
                            limited = failure
                            cardsBlockedUntilMs = t + maxOf(cardFailureMs, RetryPolicy.waitSeconds(failure, 1) * 1000L)
                        }
                        val until = if (failure is CatalogFailure.RateLimited) cardsBlockedUntilMs else t + cardFailureMs
                        for (id in ids) if (id !in cards) cardFailedUntil[id] = until
                    }
                    null
                }
                if (gen != railGen) return@launch          // superseded (PIN change or a newer content load)
                val shown = _rails.value[rail.blockId]
                val failure: CatalogFailure? = limited ?: if (got == null || remembered) CatalogFailure.Unknown else null
                if (got != null) read += ids
                val v: PagedState<CatalogProduct> = when {
                    rail.productIds.all { it in cards } -> verdict(rail)
                    // a failed read keeps the rail already on screen; with nothing on screen the rail fails (not the Home)
                    failure != null -> if (shown is PagedState.Content) shown else PagedState.FirstPageFailed(failure)
                    // cut short by the budget only: what is known stays up, the rest comes with the next sweep
                    shown is PagedState.Content -> shown
                    else -> rail.productIds.mapNotNull { cards[it]?.product }
                        .let { items -> if (items.isEmpty()) PagedState.LoadingFirst else PagedState.Content(items, hasMore = false) }
                }
                _rails.value = _rails.value + (rail.blockId to v)
            }
        }
    }

    private fun verdict(rail: HomeBlock.ProductRail): PagedState<CatalogProduct> {
        val items = rail.productIds.mapNotNull { cards[it]?.product }
        return if (items.isEmpty()) PagedState.Empty else PagedState.Content(items, hasMore = false)
    }

    companion object {
        /** Distinct product reads per rail sweep: six full rails; the rest wait for the next sweep. */
        const val DEFAULT_MAX_CARD_READS = 120
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
            for ((id, node) in named) if (node == null) nodeNames.remove(id) else nodeNames[id] = node   // null = 404: gone
            _grids.value = grids.associate { g -> g.blockId to g.nodeIds.mapNotNull { nodeNames[it] } }
        }
    }
}
