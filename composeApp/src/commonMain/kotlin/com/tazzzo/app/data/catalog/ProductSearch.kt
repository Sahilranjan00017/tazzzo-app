package com.tazzzo.app.data.catalog

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
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
 * The backend's query grammar (`SearchTokens`), checked BEFORE a request so a query the server would reject with 400 is never
 * sent: the trimmed text is 2..64 characters; words are runs of letters/digits; only words of 2+ characters count, at least one
 * and at most 5 of them, none longer than 32. (The server also lets combining marks join a word, e.g. Devanagari matras; here
 * a mark simply splits, which can only make the local word count larger, so nothing invalid is ever sent.)
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
        val words = words(text)
        if (words.any { it.length > MAX_WORD_LENGTH }) return SearchQueryCheck.TooLong
        val counted = words.filter { it.length >= 2 }.map { it.lowercase() }.distinct()
        if (counted.isEmpty()) return SearchQueryCheck.TooShort
        if (counted.size > MAX_WORDS) return SearchQueryCheck.TooManyWords
        return SearchQueryCheck.Valid(text)
    }

    private fun words(text: String): List<String> {
        val out = ArrayList<String>(); val cur = StringBuilder()
        for (c in text) if (c.isLetterOrDigit()) cur.append(c) else if (cur.isNotEmpty()) { out += cur.toString(); cur.clear() }
        if (cur.isNotEmpty()) out += cur.toString()
        return out
    }
}

/** A search page is bound to its query AND the delivery PIN (the cursor carries both server-side). */
data class SearchKey(val query: String, val pin: Pincode?)

/**
 * Product search for the Search screen. UI-agnostic (screens observe [input], [check] and [results]):
 *  - [onInput] records the text and, when it is valid, runs it after [debounceMillis] of quiet typing; an invalid or blank
 *    text cancels any pending run and clears the results (nothing stale stays on screen).
 *  - [submit] (the keyboard's Search action, a recent-search chip, a `search:` banner) runs at once.
 *  - A PIN change re-runs the current query (results and cursor are bound to the PIN).
 *  - Paging, the stale-response guard and INVALID_CURSOR recovery are [PagedLoader]'s; a 429 surfaces as
 *    `CatalogFailure.RateLimited(retryAfter)` for the shared RetryPolicy countdown.
 * The query text is never logged.
 */
class ProductSearch(
    private val scope: CoroutineScope,
    private val pager: PagedLoader<SearchKey, CatalogProduct>,
    private val pin: StateFlow<Pincode>,
    private val debounceMillis: Long = DEBOUNCE_MILLIS
) {
    private val _input = MutableStateFlow("")
    val input: StateFlow<String> = _input
    private val _check = MutableStateFlow<SearchQueryCheck>(SearchQueryCheck.Blank)
    val check: StateFlow<SearchQueryCheck> = _check
    val results: StateFlow<PagedState<CatalogProduct>> = pager.state

    private var pending: Job? = null
    private var active: String? = null
    private var lastKey: SearchKey? = null

    init {
        scope.launch { pin.collect { p -> active?.let { q -> SearchKey(q, p).also { lastKey = it; pager.setKey(it) } } } }
    }

    fun onInput(text: String) {
        _input.value = text
        val c = SearchQueryRules.check(text)
        _check.value = c
        pending?.cancel()
        if (c !is SearchQueryCheck.Valid) { active = null; lastKey = null; pager.reset(); return }
        if (c.text == active) return
        pending = scope.launch { delay(debounceMillis); run(c.text) }
    }

    /** Runs the current input now. Returns the query that ran (for "recent searches"), or null when the input is not valid. */
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
    fun refresh() = pager.refresh()

    private fun run(query: String) {
        active = query
        val key = SearchKey(query, pin.value)
        // Re-running the same query after a failed first page is an explicit retry; otherwise the same key is a no-op.
        if (key == lastKey && pager.state.value is PagedState.FirstPageFailed) pager.refresh() else pager.setKey(key)
        lastKey = key
    }

    companion object { const val DEBOUNCE_MILLIS = 350L }
}

fun productSearch(scope: CoroutineScope, reader: CatalogReader, pin: StateFlow<Pincode>): ProductSearch =
    ProductSearch(scope, PagedLoader(scope, { it.skuId }) { key, cursor ->
        val p = reader.searchPage(key.query, key.pin, cursor)
        Page(p.items, p.nextCursor, p.hasMore)
    }, pin)
