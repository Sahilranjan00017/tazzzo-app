package com.tazzzo.app.data.catalog

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

/** Whether a typed query may be sent to `GET /v1/search`, and why not. */
sealed interface SearchQueryCheck {
    /** [text] is the trimmed query exactly as it is sent. */
    data class Valid(val text: String) : SearchQueryCheck
    /** Nothing typed: the screen shows recent searches. */
    data object Blank : SearchQueryCheck
    /** Fewer than [SearchQueryRules.MIN_LENGTH] characters, or no word of at least two letters/digits. */
    data object TooShort : SearchQueryCheck
    /** Longer than [SearchQueryRules.MAX_LENGTH], or a word longer than [SearchQueryRules.MAX_WORD_LENGTH]. */
    data object TooLong : SearchQueryCheck
    data object TooManyWords : SearchQueryCheck
}

/**
 * The backend's query grammar (`commerce.read.SearchTokens`), checked BEFORE a request so a query the server would reject with
 * 400 is never sent. Tokenisation is the server's: a token is a run of letters/digits plus the combining marks that follow
 * them (so Devanagari words keep their matras, e.g. "की" is one 2-character token), lower-cased; everything else separates.
 * The trimmed text is 2..64 characters; tokens of 2+ characters count (distinct, at least one, at most 5); no token may be
 * longer than 32 characters. Lengths are UTF-16 units, as on the server. Only commonMain Unicode APIs are used.
 */
object SearchQueryRules {
    const val MIN_LENGTH = 2
    const val MAX_LENGTH = 64
    const val MAX_WORDS = 5
    const val MAX_WORD_LENGTH = 32

    fun check(raw: String): SearchQueryCheck {
        val text = raw.trim()
        if (text.isEmpty()) return SearchQueryCheck.Blank
        if (text.length > MAX_LENGTH) return SearchQueryCheck.TooLong
        if (text.length < MIN_LENGTH) return SearchQueryCheck.TooShort
        val tokens = tokens(text)
        if (tokens.any { it.length > MAX_WORD_LENGTH }) return SearchQueryCheck.TooLong
        val counted = tokens.filter { it.length >= 2 }
        if (counted.isEmpty()) return SearchQueryCheck.TooShort
        if (counted.size > MAX_WORDS) return SearchQueryCheck.TooManyWords
        return SearchQueryCheck.Valid(text)
    }

    /** Distinct tokens in first-seen order, exactly as the server splits them. */
    internal fun tokens(text: String): List<String> {
        val out = LinkedHashSet<String>(); val cur = StringBuilder()
        for (c in text) {
            if (c.isLetterOrDigit() || (cur.isNotEmpty() && c.isCombiningMark())) cur.append(c.lowercaseChar())
            else if (cur.isNotEmpty()) { out += cur.toString(); cur.clear() }
        }
        if (cur.isNotEmpty()) out += cur.toString()
        return out.toList()
    }

    private fun Char.isCombiningMark(): Boolean =
        category == CharCategory.NON_SPACING_MARK || category == CharCategory.COMBINING_SPACING_MARK || category == CharCategory.ENCLOSING_MARK
}

/** A search page is bound to its query AND the delivery PIN (the cursor carries both server-side). */
data class SearchKey(val query: String, val pin: Pincode?)

/**
 * Product search for the Search screen. UI-agnostic (screens observe [input], [check], [results] and [stale]):
 *  - [onInput] records the text and, when it is valid, runs it after [debounceMillis] of quiet typing; an invalid or blank
 *    text cancels any pending run and clears the results (nothing stale stays on screen). While a new valid text waits for its
 *    run, [stale] is true so the screen can dim the previous query's results.
 *  - [submit] (the keyboard's Search action, a recent-search chip, a `search:` banner) runs at once.
 *  - A PIN change re-runs the current query (results and cursor are bound to the PIN).
 *  - [onResults] is told a query ONLY when the first page for THAT (query, PIN) key has arrived with at least one product — never
 *    for a prefix that was typed past, a query still debouncing, or a zero-result query ("recent searches" are fed from here).
 *  - A 429 holds every request (typed, debounced or submitted) until its Retry-After has elapsed, then runs the latest query
 *    automatically; the results stay `FirstPageFailed(RateLimited)` meanwhile so the screen shows the countdown.
 *  - Paging, the stale-response guard and INVALID_CURSOR recovery are [PagedLoader]'s.
 * [scope] must be dedicated to this instance: [close] cancels it. The query text is never logged.
 */
class ProductSearch(
    private val scope: CoroutineScope,
    private val pager: PagedLoader<SearchKey, CatalogProduct>,
    private val pin: StateFlow<Pincode>,
    private val debounceMillis: Long = DEBOUNCE_MILLIS,
    private val onResults: (String) -> Unit = {}
) {
    private val _input = MutableStateFlow("")
    val input: StateFlow<String> = _input
    private val _check = MutableStateFlow<SearchQueryCheck>(SearchQueryCheck.Blank)
    val check: StateFlow<SearchQueryCheck> = _check
    val results: StateFlow<PagedState<CatalogProduct>> = pager.state
    private val _stale = MutableStateFlow(false)
    /** True while the shown results belong to an earlier query (the new valid text has not run yet). */
    val stale: StateFlow<Boolean> = _stale

    private var pending: Job? = null
    private var active: String? = null
    private var lastKey: SearchKey? = null
    private var recordedKey: SearchKey? = null
    /** Set while a 429's Retry-After window is open: the query to run when it closes. */
    private var heldQuery: String? = null
    private var holding = false

    init {
        scope.launch { pin.collect { p -> active?.let { q -> SearchKey(q, p).also { lastKey = it; if (!holding) pager.setKey(it) else heldQuery = q } } } }
        scope.launch {
            pager.state.collect { st ->
                val key = lastKey
                if (st is PagedState.Content && key != null && key != recordedKey && st.items.isNotEmpty()) { recordedKey = key; onResults(key.query) }
                if (st is PagedState.FirstPageFailed && st.failure is CatalogFailure.RateLimited && !holding) hold(st.failure)
            }
        }
    }

    fun onInput(text: String) {
        _input.value = text
        val c = SearchQueryRules.check(text)
        _check.value = c
        pending?.cancel()
        if (c !is SearchQueryCheck.Valid) {
            active = null; lastKey = null; _stale.value = false
            if (holding) heldQuery = null else pager.reset()
            return
        }
        if (c.text == active) { _stale.value = false; return }
        _stale.value = pager.state.value is PagedState.Content
        pending = scope.launch { delay(debounceMillis); run(c.text) }
    }

    /** Runs the current input now. Returns the query that will run, or null when the input is not valid. */
    fun submit(text: String = _input.value): String? {
        if (text != _input.value) { _input.value = text }
        val c = SearchQueryRules.check(text)
        _check.value = c
        pending?.cancel()
        if (c !is SearchQueryCheck.Valid) return null
        run(c.text)
        return c.text
    }

    fun loadMore() = pager.loadMore()
    fun retryAppend() = pager.retryAppend()
    fun refresh() { if (!holding) pager.refresh() }

    /** Cancels this instance's coroutines (its dedicated scope). */
    fun close() = scope.cancel()

    private fun run(query: String) {
        active = query
        _stale.value = false
        if (holding) { heldQuery = query; return }            // Retry-After: nothing is sent until the window closes
        val key = SearchKey(query, pin.value)
        // Re-running the same query after a failed first page is an explicit retry; otherwise the same key is a no-op.
        if (key == lastKey && pager.state.value is PagedState.FirstPageFailed) pager.refresh() else pager.setKey(key)
        lastKey = key
    }

    private fun hold(failure: CatalogFailure.RateLimited) {
        holding = true
        heldQuery = active
        scope.launch {
            delay(RetryPolicy.waitSeconds(failure, 1) * 1_000L)
            holding = false
            val q = heldQuery; heldQuery = null
            if (q != null) run(q)                              // the same key after a failed first page = refresh
        }
    }

    companion object { const val DEBOUNCE_MILLIS = 350L }
}

/** A search holder on its OWN child scope of [scope] (so [ProductSearch.close] never cancels the caller's scope). */
fun productSearch(scope: CoroutineScope, reader: CatalogReader, pin: StateFlow<Pincode>, onResults: (String) -> Unit = {}): ProductSearch {
    val own = CoroutineScope(scope.coroutineContext + SupervisorJob(scope.coroutineContext[Job]))
    return ProductSearch(own, PagedLoader(own, { it.skuId }) { key, cursor ->
        val p = reader.searchPage(key.query, key.pin, cursor)
        Page(p.items, p.nextCursor, p.hasMore)
    }, pin, onResults = onResults)
}
