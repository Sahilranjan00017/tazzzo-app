package com.tazzzo.app.catalog

import com.tazzzo.app.data.catalog.CatalogNode
import com.tazzzo.app.data.catalog.CatalogProduct
import com.tazzzo.app.data.catalog.NodesState
import com.tazzzo.app.data.catalog.Page
import com.tazzzo.app.data.catalog.PagedLoader
import com.tazzzo.app.data.catalog.PagedState
import com.tazzzo.app.data.catalog.Pincode
import com.tazzzo.app.data.catalog.ProductListHolder
import com.tazzzo.app.data.catalog.ProductListKey
import com.tazzzo.app.data.catalog.TaxonomyBrowser
import com.tazzzo.app.data.catalog.TaxonomyPage
import com.tazzzo.app.ui.catalog.BrowsePath
import com.tazzzo.app.ui.catalog.ChipLevel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs

/**
 * UI-03: the browse screen's model against a REAL-shaped four-level hierarchy (section → category → subcategory →
 * vertical). Children are fetched lazily per level, the chip rows follow the path, and the product list re-keys to the
 * deepest chosen node; opening a child then paging continues from that child's cursor, not the parent's.
 */
class BrowseHierarchyTest {
    private val tree = mapOf(
        "TZS-000001" to listOf(CatalogNode("TZC-000010", "Rice"), CatalogNode("TZC-000011", "Atta")),
        "TZC-000010" to listOf(CatalogNode("TZG-000100", "Basmati"), CatalogNode("TZG-000101", "Sona Masoori")),
        "TZG-000100" to listOf(CatalogNode("TZV-000225", "Premium Basmati")),
        "TZV-000225" to emptyList(),
        "TZC-000011" to emptyList()
    )

    @Test fun eachLevelIsFetchedOnlyWhenThePathReachesIt() = runTest {
        val calls = mutableListOf<String>()
        val b = TaxonomyBrowser(backgroundScope, loadRoot = { TaxonomyPage("rel", listOf(CatalogNode("TZS-000001", "Staples"))) },
            loadChildren = { id -> calls += id; TaxonomyPage("rel", tree.getValue(id)) })
        var path = BrowsePath("TZS-000001")
        fun sync() { path.levels.forEach { b.ensureChildren(it) } }

        sync(); runCurrent()
        assertEquals(listOf("TZS-000001"), calls)
        assertEquals(listOf(ChipLevel(0, "TZS-000001", tree.getValue("TZS-000001"))), levels(path, b))

        path = path.select(0, "TZC-000010"); sync(); runCurrent()
        assertEquals(listOf("TZS-000001", "TZC-000010"), calls)
        assertEquals(2, levels(path, b).size)

        path = path.select(1, "TZG-000100"); sync(); runCurrent()
        path = path.select(2, "TZV-000225"); sync(); runCurrent()
        assertEquals(listOf("TZS-000001", "TZC-000010", "TZG-000100", "TZV-000225"), calls)
        // The vertical has no children: no fourth chip row is shown, and nothing was invented for it.
        assertEquals(3, levels(path, b).size)
        assertEquals("TZV-000225", path.current)

        // Stepping back up re-uses what was loaded; nothing is fetched twice.
        path = path.selectAll(0); sync(); runCurrent()
        assertEquals(4, calls.size)
        assertEquals(1, levels(path, b).size)
    }

    @Test fun theListFollowsTheDeepestChosenNodeAndPagesFromItsOwnCursor() = runTest {
        val fetched = mutableListOf<Pair<String, String?>>()
        val pager = PagedLoader<ProductListKey, CatalogProduct>(backgroundScope, { it.skuId }) { key, cursor ->
            fetched += key.nodeId to cursor
            when (key.nodeId) {
                "TZS-000001" -> Page(listOf(cp("S1"), cp("S2")), "s-next", true)
                "TZC-000010" -> if (cursor == null) Page(listOf(cp("R1")), "r-next", true) else Page(listOf(cp("R2")), null, false)
                else -> Page(emptyList(), null, false)
            }
        }
        val h = ProductListHolder(backgroundScope, pager, MutableStateFlow(Pincode.LAUNCH))
        var path = BrowsePath("TZS-000001")
        h.open(path.current); runCurrent()
        assertEquals(listOf("S1", "S2"), content(h).items.map { it.skuId })

        path = path.select(0, "TZC-000010"); h.open(path.current); runCurrent()
        assertEquals(listOf("R1"), content(h).items.map { it.skuId })
        h.loadMore(); runCurrent()
        assertEquals(listOf("R1", "R2"), content(h).items.map { it.skuId })
        assertEquals(listOf("TZS-000001" to null, "TZC-000010" to null, "TZC-000010" to "r-next"), fetched)

        path = path.select(0, "TZC-000011"); h.open(path.current); runCurrent()
        assertEquals(PagedState.Empty, h.state.value)
    }

    private fun levels(path: BrowsePath, b: TaxonomyBrowser): List<ChipLevel> =
        path.levels.mapIndexedNotNull { i, id -> (b.children.value[id] as? NodesState.Loaded)?.items?.takeIf { it.isNotEmpty() }?.let { ChipLevel(i, id, it) } }

    private fun content(h: ProductListHolder) = assertIs<PagedState.Content<CatalogProduct>>(h.state.value)
}
