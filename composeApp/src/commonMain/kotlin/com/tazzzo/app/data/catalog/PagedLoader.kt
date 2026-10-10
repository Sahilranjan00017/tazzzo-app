package com.tazzzo.app.data.catalog

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

/** One fetched page. [nextCursor] is opaque: stored and passed back, never read. */
data class Page<T>(val items: List<T>, val nextCursor: String?, val hasMore: Boolean)

/** State of an append (page 2+). A failure KEEPS the items already loaded. */
sealed interface AppendState {
    data object Idle : AppendState
    data object Loading : AppendState
    data class Failed(val failure: CatalogFailure) : AppendState
}

sealed interface PagedState<out T> {
    /** No key set yet. */
    data object Idle : PagedState<Nothing>
    data object LoadingFirst : PagedState<Nothing>
    data class FirstPageFailed(val failure: CatalogFailure) : PagedState<Nothing>
    /** Loaded, and there was nothing to show. */
    data object Empty : PagedState<Nothing>
    data class Content<T>(val items: List<T>, val hasMore: Boolean, val append: AppendState = AppendState.Idle) : PagedState<T>
}

/**
 * Reusable cursor pagination, UI-agnostic (screens observe [state]).
 *
 *  - **Key.** Everything a cursor is bound to — for products, (category, PIN) — is the [K].
 *    [setKey] with a different key discards items and cursor and loads page 1; the same key is a no-op.
 *  - **Append.** [loadMore] fetches the next page once at a time. A failure leaves
 *    the loaded items on screen with `AppendState.Failed`; [retryAppend] tries again.
 *  - **De-dup.** Items are unique by [idOf]; a repeated id is dropped.
 *  - **INVALID_CURSOR.** The cursor is dead (bound to something that changed): restart
 *    from page 1, replacing the list. It is never retried with the same cursor.
 *  - **Stale responses.** A response for a key that has since changed is discarded.
 */
class PagedLoader<K : Any, T>(
    private val scope: CoroutineScope,
    private val idOf: (T) -> String,
    private val fetch: suspend (key: K, cursor: String?) -> Page<T>
) {
    private val _state = MutableStateFlow<PagedState<T>>(PagedState.Idle)
    val state: StateFlow<PagedState<T>> = _state

    private var key: K? = null
    private var cursor: String? = null
    private var generation = 0
    private var job: Job? = null

    fun setKey(newKey: K) {
        if (newKey == key && _state.value !is PagedState.Idle) return
        key = newKey
        restart()
    }

    /** Reload page 1 for the current key (pull-to-refresh, "retry" after a first-page failure). */
    fun refresh() { if (key != null) restart() }

    /** Forget the key, the items and the cursor (e.g. sign-out); any in-flight response is discarded. Back to [PagedState.Idle]. */
    fun reset() {
        job?.cancel(); job = null
        generation++
        key = null
        cursor = null
        _state.value = PagedState.Idle
    }

    fun loadMore() {
        val s = _state.value as? PagedState.Content ?: return
        if (!s.hasMore || s.append == AppendState.Loading || s.append is AppendState.Failed) return
        append(s)
    }

    fun retryAppend() {
        val s = _state.value as? PagedState.Content ?: return
        if (s.append is AppendState.Failed && s.hasMore) append(s)
    }

    private fun restart() {
        job?.cancel()
        val gen = ++generation
        val k = key ?: return
        cursor = null
        _state.value = PagedState.LoadingFirst
        job = scope.launch {
            try {
                val page = fetch(k, null)
                if (gen != generation) return@launch
                val items = dedupe(emptyList(), page.items)
                cursor = page.nextCursor.takeIf { page.hasMore }
                _state.value = if (items.isEmpty() && !page.hasMore) PagedState.Empty
                else PagedState.Content(items, hasMore = page.hasMore && cursor != null)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Throwable) {
                if (gen == generation) _state.value = PagedState.FirstPageFailed(e.toCatalogFailure())
            }
        }
    }

    private fun append(current: PagedState.Content<T>) {
        val k = key ?: return
        val c = cursor ?: return
        val gen = generation
        _state.value = current.copy(append = AppendState.Loading)
        job = scope.launch {
            try {
                val page = fetch(k, c)
                if (gen != generation) return@launch
                val items = dedupe(current.items, page.items)
                cursor = page.nextCursor.takeIf { page.hasMore }
                _state.value = PagedState.Content(items, hasMore = page.hasMore && cursor != null)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Throwable) {
                if (gen != generation) return@launch
                val failure = e.toCatalogFailure()
                if (failure == CatalogFailure.InvalidCursor) restart()
                else _state.value = current.copy(append = AppendState.Failed(failure))
            }
        }
    }

    private fun dedupe(existing: List<T>, incoming: List<T>): List<T> {
        val seen = existing.mapTo(HashSet()) { idOf(it) }
        return existing + incoming.filter { seen.add(idOf(it)) }
    }
}

/**
 * The catalogue's product list: cursor pagination keyed by (category, PIN).
 *
 * List identity is [CatalogProduct.skuId], NOT `productId`: a list row is a SKU-level,
 * purchasable card (price and inventory are SKU-oriented, and the backend states "nothing
 * here may ever assume equality" of the two ids — they are equal only at launch). Keying on
 * `productId` would silently collapse two purchasable SKUs of one product once variants exist.
 * `productId` stays on the item for product-detail navigation.
 */
data class ProductListKey(val nodeId: String, val pin: Pincode?)

fun productPager(scope: CoroutineScope, reader: CatalogReader, pageSize: Int = RemoteCatalogDataSource.DEFAULT_PAGE_SIZE) =
    PagedLoader<ProductListKey, CatalogProduct>(scope, { it.skuId }) { key, cursor ->
        val p = reader.productPage(key.nodeId, key.pin, cursor, pageSize)
        Page(p.items, p.nextCursor, p.hasMore)
    }
