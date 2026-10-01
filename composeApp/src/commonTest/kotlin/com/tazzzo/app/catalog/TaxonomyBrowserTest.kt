package com.tazzzo.app.catalog

import com.tazzzo.app.data.catalog.CatalogFailure
import com.tazzzo.app.data.catalog.CatalogNode
import com.tazzzo.app.data.catalog.NodeLevel
import com.tazzzo.app.data.catalog.NodesState
import com.tazzzo.app.data.catalog.TaxonomyBrowser
import com.tazzzo.app.data.catalog.TaxonomyPage
import com.tazzzo.app.data.catalog.level
import com.tazzzo.app.data.remote.ApiError
import com.tazzzo.app.data.remote.ApiException
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

class TaxonomyBrowserTest {
    private val sections = listOf(CatalogNode("TZS-000001", "Staples"), CatalogNode("TZS-000002", "Food"))
    private val categories = listOf(CatalogNode("TZC-000010", "Rice"), CatalogNode("TZC-000011", "Atta"))

    private class Backend(val sections: List<CatalogNode>, val categories: List<CatalogNode>) {
        var rootCalls = 0
        val childCalls = mutableListOf<String>()
        var rootError: Throwable? = null
        var childError: Throwable? = null
    }

    private fun TestScope.browser(b: Backend) = TaxonomyBrowser(
        backgroundScope,
        loadRoot = { b.rootCalls++; b.rootError?.let { throw it }; TaxonomyPage("rel", b.sections) },
        loadChildren = { id -> b.childCalls += id; b.childError?.let { throw it }; TaxonomyPage("rel", b.categories) }
    )

    @Test fun mapsTheRealLevels() {
        assertEquals(NodeLevel.SECTION, CatalogNode("TZS-000001", "x").level)       // group/section
        assertEquals(NodeLevel.CATEGORY, CatalogNode("TZC-000010", "x").level)      // Category
        assertEquals(NodeLevel.SUBCATEGORY, CatalogNode("TZG-000100", "x").level)   // Subcategory
        assertEquals(NodeLevel.VERTICAL, CatalogNode("TZV-000225", "x").level)      // backend/internal
        assertEquals(NodeLevel.UNKNOWN, CatalogNode("p8", "x").level)               // a mock id is never a real node
    }

    @Test fun rootLoadsOnceIdleToLoadingToLoaded() = runTest {
        val b = Backend(sections, categories); val t = browser(b)
        assertEquals(NodesState.Idle, t.root.value)
        t.ensureRoot(); assertEquals(NodesState.Loading, t.root.value); runCurrent()
        assertEquals(NodesState.Loaded(sections), t.root.value)
        t.ensureRoot(); t.ensureRoot(); runCurrent()
        assertEquals(1, b.rootCalls)
    }

    @Test fun childrenAreLoadedLazilyAndOnlyForTheNodeAskedFor() = runTest {
        val b = Backend(sections, categories); val t = browser(b)
        t.ensureRoot(); runCurrent()
        assertTrue(b.childCalls.isEmpty(), "no level below the root is prefetched")
        t.ensureChildren("TZS-000002"); runCurrent()
        assertEquals(listOf("TZS-000002"), b.childCalls)
        assertEquals(NodesState.Loaded(categories), t.children.value["TZS-000002"])
        assertEquals(null, t.children.value["TZS-000001"], "an untouched section stays unloaded")
    }

    @Test fun askingForTheSameChildrenTwiceFetchesOnce() = runTest {
        val b = Backend(sections, categories); val t = browser(b)
        t.ensureChildren("TZS-000001"); t.ensureChildren("TZS-000001"); runCurrent(); t.ensureChildren("TZS-000001"); runCurrent()
        assertEquals(1, b.childCalls.size)
    }

    @Test fun nodesCarryOnlyWhatTheBackendSentNoCountsNoOrderingMetadata() = runTest {
        val b = Backend(sections, categories); val t = browser(b)
        t.ensureRoot(); runCurrent()
        val items = (t.root.value as NodesState.Loaded).items
        assertEquals(sections, items)                                         // order exactly as given
        // A node is exactly an id and a name: there is no count, emoji, image or rank field to fabricate.
        assertEquals("CatalogNode(id=TZS-000001, name=Staples)", sections.first().toString())
    }

    @Test fun rootFailureIsTypedAndRetryable() = runTest {
        val b = Backend(sections, categories).apply { rootError = ApiException(ApiError.Http(503, "SERVICE_UNAVAILABLE")) }
        val t = browser(b)
        t.ensureRoot(); runCurrent()
        assertEquals(NodesState.Failed(CatalogFailure.Unavailable), t.root.value)
        t.ensureRoot(); runCurrent(); assertEquals(1, b.rootCalls, "a failure is not silently re-fetched by ensure")
        b.rootError = null; t.retryRoot(); runCurrent()
        assertEquals(NodesState.Loaded(sections), t.root.value); assertEquals(2, b.rootCalls)
    }

    @Test fun childFailureIsIndependentOfTheRootAndRetryable() = runTest {
        val b = Backend(sections, categories).apply { childError = ApiException(ApiError.Network) }
        val t = browser(b)
        t.ensureRoot(); t.ensureChildren("TZS-000001"); runCurrent()
        assertIs<NodesState.Loaded>(t.root.value)
        assertEquals(NodesState.Failed(CatalogFailure.Network), t.children.value["TZS-000001"])
        b.childError = null; t.retryChildren("TZS-000001"); runCurrent()
        assertEquals(NodesState.Loaded(categories), t.children.value["TZS-000001"])
    }

    @Test fun retryDoesNothingUnlessFailed() = runTest {
        val b = Backend(sections, categories); val t = browser(b)
        t.retryRoot(); t.retryChildren("TZS-000001"); runCurrent()
        assertEquals(0, b.rootCalls); assertTrue(b.childCalls.isEmpty())
    }

    @Test fun anEmptyChildListIsLoadedNotAnError() = runTest {
        val b = Backend(sections, emptyList()); val t = browser(b)
        t.ensureChildren("TZV-000225"); runCurrent()
        assertEquals(NodesState.Loaded(emptyList()), t.children.value["TZV-000225"])
    }
}
