package com.tazzzo.app.catalog

import com.tazzzo.app.data.catalog.AppendState
import com.tazzzo.app.data.catalog.CatalogFailure
import com.tazzzo.app.data.catalog.CatalogProduct
import com.tazzzo.app.data.catalog.Page
import com.tazzzo.app.data.catalog.PagedLoader
import com.tazzzo.app.data.catalog.PagedState
import com.tazzzo.app.data.catalog.Pincode
import com.tazzzo.app.data.catalog.ProductListHolder
import com.tazzzo.app.data.catalog.ProductListKey
import com.tazzzo.app.data.catalog.StockState
import com.tazzzo.app.data.model.Money
import com.tazzzo.app.data.remote.ApiError
import com.tazzzo.app.data.remote.ApiException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue

class ProductListHolderTest {
    private class Script {
        val calls = mutableListOf<Triple<String, String?, String?>>()   // node, pin, cursor
        val queue = ArrayDeque<() -> Page<CatalogProduct>>()
        fun page(vararg p: CatalogProduct, next: String? = null) = queue.addLast { Page(p.toList(), next, next != null) }
        fun fail(t: Throwable) = queue.addLast { throw t }
    }

    private val pin = Pincode.LAUNCH
    private fun TestScope.holder(s: Script, pins: MutableStateFlow<Pincode> = MutableStateFlow(pin)): ProductListHolder {
        val pager = PagedLoader<ProductListKey, CatalogProduct>(backgroundScope, { it.skuId }) { key, cursor ->
            s.calls += Triple(key.nodeId, key.pin?.value, cursor); s.queue.removeFirst()()
        }
        return ProductListHolder(backgroundScope, pager, pins)
    }
    private fun ProductListHolder.content() = assertIs<PagedState.Content<CatalogProduct>>(state.value)

    @Test fun firstLoadUsesTheCategoryAndTheCurrentPin() = runTest {
        val s = Script().apply { page(cp("A"), cp("B", "TZP-2")) }
        val h = holder(s); h.open("TZC-000010")
        assertEquals(PagedState.LoadingFirst, h.state.value); runCurrent()
        assertEquals(listOf(Triple<String, String?, String?>("TZC-000010", "560047", null)), s.calls)
        assertEquals(listOf("A", "B"), h.content().items.map { it.skuId })
    }

    @Test fun anEmptyCategoryIsTheEmptyState() = runTest {
        val s = Script().apply { page() }
        val h = holder(s); h.open("TZC-000010"); runCurrent()
        assertEquals(PagedState.Empty, h.state.value)
    }

    @Test fun anInitialFailureIsTypedAndRetryableByRefresh() = runTest {
        val s = Script().apply { fail(ApiException(ApiError.Http(429, "RATE_LIMITED", retryAfterSeconds = 7))); page(cp()) }
        val h = holder(s); h.open("TZC-000010"); runCurrent()
        assertEquals(PagedState.FirstPageFailed(CatalogFailure.RateLimited(7)), h.state.value)
        h.refresh(); runCurrent()
        assertEquals(1, h.content().items.size)
    }

    @Test fun paginationAppendsWithTheOpaqueCursorAndEndsWithHasMoreFalse() = runTest {
        val s = Script().apply { page(cp("A"), next = "C1"); page(cp("B", "TZP-2"), next = "C2"); page(cp("C", "TZP-3")) }
        val h = holder(s); h.open("TZC-000010"); runCurrent()
        assertTrue(h.content().hasMore)
        h.loadMore(); runCurrent(); h.loadMore(); runCurrent()
        assertEquals(listOf("A", "B", "C"), h.content().items.map { it.skuId })
        assertFalse(h.content().hasMore)
        assertEquals(listOf<String?>(null, "C1", "C2"), s.calls.map { it.third })
        h.loadMore(); runCurrent(); assertEquals(3, s.calls.size, "no request past the end")
    }

    @Test fun anAppendFailureKeepsLoadedItemsAndRetryContinuesFromTheSameCursor() = runTest {
        val s = Script().apply { page(cp("A"), next = "C1"); fail(ApiException(ApiError.Http(503, "SERVICE_UNAVAILABLE"))); page(cp("B", "TZP-2")) }
        val h = holder(s); h.open("TZC-000010"); runCurrent(); h.loadMore(); runCurrent()
        assertEquals(listOf("A"), h.content().items.map { it.skuId })
        assertEquals(AppendState.Failed(CatalogFailure.Unavailable), h.content().append)
        h.retryAppend(); runCurrent()
        assertEquals(listOf("A", "B"), h.content().items.map { it.skuId })
        assertEquals(listOf<String?>(null, "C1", "C1"), s.calls.map { it.third })
    }

    @Test fun invalidCursorRestartsTheListingFromPageOne() = runTest {
        val s = Script().apply { page(cp("A"), next = "C1"); fail(ApiException(ApiError.Http(400, "INVALID_CURSOR"))); page(cp("A2", "TZP-9")) }
        val h = holder(s); h.open("TZC-000010"); runCurrent(); h.loadMore(); runCurrent()
        assertEquals(listOf("A2"), h.content().items.map { it.skuId })
        assertNull(s.calls.last().third)
    }

    // ---- identity ----------------------------------------------------------------------------------

    @Test fun skuIdIsTheListIdentitySoTwoSkusOfOneProductAreBothKept() = runTest {
        val s = Script().apply { page(cp("SKU-A", "TZP-1"), cp("SKU-B", "TZP-1")) }
        val h = holder(s); h.open("TZC-000010"); runCurrent()
        val items = h.content().items
        assertEquals(listOf("SKU-A", "SKU-B"), items.map { it.skuId })
        assertEquals(listOf("TZP-1", "TZP-1"), items.map { it.productId })   // productId is kept for PDP navigation
    }

    @Test fun theSameSkuRepeatedAcrossPagesIsCollapsed() = runTest {
        val s = Script().apply { page(cp("SKU-A"), next = "C1"); page(cp("SKU-A"), cp("SKU-C", "TZP-3")) }
        val h = holder(s); h.open("TZC-000010"); runCurrent(); h.loadMore(); runCurrent()
        assertEquals(listOf("SKU-A", "SKU-C"), h.content().items.map { it.skuId })
    }

    // ---- resets -----------------------------------------------------------------------------------------

    @Test fun aPinChangeResetsAndReloadsFromPageOneForTheNewPin() = runTest {
        val pins = MutableStateFlow(pin)
        val s = Script().apply { page(cp("A"), next = "C1"); page(cp("A-other-area")) }
        val h = holder(s, pins); h.open("TZC-000010"); runCurrent()
        pins.value = Pincode.parse("560102")!!; runCurrent()
        assertEquals(listOf(Triple<String, String?, String?>("TZC-000010", "560047", null), Triple("TZC-000010", "560102", null)), s.calls)
        assertEquals(listOf("A-other-area"), h.content().items.map { it.skuId })
    }

    @Test fun aCategoryChangeResetsTheList() = runTest {
        val s = Script().apply { page(cp("A"), next = "C1"); page(cp("X", "TZP-8")) }
        val h = holder(s); h.open("TZC-000010"); runCurrent()
        h.open("TZG-000100"); runCurrent()                                 // a subcategory is just another node
        assertEquals(listOf(Triple<String, String?, String?>("TZC-000010", "560047", null), Triple("TZG-000100", "560047", null)), s.calls)
        assertEquals(listOf("X"), h.content().items.map { it.skuId })
    }

    @Test fun reopeningTheSameNodeWithTheSamePinDoesNotRefetch() = runTest {
        val s = Script().apply { page(cp("A")) }
        val h = holder(s); h.open("TZC-000010"); runCurrent(); h.open("TZC-000010"); runCurrent()
        assertEquals(1, s.calls.size)
    }

    // ---- what the server said is what the list holds --------------------------------------------------------------

    @Test fun unknownStockNullPriceAndNotBuyableSurviveIntoTheList() = runTest {
        val unknown = cp("U", "TZP-5", price = null, mrp = null, discountPercent = null, discountAmount = null,
            stock = StockState.UNKNOWN, serviceable = null, buyable = false, max = 0)
        val s = Script().apply { page(unknown) }
        val h = holder(s); h.open("TZC-000010"); runCurrent()
        val p = h.content().items.single()
        assertEquals(StockState.UNKNOWN, p.stockState); assertNull(p.sellingPrice); assertFalse(p.buyable); assertEquals(0, p.maxOrderQuantity)
        assertEquals(Money.ofPaise(4_950), cp().sellingPrice)
    }
}
