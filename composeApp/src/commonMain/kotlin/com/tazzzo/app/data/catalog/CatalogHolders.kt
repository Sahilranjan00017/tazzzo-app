package com.tazzzo.app.data.catalog

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

/*
 * State holders between the screens and the catalogue reader (PR-04C). Composables never call a
 * data source: they observe these. Plain classes over StateFlow, no framework, so they are tested
 * with a test scope.
 */

// ---- taxonomy ----------------------------------------------------------------------------------------

sealed interface NodesState {
    /** Nothing requested yet. */
    data object Idle : NodesState
    data object Loading : NodesState
    data class Loaded(val items: List<CatalogNode>) : NodesState
    data class Failed(val failure: CatalogFailure) : NodesState
}

/**
 * Lazy taxonomy browser. Only the root (the TZS sections) is loaded up front by the screen that
 * shows it; a section's or category's children are fetched ONLY when [ensureChildren] is asked for
 * them. No level is prefetched. Counts, emoji, imagery and ordering are not fabricated: a node is
 * just an id and a name, in the order the backend gave.
 */
class TaxonomyBrowser(
    private val scope: CoroutineScope,
    private val loadRoot: suspend () -> TaxonomyPage,
    private val loadChildren: suspend (String) -> TaxonomyPage
) {
    constructor(scope: CoroutineScope, reader: CatalogReader) : this(scope, reader::categories, reader::children)

    private val _root = MutableStateFlow<NodesState>(NodesState.Idle)
    val root: StateFlow<NodesState> = _root

    private val _children = MutableStateFlow<Map<String, NodesState>>(emptyMap())
    val children: StateFlow<Map<String, NodesState>> = _children

    /** Loads the root once; a no-op while loading or loaded. A failure is retried with [retryRoot]. */
    fun ensureRoot() {
        if (_root.value is NodesState.Idle) startRoot()
    }

    fun retryRoot() {
        if (_root.value is NodesState.Failed) startRoot()
    }

    fun ensureChildren(nodeId: String) {
        if (_children.value[nodeId] == null || _children.value[nodeId] is NodesState.Idle) startChildren(nodeId)
    }

    fun retryChildren(nodeId: String) {
        if (_children.value[nodeId] is NodesState.Failed) startChildren(nodeId)
    }

    private fun startRoot() {
        _root.value = NodesState.Loading
        scope.launch {
            _root.value = try {
                NodesState.Loaded(loadRoot().items)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Throwable) {
                NodesState.Failed(e.toCatalogFailure())
            }
        }
    }

    private fun startChildren(nodeId: String) {
        put(nodeId, NodesState.Loading)
        scope.launch {
            put(
                nodeId,
                try {
                    NodesState.Loaded(loadChildren(nodeId).items)
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Throwable) {
                    NodesState.Failed(e.toCatalogFailure())
                }
            )
        }
    }

    private fun put(id: String, s: NodesState) { _children.value = _children.value + (id to s) }
}

// ---- product list ---------------------------------------------------------------------------------------

/**
 * One category's product list. Wraps the cursor pager and the delivery PIN: a PIN change or a
 * different node restarts paging from page 1 (the cursor is bound to both), and an unchanged
 * (node, PIN) is a no-op.
 */
class ProductListHolder(
    scope: CoroutineScope,
    private val pager: PagedLoader<ProductListKey, CatalogProduct>,
    pin: StateFlow<Pincode>
) {
    private val currentPin = pin
    private var node: String? = null

    val state: StateFlow<PagedState<CatalogProduct>> = pager.state

    init {
        scope.launch { pin.collect { apply() } }
    }

    /** Opens (or switches to) [nodeId]: a category, or one of its subcategories. */
    fun open(nodeId: String) {
        node = nodeId
        apply()
    }

    fun loadMore() = pager.loadMore()
    fun retryAppend() = pager.retryAppend()
    fun refresh() = pager.refresh()

    private fun apply() {
        node?.let { pager.setKey(ProductListKey(it, currentPin.value)) }
    }
}

fun productListHolder(scope: CoroutineScope, reader: CatalogReader, pin: StateFlow<Pincode>): ProductListHolder =
    ProductListHolder(scope, productPager(scope, reader), pin)

// ---- product detail -----------------------------------------------------------------------------------------

sealed interface PdpState {
    data object Idle : PdpState
    data object Loading : PdpState
    data class Content(val detail: CatalogProductDetail) : PdpState
    /** 404: unknown, ineligible or unreachable — all the same answer by backend design. */
    data object NotFound : PdpState
    data class Failed(val failure: CatalogFailure) : PdpState
}

/**
 * One product page. Navigating to another product, or changing the PIN, supersedes the load in
 * flight: a late answer for a product or PIN that is no longer current is discarded.
 */
class ProductDetailHolder(
    private val scope: CoroutineScope,
    private val load: suspend (productId: String, pin: Pincode?) -> CatalogProductDetail?,
    pin: StateFlow<Pincode>
) {
    private val currentPin = pin
    private val _state = MutableStateFlow<PdpState>(PdpState.Idle)
    val state: StateFlow<PdpState> = _state

    private var productId: String? = null
    private var generation = 0
    private var job: Job? = null

    init {
        // Collection starts with the current PIN; only a CHANGE re-loads an open product.
        scope.launch {
            var first = true
            pin.collect { if (first) first = false else if (productId != null) start() }
        }
    }

    fun open(id: String) {
        if (id == productId && _state.value !is PdpState.Failed) return
        productId = id
        start()
    }

    fun retry() {
        if (productId != null && _state.value is PdpState.Failed) start()
    }

    private fun start() {
        val id = productId ?: return
        job?.cancel()
        val gen = ++generation
        val pinAtStart = currentPin.value
        _state.value = PdpState.Loading
        job = scope.launch {
            val result: PdpState = try {
                load(id, pinAtStart)?.let { PdpState.Content(it) } ?: PdpState.NotFound
            } catch (e: CancellationException) {
                throw e
            } catch (e: Throwable) {
                PdpState.Failed(e.toCatalogFailure())
            }
            if (gen == generation) _state.value = result
        }
    }
}

fun productDetailHolder(scope: CoroutineScope, reader: CatalogReader, pin: StateFlow<Pincode>): ProductDetailHolder =
    ProductDetailHolder(scope, { id, p -> reader.product(id, p) }, pin)
