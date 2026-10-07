package com.tazzzo.app.data.content

import com.tazzzo.app.data.catalog.CatalogFailure
import com.tazzzo.app.data.catalog.CatalogProduct
import com.tazzzo.app.data.catalog.CatalogProductDetail
import com.tazzzo.app.data.catalog.PagedState
import com.tazzzo.app.data.catalog.Pincode
import com.tazzzo.app.data.catalog.toCatalogFailure
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withLock
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
 * Owns the published Home for the process: one fetch at a time; a copy younger than [freshMs] (the backend advertises
 * `max-age=60`) is reused without a request, and so is a FAILURE younger than [freshMs] — a backend that cannot serve
 * the Home is not asked again on every visit to the tab. A stale copy stays on screen while it is re-read, and survives
 * a failed re-read (it was published content; the server's own cache would serve it for as long). Every PRODUCT_RAIL's
 * cards are loaded through the same per-product read the PDP uses (`GET /v1/products/{id}`, PIN-aware, 404 = not
 * shown) with bounded parallelism; a rail whose products are all missing is simply absent; nothing is filled in.
 * Rail loading is serialised ([railLock]) so a PIN change and a content load can never run two loaders at once.
 */
@OptIn(ExperimentalTime::class)
class HomeContentHolder(
    private val scope: CoroutineScope,
    private val load: suspend () -> HomeContent,
    private val product: suspend (productId: String, pin: Pincode?) -> CatalogProductDetail?,
    private val pin: StateFlow<Pincode>,
    private val nowMs: () -> Long = { Clock.System.now().toEpochMilliseconds() },
    private val freshMs: Long = 60_000L,
    private val parallelism: Int = 4
) {
    private val _state = MutableStateFlow<HomeContentState>(HomeContentState.Idle)
    val state: StateFlow<HomeContentState> = _state

    private val _rails = MutableStateFlow<Map<String, PagedState<CatalogProduct>>>(emptyMap())
    /** By rail block id. Absent = not requested yet. */
    val rails: StateFlow<Map<String, PagedState<CatalogProduct>>> = _rails

    /** When the last attempt (success or failure) finished; 0 = never. */
    private var attemptedAtMs = 0L
    private var loading = false
    private var job: Job? = null
    private val railLock = Mutex()
    private var railJob: Job? = null
    private var railPin: Pincode? = null
    private var railContent: HomeContent? = null

    /** Load once; a no-op while loading or while the last attempt — a copy OR a failure — is younger than [freshMs]. */
    fun ensure() {
        if (loading) return
        if (attemptedAtMs != 0L && nowMs() - attemptedAtMs < freshMs) return
        start()
    }

    /** Try again now, whatever the age of the last attempt; never cancels a load in flight. */
    fun refresh() {
        if (!loading) start()
    }

    private fun start() {
        loading = true
        // a good copy stays on screen while it is re-read; only a first load shows Loading
        if (_state.value !is HomeContentState.Content) _state.value = HomeContentState.Loading
        job = scope.launch {
            try {
                val content = load()
                attemptedAtMs = nowMs()
                loading = false
                _state.value = HomeContentState.Content(content)
                loadRails(content)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Throwable) {
                attemptedAtMs = nowMs()
                loading = false
                // a stale copy is kept over a failed re-read; a first load records the failure (and shows nothing)
                if (_state.value !is HomeContentState.Content) _state.value = HomeContentState.Failed(e.toCatalogFailure())
            }
        }
    }

    /** (Re)load every rail of [content] for the current PIN. Serialised: the lock holds while the previous loader is replaced. */
    private fun loadRails(content: HomeContent) {
        scope.launch {
            railLock.withLock {
                railJob?.cancel()
                val rails = content.blocks.filterIsInstance<HomeBlock.ProductRail>()
                val currentPin = pin.value
                railPin = currentPin
                railContent = content
                _rails.value = rails.associate { it.blockId to PagedState.LoadingFirst }
                if (rails.isEmpty()) return@withLock
                railJob = scope.launch {
                    val gate = Semaphore(parallelism)
                    for (rail in rails) {
                        val items: List<CatalogProduct>? = try {
                            coroutineScope {
                                rail.productIds.map { id -> async { gate.withPermit { runCatchingProduct(id, currentPin) } } }.awaitAll()
                            }.mapNotNull { it }
                        } catch (e: CancellationException) {
                            throw e
                        } catch (e: Throwable) {
                            null
                        }
                        val verdict = when {
                            items == null -> PagedState.FirstPageFailed(CatalogFailure.Unknown)
                            items.isEmpty() -> PagedState.Empty
                            else -> PagedState.Content(items, hasMore = false)
                        }
                        railLock.withLock {
                            // only the current loader writes: a superseded one (PIN changed meanwhile) is cancelled, but never races
                            if (railPin == currentPin && railContent === content) _rails.value = _rails.value + (rail.blockId to verdict)
                        }
                    }
                }
            }
        }
    }

    /** One card, or null when the product is unknown/not visible (404). Any other failure propagates and fails the rail. */
    private suspend fun runCatchingProduct(id: String, pin: Pincode?): CatalogProduct? = product(id, pin)?.product

    /** The PIN changed: cards are PIN-aware (price, serviceability), so the rails are reloaded for the current content. */
    fun onPinChanged() {
        val s = _state.value as? HomeContentState.Content ?: return
        if (pin.value != railPin) loadRails(s.content)
    }
}
