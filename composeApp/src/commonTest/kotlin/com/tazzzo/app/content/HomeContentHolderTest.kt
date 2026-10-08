package com.tazzzo.app.content

import com.tazzzo.app.data.catalog.CatalogFailure
import com.tazzzo.app.data.catalog.CatalogNode
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
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

@OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
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

    @Test fun aFailureIsRecordedNotMaskedAndIsNotRetriedOnEveryVisitWithinItsBackoff() = runTest {
        var fail = true; var loads = 0
        val h = HomeContentHolder(backgroundScope, {
            loads++; if (fail) throw ApiException(ApiError.Http(400, "INVALID_REQUEST", null, false, null)) else HomeContent.EMPTY
        }, { _, _ -> null }, pin, { now })
        h.ensure(); runCurrent()
        assertEquals(HomeContentState.Failed(CatalogFailure.InvalidRequest), h.state.value)
        repeat(5) { h.ensure(); runCurrent() }
        assertEquals(1, loads, "a backend that cannot serve the Home is not asked again on every visit to the tab")
        now += 61_000; h.ensure(); runCurrent()
        assertEquals(2, loads, "after the backoff it is asked once more")
        fail = false; h.refresh(); runCurrent()
        assertEquals(3, loads, "a pull asks now, whatever the backoff"); assertIs<HomeContentState.Content>(h.state.value)
    }

    @Test fun aFailureBacksOffFromTenSecondsDoublingAndCappedAtTheFreshnessWindow() = runTest {
        var loads = 0
        val h = HomeContentHolder(backgroundScope, { loads++; throw ApiException(ApiError.Network) }, { _, _ -> null }, pin, { now })
        h.ensure(); runCurrent(); assertEquals(1, loads)
        for ((i, wait) in listOf(10_000L, 20_000L, 40_000L, 60_000L, 60_000L).withIndex()) {
            now += wait - 1; h.ensure(); runCurrent()
            assertEquals(i + 1, loads, "not before ${wait} ms after failure ${i + 1}")
            now += 1; h.ensure(); runCurrent()
            assertEquals(i + 2, loads, "exactly ${wait} ms after failure ${i + 1}")
        }
    }

    @Test fun aSuccessResetsTheBackoff() = runTest {
        var fail = true; var loads = 0
        val h = HomeContentHolder(backgroundScope, { loads++; if (fail) throw ApiException(ApiError.Network) else HomeContent.EMPTY }, { _, _ -> null }, pin, { now })
        h.ensure(); runCurrent(); now += 10_000; h.ensure(); runCurrent(); now += 20_000; h.ensure(); runCurrent()
        assertEquals(3, loads)
        fail = false; now += 40_000; h.ensure(); runCurrent(); assertEquals(4, loads); assertIs<HomeContentState.Content>(h.state.value)
        fail = true; now += 60_000; h.ensure(); runCurrent(); assertEquals(5, loads)
        now += 10_000; h.ensure(); runCurrent(); assertEquals(6, loads, "the first failure after a success waits 10 s again, not 80")
    }

    @Test fun aRateLimitIsHonouredByTheBackoffAndByAPull() = runTest {
        var loads = 0; var limited = true
        val h = HomeContentHolder(backgroundScope, {
            loads++; if (limited) throw ApiException(ApiError.Http(429, "RATE_LIMITED", null, true, 30)) else HomeContent.EMPTY
        }, { _, _ -> null }, pin, { now })
        h.ensure(); runCurrent()
        assertEquals(HomeContentState.Failed(CatalogFailure.RateLimited(30)), h.state.value)
        now += 29_000; h.ensure(); h.refresh(); runCurrent()
        assertEquals(1, loads, "neither a visit nor a pull asks again inside Retry-After"); assertEquals(false, h.refreshing.value)
        limited = false; now += 1_000; h.refresh(); runCurrent()
        assertEquals(2, loads); assertIs<HomeContentState.Content>(h.state.value)
    }

    @Test fun aHugeRetryAfterIsCappedAtTwoMinutes() = runTest {
        var loads = 0; var limited = true
        val h = HomeContentHolder(backgroundScope, {
            loads++; if (limited) throw ApiException(ApiError.Http(429, "RATE_LIMITED", null, true, Long.MAX_VALUE)) else HomeContent.EMPTY
        }, { _, _ -> null }, pin, { now })
        h.ensure(); runCurrent(); assertEquals(1, loads)
        now += 119_999; h.ensure(); h.refresh(); runCurrent(); assertEquals(1, loads, "inside the capped window")
        limited = false; now += 1; h.ensure(); runCurrent()
        assertEquals(2, loads, "120 s later, not never (and no overflow into the past)"); assertIs<HomeContentState.Content>(h.state.value)
    }

    @Test fun aPullRefusedByA429WindowIsAnnounced() = runTest {
        val h = HomeContentHolder(backgroundScope, { throw ApiException(ApiError.Http(429, "RATE_LIMITED", null, true, 30)) }, { _, _ -> null }, pin, { now })
        val refused = mutableListOf<CatalogFailure>()
        backgroundScope.launch { h.pullRefused.collect { refused += it } }
        h.ensure(); runCurrent()
        h.refresh(); runCurrent()
        assertEquals(1, refused.size); assertIs<CatalogFailure.RateLimited>(refused.single()); assertEquals(false, h.refreshing.value)
    }

    @Test fun repeatedPullsCostAtMostOneCardSweepPerThirtySeconds() = runTest {
        val ids = (1..20).map { "TZP-$it" }
        val asked = mutableListOf<String>()
        val h = HomeContentHolder(backgroundScope, { HomeContent(listOf(HomeBlock.ProductRail("R1", "x", ids), HomeBlock.ProductRail("R2", "y", ids.reversed()))) },
            { id, _ -> asked += id; detail(id) }, pin, { now })
        h.ensure(); runCurrent(); assertEquals(20, asked.size, "first load reads each id once across both rails"); asked.clear()
        now += 40_000
        repeat(5) { h.refresh(); runCurrent(); now += 2_000 }          // five pulls in ten seconds
        assertEquals(ids.sorted(), asked.sorted(), "one sweep, not five"); asked.clear()
        repeat(5) { h.refresh() }; runCurrent()                       // pulls racing each other
        assertTrue(asked.isEmpty(), "cards younger than 30 s are not read again")
        now += 30_000; h.refresh(); runCurrent()
        assertEquals(20, asked.size, "after 30 s a pull sweeps again")
    }

    @Test fun aPullThatSupersedesASweepInFlightDoesNotReReadFinishedCards() = runTest {
        val ids = (1..8).map { "TZP-$it" }
        val asked = mutableListOf<String>()
        val gates = HashMap<String, CompletableDeferred<Unit>>()
        var gated = false
        val h = HomeContentHolder(backgroundScope, { HomeContent(listOf(HomeBlock.ProductRail("R1", "x", ids))) },
            { id, _ -> asked += id; if (gated) gates.getOrPut(id) { CompletableDeferred() }.await(); detail(id) }, pin, { now }, parallelism = 2)
        h.ensure(); runCurrent(); asked.clear()
        gated = true; now += 40_000; h.refresh(); runCurrent()
        gates.getValue("TZP-1").complete(Unit); gates.getValue("TZP-2").complete(Unit); runCurrent()
        h.refresh(); runCurrent()                                       // supersedes the sweep half-way
        gates.values.forEach { it.complete(Unit) }; (1..8).forEach { gates.getOrPut("TZP-$it") { CompletableDeferred() }.complete(Unit) }; runCurrent()
        assertTrue(asked.count { it == "TZP-1" } == 1 && asked.count { it == "TZP-2" } == 1, "finished cards are kept: $asked")
        assertTrue(asked.size <= ids.size + 2, "at most the in-flight reads are repeated: $asked")
        assertEquals(ids, (h.rails.value["R1"] as PagedState.Content).items.map { it.skuId })
    }

    @Test fun aPullShowsTheIndicatorUntilTheReadAnswersAndJoinsAReadInFlight() = runTest {
        val gate = CompletableDeferred<Unit>(); var loads = 0
        val h = HomeContentHolder(backgroundScope, { loads++; gate.await(); HomeContent.EMPTY }, { _, _ -> null }, pin, { now })
        h.ensure(); runCurrent()
        assertEquals(false, h.refreshing.value, "a background read never shows the pull indicator")
        h.refresh(); runCurrent()
        assertEquals(true, h.refreshing.value); assertEquals(1, loads, "the pull joins the read already in flight")
        gate.complete(Unit); runCurrent()
        assertEquals(false, h.refreshing.value); assertIs<HomeContentState.Content>(h.state.value)
    }

    @Test fun callsArePostedToTheHolderScopeNeverRunOnTheCallersThread() = runTest {
        var loads = 0
        val h = HomeContentHolder(backgroundScope, { loads++; HomeContent.EMPTY }, { _, _ -> null }, pin, { now })
        h.ensure(); h.refresh(); h.onPinChanged()
        assertEquals(0, loads); assertEquals(HomeContentState.Idle, h.state.value, "nothing ran on the caller's thread")
        runCurrent()
        assertEquals(1, loads)
    }

    @Test fun concurrentCallersFromManyThreadsNeverOverlapTwoReads() = runTest {
        val errors = mutableListOf<Throwable>()
        @OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
        val confined = kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.SupervisorJob() +
            kotlinx.coroutines.Dispatchers.Default.limitedParallelism(1) +
            kotlinx.coroutines.CoroutineExceptionHandler { _, e -> errors += e })
        var inFlight = 0; var peak = 0; var loads = 0
        val content = HomeContent(listOf(HomeBlock.ProductRail("R1", "x", (1..20).map { "TZP-$it" })))
        val h = HomeContentHolder(confined, {
            inFlight++; loads++; peak = maxOf(peak, inFlight); kotlinx.coroutines.yield(); inFlight--; content
        }, { id, _ -> kotlinx.coroutines.yield(); detail(id) }, pin, freshMs = 0L, retryBaseMs = 0L)
        kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Default) {
            (1..8).map { t ->
                launch { repeat(200) { i -> when ((t + i) % 3) { 0 -> h.ensure(); 1 -> h.refresh(); else -> h.onPinChanged() } } }
            }.forEach { it.join() }
            kotlinx.coroutines.withTimeout(10_000) {
                while (h.refreshing.value || (h.rails.value["R1"] as? PagedState.Content)?.items?.size != 20) kotlinx.coroutines.delay(5)
            }
        }
        confined.coroutineContext[kotlinx.coroutines.Job]?.cancel()
        assertEquals(1, peak, "reads never overlap"); assertTrue(loads >= 1); assertTrue(errors.isEmpty(), "no failure inside the holder: $errors")
    }

    @Test fun aStaleCopyStaysOnScreenWhileItIsReReadAndSurvivesAFailedReRead() = runTest {
        var fail = false
        val h = HomeContentHolder(backgroundScope, { if (fail) throw ApiException(ApiError.Network) else rail }, { id, _ -> detail(id) }, pin, { now })
        h.ensure(); runCurrent()
        assertIs<HomeContentState.Content>(h.state.value)
        now += 61_000; fail = true
        h.ensure()
        assertIs<HomeContentState.Content>(h.state.value, "the blocks do not blank out while the re-read is in flight")
        runCurrent()
        assertIs<HomeContentState.Content>(h.state.value, "a failed re-read keeps the published copy")
        assertIs<PagedState.Content<CatalogProduct>>(h.rails.value["R1"], "and its rails")
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

    @Test fun aReReadKeepsEveryRailOnScreenAndReadsOnlyTheIdsThatChanged() = runTest {
        var content = rail
        val asked = mutableListOf<String>()
        val h = HomeContentHolder(backgroundScope, { content }, { id, _ -> asked += id; detail(id) }, pin, { now })
        h.ensure(); runCurrent()
        val seen = mutableListOf<PagedState<CatalogProduct>?>()
        backgroundScope.launch { h.rails.collect { seen += it["R1"] } }
        runCurrent(); asked.clear()
        content = HomeContent(listOf(HomeBlock.ProductRail("R1", "Picks", listOf("TZP-1", "TZP-4", "TZP-3"))))
        now += 61_000; h.ensure(); runCurrent()
        assertEquals(listOf("TZP-4"), asked, "only the new id is read")
        assertTrue(seen.none { it !is PagedState.Content<*> }, "the rail never went back to loading: $seen")
        assertEquals(listOf("TZP-1", "TZP-4", "TZP-3"), (h.rails.value["R1"] as PagedState.Content).items.map { it.skuId })
        asked.clear(); now += 61_000; h.ensure(); runCurrent()
        assertTrue(asked.isEmpty(), "unchanged ids with fresh cards cost no product read")
    }

    @Test fun cardsOlderThanTheirFreshnessAreReReadSilentlyAndAPullReReadsThemAll() = runTest {
        val asked = mutableListOf<String>()
        val h = HomeContentHolder(backgroundScope, { rail }, { id, _ -> asked += id; detail(id) }, pin, { now }, cardFreshMs = 300_000L)
        h.ensure(); runCurrent(); asked.clear()
        val seen = mutableListOf<PagedState<CatalogProduct>?>()
        backgroundScope.launch { h.rails.collect { seen += it["R1"] } }
        now += 120_000; h.refresh(); runCurrent()
        assertEquals(listOf("TZP-1", "TZP-2", "TZP-3"), asked.sorted(), "a pull re-reads every card older than 30 s"); asked.clear()
        now += 299_000; h.ensure(); runCurrent(); assertTrue(asked.isEmpty(), "cards re-read by the pull are still fresh")
        now += 61_000; h.ensure(); runCurrent()
        assertEquals(listOf("TZP-1", "TZP-2", "TZP-3"), asked.sorted(), "the next re-read after the cards' freshness reads them again")
        assertTrue(seen.all { it is PagedState.Content<*> }, "and the rail never flashed: $seen")
    }

    @Test fun aFailedRevalidationKeepsTheRailOnScreen() = runTest {
        var content = rail; var broken = false
        val h = HomeContentHolder(backgroundScope, { content }, { id, _ -> if (broken) throw ApiException(ApiError.Network) else detail(id) }, pin, { now })
        h.ensure(); runCurrent()
        content = HomeContent(listOf(HomeBlock.ProductRail("R1", "Picks", listOf("TZP-1", "TZP-9")))); broken = true
        now += 61_000; h.ensure(); runCurrent()
        assertEquals(listOf("TZP-1", "TZP-2", "TZP-3"), (h.rails.value["R1"] as PagedState.Content).items.map { it.skuId })
    }

    @Test fun aRailRendersAllTwentyPublishedProducts() = runTest {
        val ids = (1..20).map { "TZP-$it" }
        val h = HomeContentHolder(backgroundScope, { HomeContent(listOf(HomeBlock.ProductRail("R1", "x", ids))) }, { id, _ -> detail(id) }, pin, { now })
        h.ensure(); runCurrent()
        assertEquals(ids, (h.rails.value["R1"] as PagedState.Content).items.map { it.skuId })
    }

    @Test fun gridsAreNamedAtAnyLevelSkipUnknownTilesAndKeepNamesOverAFailedResolution() = runTest {
        val grid = HomeContent(listOf(HomeBlock.CategoryGrid("G1", "Aisles", listOf("TZV-000037", "TZC-000002", "TZS-000001"))))
        var known = mapOf("TZV-000037" to CatalogNode("TZV-000037", "Basmati"), "TZS-000001" to CatalogNode("TZS-000001", "Staples"))
        val asked = mutableListOf<Collection<String>>()
        val h = HomeContentHolder(backgroundScope, { grid }, { _, _ -> null }, pin, { now }, resolveNodes = { ids -> asked += ids; known.filterKeys { it in ids } })
        h.ensure(); runCurrent()
        assertEquals(listOf("Basmati", "Staples"), h.grids.value["G1"]?.map { it.name }, "published order; the unnamed tile is skipped, not the grid")
        assertEquals(listOf(listOf("TZV-000037", "TZC-000002", "TZS-000001")), asked.map { it.toList() }, "one resolution for all grid ids")
        known = emptyMap(); now += 61_000; h.ensure(); runCurrent()
        assertEquals(listOf("Basmati", "Staples"), h.grids.value["G1"]?.map { it.name }, "a failed re-resolution keeps the names on screen")
    }

    @Test fun aPinChangeReloadsTheRailsForTheNewPin() = runTest {
        val asked = mutableListOf<Pincode?>()
        val h = HomeContentHolder(backgroundScope, { rail }, { id, p -> asked += p; detail(id) }, pin, { now })
        h.ensure(); runCurrent()
        h.onPinChanged(); runCurrent()
        assertEquals(3, asked.size, "same PIN: no reload")
        pin.value = Pincode.parse("560001")!!; h.onPinChanged()
        runCurrent()
        assertEquals(6, asked.size); assertEquals(pin.value, asked.last())
    }

    @Test fun aPinChangeShowsTheRailLoadingRatherThanCardsPricedForTheOldPin() = runTest {
        val gate = CompletableDeferred<Unit>(); var gated = false
        val h = HomeContentHolder(backgroundScope, { rail }, { id, _ -> if (gated) gate.await(); detail(id) }, pin, { now })
        h.ensure(); runCurrent()
        gated = true; pin.value = Pincode.parse("560001")!!; h.onPinChanged(); runCurrent()
        assertEquals(PagedState.LoadingFirst, h.rails.value["R1"])
        gate.complete(Unit); runCurrent()
        assertIs<PagedState.Content<CatalogProduct>>(h.rails.value["R1"])
    }
}
