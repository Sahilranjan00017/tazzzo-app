package com.tazzzo.app.catalog

import com.tazzzo.app.data.catalog.AppendState
import com.tazzzo.app.data.catalog.CatalogFailure
import com.tazzzo.app.data.catalog.Page
import com.tazzzo.app.data.catalog.PagedLoader
import com.tazzzo.app.data.catalog.PagedState
import com.tazzzo.app.data.catalog.Pincode
import com.tazzzo.app.data.catalog.ProductListKey
import com.tazzzo.app.data.catalog.productPager
import com.tazzzo.app.data.remote.ApiError
import com.tazzzo.app.data.remote.ApiException
import io.ktor.client.engine.mock.respond
import io.ktor.http.HttpStatusCode
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

class PagedLoaderTest {
    private data class Item(val id: String)

    /** Scripted fetcher: records (key, cursor) and returns/throws what the test queued. */
    private class Script {
        val calls = mutableListOf<Pair<String, String?>>()
        val queue = ArrayDeque<() -> Page<Item>>()
        var gate: CompletableDeferred<Unit>? = null
        fun page(vararg ids: String, next: String? = null) = queue.addLast { Page(ids.map(::Item), next, next != null) }
        fun fail(t: Throwable) = queue.addLast { throw t }
    }

    private fun TestScope.loader(s: Script) = PagedLoader<String, Item>(backgroundScope, { it.id }) { key, cursor ->
        s.calls += key to cursor
        s.gate?.await()
        s.queue.removeFirst()()
    }

    private val invalidCursor = ApiException(ApiError.Http(400, "INVALID_CURSOR"))
    private val unavailable = ApiException(ApiError.Http(503, "SERVICE_UNAVAILABLE"))

    private fun PagedLoader<String, Item>.content() = assertIs<PagedState.Content<Item>>(state.value)

    @Test fun firstPageThenAppendUsesTheOpaqueCursorUntouched() = runTest {
        val s = Script().apply { page("a", "b", next = "C1"); page("c", next = null) }
        val l = loader(s); l.setKey("cat")
        runCurrent()
        assertEquals(listOf("a", "b"), l.content().items.map { it.id }); assertTrue(l.content().hasMore)
        l.loadMore(); runCurrent()
        assertEquals(listOf<Pair<String, String?>>("cat" to null, "cat" to "C1"), s.calls)
        assertEquals(listOf("a", "b", "c"), l.content().items.map { it.id }); assertEquals(false, l.content().hasMore)
    }

    @Test fun loadMoreWhenThereIsNoMoreDoesNothing() = runTest {
        val s = Script().apply { page("a") }
        val l = loader(s); l.setKey("cat"); runCurrent(); l.loadMore(); runCurrent()
        assertEquals(1, s.calls.size)
    }

    @Test fun duplicatesAcrossPagesAreDropped() = runTest {
        val s = Script().apply { page("a", "b", "a", next = "C1"); page("b", "c", next = null) }
        val l = loader(s); l.setKey("cat"); runCurrent(); l.loadMore(); runCurrent()
        assertEquals(listOf("a", "b", "c"), l.content().items.map { it.id })
    }

    @Test fun appendLoadsOnlyOnceAtATime() = runTest {
        val s = Script().apply { page("a", next = "C1"); page("b", next = null) }
        val l = loader(s); l.setKey("cat"); runCurrent()
        s.gate = CompletableDeferred()
        l.loadMore(); l.loadMore(); l.loadMore(); runCurrent()
        assertEquals(AppendState.Loading, l.content().append); assertEquals(2, s.calls.size)
        s.gate!!.complete(Unit); runCurrent()
        assertEquals(2, s.calls.size)
    }

    @Test fun appendFailureKeepsLoadedItemsAndRetryRecovers() = runTest {
        val s = Script().apply { page("a", "b", next = "C1"); fail(unavailable); page("c", next = null) }
        val l = loader(s); l.setKey("cat"); runCurrent(); l.loadMore(); runCurrent()
        val failed = l.content()
        assertEquals(listOf("a", "b"), failed.items.map { it.id }, "nothing already loaded is dropped")
        assertEquals(AppendState.Failed(CatalogFailure.Unavailable), failed.append); assertTrue(failed.hasMore)
        l.loadMore(); runCurrent(); assertEquals(2, s.calls.size) // no auto-retry loop from scrolling
        l.retryAppend(); runCurrent()
        assertEquals(listOf<Pair<String, String?>>("cat" to null, "cat" to "C1", "cat" to "C1"), s.calls) // same cursor, as given
        assertEquals(listOf("a", "b", "c"), l.content().items.map { it.id }); assertEquals(AppendState.Idle, l.content().append)
    }

    @Test fun firstPageFailureThenRefresh() = runTest {
        val s = Script().apply { fail(ApiException(ApiError.Network)); page("a") }
        val l = loader(s); l.setKey("cat"); runCurrent()
        assertEquals(PagedState.FirstPageFailed(CatalogFailure.Network), l.state.value)
        l.refresh(); runCurrent()
        assertEquals(listOf("a"), l.content().items.map { it.id })
    }

    @Test fun emptyFirstPageIsEmptyNotContent() = runTest {
        val s = Script().apply { page() }
        val l = loader(s); l.setKey("cat"); runCurrent()
        assertEquals(PagedState.Empty, l.state.value)
    }

    @Test fun changingTheKeyResetsItemsAndCursor() = runTest {
        val s = Script().apply { page("a", next = "C1"); page("x", next = null) }
        val l = loader(s); l.setKey("cat1"); runCurrent()
        l.setKey("cat2"); runCurrent()
        assertEquals(listOf<Pair<String, String?>>("cat1" to null, "cat2" to null), s.calls, "the old cursor is never sent for the new category")
        assertEquals(listOf("x"), l.content().items.map { it.id })
    }

    @Test fun theSameKeyIsANoOp() = runTest {
        val s = Script().apply { page("a") }
        val l = loader(s); l.setKey("cat"); runCurrent(); l.setKey("cat"); runCurrent()
        assertEquals(1, s.calls.size)
    }

    @Test fun aSlowResponseForAnOldKeyIsDiscarded() = runTest {
        val gates = mapOf("cat1" to CompletableDeferred<Unit>(), "cat2" to CompletableDeferred<Unit>())
        // A fetch that ignores cancellation, i.e. a response that still arrives after the key changed.
        val l = PagedLoader<String, Item>(backgroundScope, { it.id }) { key, _ ->
            withContext(kotlinx.coroutines.NonCancellable) { gates.getValue(key).await() }
            Page(listOf(Item(key)), null, false)
        }
        l.setKey("cat1"); runCurrent()
        l.setKey("cat2"); runCurrent()
        gates.getValue("cat2").complete(Unit); runCurrent()
        gates.getValue("cat1").complete(Unit); runCurrent() // the stale answer lands last
        assertEquals(listOf("cat2"), l.content().items.map { it.id })
    }

    @Test fun invalidCursorRestartsFromPageOneAndReplacesTheList() = runTest {
        val s = Script().apply { page("a", "b", next = "C1"); fail(invalidCursor); page("a2", "b2", next = null) }
        val l = loader(s); l.setKey("cat"); runCurrent(); l.loadMore(); runCurrent()
        assertEquals(listOf<Pair<String, String?>>("cat" to null, "cat" to "C1", "cat" to null), s.calls, "restart sends no cursor")
        assertEquals(listOf("a2", "b2"), l.content().items.map { it.id })
    }

    // ---- the product pager wires (category, PIN) as the key -------------------------------------------

    @Test fun pinChangeIsADifferentKeyAndRestartsPaging() = runTest {
        val s = Script().apply { page("a", next = "C1"); page("b") }
        val l = PagedLoader<ProductListKey, Item>(backgroundScope, { it.id }) { key, cursor ->
            s.calls += (key.pin?.value + "/" + key.nodeId) to cursor; s.queue.removeFirst()()
        }
        l.setKey(ProductListKey("TZC-000010", Pincode.parse("560047")))
        runCurrent()
        l.setKey(ProductListKey("TZC-000010", Pincode.parse("560102")))
        runCurrent()
        assertEquals(listOf<Pair<String, String?>>("560047/TZC-000010" to null, "560102/TZC-000010" to null), s.calls)
        l.setKey(ProductListKey("TZC-000010", Pincode.parse("560102"))) // equal key: no reload
        runCurrent(); assertEquals(2, s.calls.size)
    }

    @Test fun productPagerEndToEndWithTheReaderAndA404AsEmpty() = runTest {
        val r = reader { req ->
            if (req.url.encodedPath.contains("TZC-000099")) respond(errorFlat("NOT_FOUND"), HttpStatusCode.NotFound, JSON)
            else respond(pageJson(listOf(cardJson("TZP-1"), cardJson("TZP-2")), next = null), HttpStatusCode.OK, JSON)
        }
        val pager = productPager(kotlinx.coroutines.CoroutineScope(Dispatchers.Default), r)
        pager.setKey(ProductListKey("TZC-000010", Pincode.LAUNCH))
        withContext(Dispatchers.Default) { withTimeout(5_000) { while (pager.state.value !is PagedState.Content<*>) delay(5) } }
        assertEquals(2, (pager.state.value as PagedState.Content<*>).items.size)
        pager.setKey(ProductListKey("TZC-000099", Pincode.LAUNCH))
        withContext(Dispatchers.Default) { withTimeout(5_000) { while (pager.state.value !is PagedState.Empty) delay(5) } }
        assertEquals(PagedState.Empty, pager.state.value)
    }
}
