package com.tazzzo.app.content

import com.tazzzo.app.data.catalog.CatalogFailure
import com.tazzzo.app.data.catalog.CatalogProduct
import com.tazzzo.app.data.catalog.CatalogProductDetail
import com.tazzzo.app.data.catalog.PagedState
import com.tazzzo.app.data.catalog.Pincode
import com.tazzzo.app.data.catalog.StockState
import com.tazzzo.app.data.content.HomeBlock
import com.tazzzo.app.data.content.HomeContent
import com.tazzzo.app.data.content.HomeContentHolder
import com.tazzzo.app.data.content.HomeContentState
import com.tazzzo.app.data.remote.ApiError
import com.tazzzo.app.data.remote.ApiException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

class HomeContentHolderTest {
    private val pin = MutableStateFlow(Pincode.parse("560047")!!)
    private var now = 1_000_000L

    private fun card(id: String) = CatalogProduct(id, id, "Item $id", null, null, null, null, null, null, null, StockState.IN_STOCK, null, 10, 1, true, true)
    private fun detail(id: String) = CatalogProductDetail(card(id), emptyList(), emptyList(), "rel_1")
    private val rail = HomeContent(listOf(HomeBlock.ProductRail("R1", "Picks", listOf("TZP-1", "TZP-2", "TZP-3"))))

    @Test fun loadsOnceAndReusesAFreshCopy() = runTest {
        var loads = 0
        val h = HomeContentHolder(backgroundScope, { loads++; HomeContent.EMPTY }, { _, _ -> null }, pin, { now })
        h.ensure(); h.ensure(); runCurrent()
        assertIs<HomeContentState.Content>(h.state.value); assertEquals(1, loads)
        now += 30_000; h.ensure(); runCurrent(); assertEquals(1, loads)
        now += 31_000; h.ensure(); runCurrent(); assertEquals(2, loads)
    }

    @Test fun aFailureIsRecordedNotMaskedAndRefreshRetries() = runTest {
        var fail = true
        val h = HomeContentHolder(backgroundScope, {
            if (fail) throw ApiException(ApiError.Http(400, "INVALID_REQUEST", null, false, null)) else HomeContent.EMPTY
        }, { _, _ -> null }, pin, { now })
        h.ensure(); runCurrent()
        assertEquals(HomeContentState.Failed(CatalogFailure.InvalidRequest), h.state.value)
        h.ensure(); runCurrent()
        assertIs<HomeContentState.Failed>(h.state.value, "ensure does not hammer a failed endpoint")
        fail = false; h.refresh(); runCurrent()
        assertIs<HomeContentState.Content>(h.state.value)
    }

    @Test fun railCardsComeFromThePerProductReadAndAMissingProductIsSimplyAbsent() = runTest {
        val asked = mutableListOf<Pair<String, Pincode?>>()
        val h = HomeContentHolder(backgroundScope, { rail }, { id, p -> asked += id to p; if (id == "TZP-2") null else detail(id) }, pin, { now })
        h.ensure(); runCurrent()
        val r = h.rails.value["R1"]
        assertIs<PagedState.Content<CatalogProduct>>(r)
        assertEquals(listOf("TZP-1", "TZP-3"), r.items.map { it.skuId }); assertTrue(!r.hasMore)
        assertEquals(setOf("TZP-1", "TZP-2", "TZP-3"), asked.map { it.first }.toSet())
        assertTrue(asked.all { it.second == pin.value }, "cards are read for the current PIN")
    }

    @Test fun aRailWhoseProductsAreAllGoneIsEmptyAndAFailedReadFailsTheRailNotTheHome() = runTest {
        val gone = HomeContentHolder(backgroundScope, { rail }, { _, _ -> null }, pin, { now })
        gone.ensure(); runCurrent()
        assertEquals(PagedState.Empty, gone.rails.value["R1"]); assertIs<HomeContentState.Content>(gone.state.value)
        val broken = HomeContentHolder(backgroundScope, { rail }, { _, _ -> throw ApiException(ApiError.Network) }, pin, { now })
        broken.ensure(); runCurrent()
        assertIs<PagedState.FirstPageFailed>(broken.rails.value["R1"]); assertIs<HomeContentState.Content>(broken.state.value)
    }

    @Test fun parallelismIsBounded() = runTest {
        val gates = HashMap<String, CompletableDeferred<Unit>>()
        var inFlight = 0; var peak = 0
        val h = HomeContentHolder(backgroundScope, { HomeContent(listOf(HomeBlock.ProductRail("R1", "x", (1..8).map { "TZP-$it" }))) },
            { id, _ -> inFlight++; peak = maxOf(peak, inFlight); gates.getOrPut(id) { CompletableDeferred() }.await(); inFlight--; detail(id) },
            pin, { now }, parallelism = 3)
        h.ensure(); runCurrent()
        assertEquals(3, peak)
        (1..8).forEach { gates.getOrPut("TZP-$it") { CompletableDeferred() }.complete(Unit) }
        runCurrent()
        assertEquals(3, peak)
        assertEquals(8, (h.rails.value["R1"] as PagedState.Content).items.size)
    }

    @Test fun aPinChangeReloadsTheRailsForTheNewPin() = runTest {
        val asked = mutableListOf<Pincode?>()
        val h = HomeContentHolder(backgroundScope, { rail }, { id, p -> asked += p; detail(id) }, pin, { now })
        h.ensure(); runCurrent()
        h.onPinChanged(); runCurrent()
        assertEquals(3, asked.size, "same PIN: no reload")
        pin.value = Pincode.parse("560001")!!; h.onPinChanged(); runCurrent()
        assertEquals(6, asked.size); assertEquals(pin.value, asked.last())
    }
}
