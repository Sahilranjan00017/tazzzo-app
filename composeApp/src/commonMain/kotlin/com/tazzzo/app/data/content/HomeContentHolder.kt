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
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlin.time.Clock
import kotlin.time.ExperimentalTime

sealed interface HomeContentState {
    data object Idle : HomeContentState
    data object Loading : HomeContentState
    data class Content(val content: HomeContent) : HomeContentState
    /** Kept for diagnostics and retry; the Home screen shows NOTHING for it — the catalogue sections stand on their own. */
    data class Failed(val failure: CatalogFailure) : HomeContentState
}

/**
 * Owns the published Home for the process: one fetch at a time, a copy younger than [freshMs] (the backend advertises
 * `max-age=60`) is reused without a request, and every PRODUCT_RAIL's cards are loaded through the same per-product
 * read the PDP uses (`GET /v1/products/{id}`, PIN-aware, 404 = not shown) with bounded parallelism. A rail whose
 * products are all missing is simply absent; nothing is filled in.
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

    private var fetchedAtMs = 0L
    private var job: Job? = null
    private var railJob: Job? = null
    private var railPin: Pincode? = null

    /** Load once; a no-op while loading or while the last copy is fresh. */
    fun ensure() {
        val s = _state.value
        if (s is HomeContentState.Loading) return
        if (s is HomeContentState.Content && nowMs() - fetchedAtMs < freshMs) return
        start()
    }

    /** Try again after a failure (or after the copy aged); never cancels a load in flight. */
    fun refresh() {
        if (_state.value !is HomeContentState.Loading) start()
    }

    private fun start() {
        _state.value = HomeContentState.Loading
        job = scope.launch {
            try {
                val content = load()
                fetchedAtMs = nowMs()
                _state.value = HomeContentState.Content(content)
                loadRails(content)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Throwable) {
                _state.value = HomeContentState.Failed(e.toCatalogFailure())
            }
        }
    }

    private fun loadRails(content: HomeContent) {
        railJob?.cancel()
        val rails = content.blocks.filterIsInstance<HomeBlock.ProductRail>()
        val currentPin = pin.value
        railPin = currentPin
        _rails.value = rails.associate { it.blockId to PagedState.LoadingFirst }
        if (rails.isEmpty()) return
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
                _rails.value = _rails.value + (rail.blockId to when {
                    items == null -> PagedState.FirstPageFailed(CatalogFailure.Unknown)
                    items.isEmpty() -> PagedState.Empty
                    else -> PagedState.Content(items, hasMore = false)
                })
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
