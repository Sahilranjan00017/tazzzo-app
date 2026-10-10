package com.tazzzo.app.catalog

import com.russhwolf.settings.MapSettings
import com.tazzzo.app.TazzzoAppState
import com.tazzzo.app.analytics.AnalyticsPolicy
import com.tazzzo.app.data.catalog.CatalogFailure
import com.tazzzo.app.data.catalog.CatalogProduct
import com.tazzzo.app.data.catalog.PagedState
import com.tazzzo.app.data.catalog.Pincode
import com.tazzzo.app.data.catalog.SearchQueryCheck
import com.tazzzo.app.data.catalog.SearchQueryRules
import com.tazzzo.app.data.catalog.Page
import com.tazzzo.app.data.catalog.PagedLoader
import com.tazzzo.app.data.catalog.ProductSearch
import com.tazzzo.app.data.catalog.toCatalogFailure
import com.tazzzo.app.data.remote.ApiError
import com.tazzzo.app.data.remote.ApiException
import com.tazzzo.app.data.local.PersistentStore
import com.tazzzo.app.ui.catalog.SearchBody
import com.tazzzo.app.ui.catalog.ShopCopy
import com.tazzzo.app.ui.catalog.searchBody
import io.ktor.client.engine.mock.respond
import io.ktor.client.request.HttpRequestData
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpMethod
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Product search on `GET /v1/search` (backend PR-G): the query grammar is enforced before any request, the request carries
 * only q/page_size/cursor/pin (anonymous, installation id), typing is debounced, the cursor is bound to (query, PIN), and a 429
 * surfaces its Retry-After for the shared RetryPolicy.
 *
 * Mutation notes: dropping the trim fails [theQueryIsTrimmedAndSentExactlyOnce]; removing the debounce fails
 * [typingIsDebouncedAndOnlyTheLastQueryIsSent]; not resetting on an invalid input fails [anInvalidInputClearsResultsAndSendsNothing];
 * ignoring the PIN in the key fails [aPinChangeReRunsTheQuery]; letting the raw query through release analytics fails [theRawQueryIsStrippedFromReleaseAnalytics].
 */
class SearchTest {

    // ---- grammar ----------------------------------------------------------------------------------------------------------

    @Test fun theGrammarMatchesTheBackendsSearchTokens() {
        assertEquals(SearchQueryCheck.Blank, SearchQueryRules.check("   "))
        assertEquals(SearchQueryCheck.TooShort, SearchQueryRules.check("a"))
        assertEquals(SearchQueryCheck.TooShort, SearchQueryRules.check("a b"))           // no word of 2+ letters/digits
        assertEquals(SearchQueryCheck.Valid("rice"), SearchQueryRules.check("  rice "))
        assertEquals(SearchQueryCheck.Valid("atta 5 kg"), SearchQueryRules.check("atta 5 kg"))
        assertIs<SearchQueryCheck.Valid>(SearchQueryRules.check("r".repeat(32) + " " + "x".repeat(31)))   // 64 chars
        assertEquals(SearchQueryCheck.TooLong, SearchQueryRules.check("ab ".repeat(22)))   // 66 chars trimmed to 65
        assertEquals(SearchQueryCheck.TooLong, SearchQueryRules.check("x".repeat(33)))
        assertEquals(SearchQueryCheck.TooManyWords, SearchQueryRules.check("aa bb cc dd ee ff"))
        assertIs<SearchQueryCheck.Valid>(SearchQueryRules.check("rice Rice RICE rice rice rice"))  // one distinct word
        assertIs<SearchQueryCheck.Valid>(SearchQueryRules.check("आटा"))
    }

    // ---- data source ------------------------------------------------------------------------------------------------------

    private fun src(seen: MutableList<HttpRequestData>, body: String = pageJson(listOf(cardJson("TZP-1"), cardJson("TZP-2")), next = "c2")) =
        reader { req -> seen += req; respond(body, HttpStatusCode.OK, JSON) }

    @Test fun theQueryIsTrimmedAndSentExactlyOnce() = runTest {
        val seen = mutableListOf<HttpRequestData>()
        val page = src(seen).searchPage("atta 5 kg", Pincode.parse("560102"), null)
        val r = seen.single()
        assertEquals(HttpMethod.Get, r.method); assertEquals("/v1/search", r.url.encodedPath)
        assertEquals(setOf("q", "page_size", "pin"), r.url.parameters.names())
        assertEquals("atta 5 kg", r.url.parameters["q"]); assertEquals("560102", r.url.parameters["pin"]); assertEquals("20", r.url.parameters["page_size"])
        assertNull(r.headers[HttpHeaders.Authorization]); assertEquals(INSTALL_ID, r.headers["X-Tazzzo-Installation-Id"])
        assertEquals(listOf("TZP-1", "TZP-2"), page.items.map { it.skuId }); assertTrue(page.hasMore); assertEquals("c2", page.nextCursor)
    }

    @Test fun anUnsendableQueryIsNeverSent() = runTest {
        val seen = mutableListOf<HttpRequestData>()
        val r = src(seen)
        for (bad in listOf("", "a", " rice", "x".repeat(65), "aa bb cc dd ee ff")) assertFailsWith<IllegalArgumentException>(bad) { r.searchPage(bad, null, null) }
        assertTrue(seen.isEmpty())
    }

    // ---- ProductSearch ----------------------------------------------------------------------------------------------------

    /** A scripted search backend: records (query, pin, cursor) and answers from a queue (default: one product). */
    private class Rig(scope: TestScope) {
        val calls = mutableListOf<Triple<String, String?, String?>>()
        val queue = ArrayDeque<() -> Page<CatalogProduct>>()
        val pin = MutableStateFlow(Pincode.parse("560102")!!)
        val search = ProductSearch(scope.backgroundScope, PagedLoader(scope.backgroundScope, { it.skuId }) { key, cursor ->
            calls += Triple(key.query, key.pin?.value, cursor)
            (queue.removeFirstOrNull() ?: { Page(listOf(cp("TZP-1")), null, false) })()
        }, pin)
        fun queries() = calls.map { it.first }
    }

    @Test fun rateLimitedFromTheServerCarriesRetryAfter() = runTest {
        val hdr = headersOf(HttpHeaders.ContentType to listOf("application/json"), HttpHeaders.RetryAfter to listOf("30"))
        val r = reader { respond(errorFlat("RATE_LIMITED", retryable = true), HttpStatusCode.TooManyRequests, hdr) }
        val e = assertFailsWith<ApiException> { r.searchPage("rice", null, null) }
        assertEquals(CatalogFailure.RateLimited(30), e.toCatalogFailure())
    }

    @Test fun typingIsDebouncedAndOnlyTheLastQueryIsSent() = runTest {
        val r = Rig(this)
        r.search.onInput("ri"); advanceTimeBy(100)
        r.search.onInput("ric"); advanceTimeBy(100)
        r.search.onInput("rice"); advanceTimeBy(300); runCurrent()
        assertTrue(r.calls.isEmpty())                                          // still inside the pause
        advanceTimeBy(100); runCurrent()
        assertEquals(listOf<String?>("rice"), r.queries())
        assertIs<PagedState.Content<CatalogProduct>>(r.search.results.value)
    }

    @Test fun anInvalidInputClearsResultsAndSendsNothing() = runTest {
        val r = Rig(this)
        r.search.submit("rice"); runCurrent()
        assertIs<PagedState.Content<CatalogProduct>>(r.search.results.value)
        r.search.onInput("r"); advanceTimeBy(1_000); runCurrent()
        assertEquals(PagedState.Idle, r.search.results.value); assertEquals(SearchQueryCheck.TooShort, r.search.check.value)
        assertEquals(1, r.calls.size)
    }

    @Test fun submitRunsAtOnceAndReturnsTheTrimmedQuery() = runTest {
        val r = Rig(this)
        assertEquals("milk", r.search.submit("  milk  ")); runCurrent()
        assertEquals(listOf<String?>("milk"), r.queries())
        assertNull(r.search.submit("m"))
    }

    @Test fun aPinChangeReRunsTheQuery() = runTest {
        val r = Rig(this)
        r.search.submit("rice"); runCurrent()
        r.pin.value = Pincode.parse("560001")!!; runCurrent()
        assertEquals(listOf<String?>("560102", "560001"), r.calls.map { it.second })
    }

    @Test fun loadMorePassesTheCursorBackUntouched() = runTest {
        val r = Rig(this)
        r.queue.addLast { Page(listOf(cp("TZP-1")), "opaque-c2", true) }
        r.queue.addLast { Page(listOf(cp("TZP-2", "TZP-2")), null, false) }
        r.search.submit("rice"); runCurrent()
        r.search.loadMore(); runCurrent()
        assertEquals(Triple<String, String?, String?>("rice", "560102", "opaque-c2"), r.calls[1])
        assertEquals(listOf("TZP-1", "TZP-2"), (r.search.results.value as PagedState.Content).items.map { it.skuId })
    }

    @Test fun aRateLimitedFirstPageFailsTypedAndResubmittingRetries() = runTest {
        val r = Rig(this)
        r.queue.addLast { throw ApiException(ApiError.Http(429, "RATE_LIMITED", retryAfterSeconds = 30)) }
        r.search.submit("rice"); runCurrent()
        assertEquals(PagedState.FirstPageFailed(CatalogFailure.RateLimited(30)), r.search.results.value)
        r.search.submit("rice"); runCurrent()                                  // the same query again is an explicit retry
        assertIs<PagedState.Content<CatalogProduct>>(r.search.results.value)
        assertEquals(2, r.calls.size)
    }

    @Test fun anEmptyResultIsNoResults() = runTest {
        val r = Rig(this)
        r.queue.addLast { Page(emptyList(), null, false) }
        r.search.submit("zzzz"); runCurrent()
        assertEquals(SearchBody.NoResults, searchBody(r.search.check.value, r.search.results.value, emptyList()))
    }

    // ---- presentation / app state -----------------------------------------------------------------------------------------

    @Test fun theBodyFollowsTheQueryAndTheResults() {
        assertEquals(SearchBody.Start(listOf("milk")), searchBody(SearchQueryCheck.Blank, PagedState.Idle, listOf("milk")))
        assertEquals(SearchBody.Invalid(ShopCopy.QUERY_TOO_SHORT), searchBody(SearchQueryCheck.TooShort, PagedState.Idle, emptyList()))
        assertEquals(SearchBody.Invalid(ShopCopy.QUERY_TOO_MANY_WORDS), searchBody(SearchQueryCheck.TooManyWords, PagedState.Idle, emptyList()))
        assertEquals(SearchBody.Loading, searchBody(SearchQueryCheck.Valid("rice"), PagedState.Idle, emptyList()))
        assertEquals(SearchBody.Failed(CatalogFailure.Network), searchBody(SearchQueryCheck.Valid("rice"), PagedState.FirstPageFailed(CatalogFailure.Network), emptyList()))
    }

    @Test fun openSearchPrefillsOnlyASendableQuery() {
        val app = TazzzoAppState(PersistentStore(MapSettings()))
        app.openSearch(" atta ")
        assertEquals("atta", app.searchPrefill)
        app.openSearch("x".repeat(80))
        assertNull(app.searchPrefill)
    }

    @Test fun theRawQueryIsStrippedFromReleaseAnalytics() {
        // recordSearch tracks {"query": text}; the release policy drops the key (developer logging only keeps it).
        assertFalse("query" in AnalyticsPolicy.releaseSafe(mapOf("query" to "my secret query")))
    }
}
