package com.tazzzo.app.content

import com.tazzzo.app.data.catalog.CatalogNode
import com.tazzzo.app.data.catalog.TaxonomyPage
import com.tazzzo.app.data.content.CategoryNodeResolver
import com.tazzzo.app.data.remote.ApiError
import com.tazzzo.app.data.remote.ApiException
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** A grid id at ANY taxonomy level is named through the public reads the app already makes — within a request budget. */
class CategoryNodeResolverTest {
    // TZS-1 → TZC-11, TZC-12 ; TZS-2 → TZC-21 ; TZC-12 → TZG-121 ; TZG-121 → TZV-1211
    private val tree = mapOf(
        "TZS-000001" to listOf("TZC-000011", "TZC-000012"),
        "TZS-000002" to listOf("TZC-000021"),
        "TZC-000011" to emptyList(),
        "TZC-000012" to listOf("TZG-000121"),
        "TZC-000021" to emptyList(),
        "TZG-000121" to listOf("TZV-001211"),
        "TZV-001211" to emptyList()
    )
    private fun page(ids: List<String>) = TaxonomyPage("rel_1", ids.map { CatalogNode(it, "Name $it") })
    private val asked = mutableListOf<String>()
    private val failing = mutableSetOf<String>()
    private var rootFails = false

    private fun resolver(max: Int = CategoryNodeResolver.DEFAULT_MAX_REQUESTS) = CategoryNodeResolver(
        root = { if (rootFails) throw ApiException(ApiError.Network) else page(listOf("TZS-000001", "TZS-000002")) },
        children = { id -> asked += id; if (id in failing) throw ApiException(ApiError.Network) else page(tree.getValue(id)) },
        maxRequests = max
    )

    @Test fun rootIdsNeedNoChildrenRead() = runTest {
        val r = resolver().resolve(listOf("TZS-000002"))
        assertEquals(CatalogNode("TZS-000002", "Name TZS-000002"), r["TZS-000002"]); assertTrue(asked.isEmpty())
    }

    @Test fun aCategoryIsFoundUnderItsSectionAndTheWalkStopsAsSoonAsEveryIdIsNamed() = runTest {
        val r = resolver().resolve(listOf("TZC-000012"))
        assertEquals("Name TZC-000012", r["TZC-000012"]?.name)
        assertEquals(listOf("TZS-000001"), asked, "found under the first section: the second is never read")
    }

    @Test fun aVerticalIsResolvedLevelByLevelOnlyThroughNodesOfTheLevelAbove() = runTest {
        val r = resolver().resolve(listOf("TZV-001211", "TZS-000001", "TZC-000021"))
        assertEquals(setOf("TZV-001211", "TZS-000001", "TZC-000021"), r.keys)
        assertEquals(listOf("TZS-000001", "TZS-000002", "TZC-000011", "TZC-000012", "TZC-000021", "TZG-000121"), asked)
        assertTrue("TZV-001211" !in asked, "a vertical's own children are never read")
    }

    @Test fun theRequestBudgetIsHonouredAndWhatIsNotFoundInItIsSimplyUnresolved() = runTest {
        val r = resolver(max = 2).resolve(listOf("TZV-001211", "TZC-000021"))
        assertEquals(2, asked.size)
        assertEquals(setOf("TZC-000021"), r.keys, "TZC found within the budget; the vertical is skipped, not guessed")
    }

    @Test fun aFailedBranchSkipsOnlyItsIdsAndAFailedRootResolvesNothing() = runTest {
        failing += "TZS-000001"
        val r = resolver().resolve(listOf("TZC-000011", "TZC-000021"))
        assertEquals(setOf("TZC-000021"), r.keys)
        rootFails = true
        assertTrue(resolver().resolve(listOf("TZS-000001")).isEmpty())
    }

    @Test fun anUnknownOrMalformedIdCostsNoWalk() = runTest {
        assertTrue(resolver().resolve(listOf("TZP-1", "bogus")).isEmpty()); assertTrue(asked.isEmpty())
        val r = resolver().resolve(listOf("TZC-999999"))
        assertTrue(r.isEmpty()); assertEquals(listOf("TZS-000001", "TZS-000002"), asked, "an absent category stops at its level")
    }

    @Test fun levelsFollowTheBackendPrefixes() {
        assertEquals(listOf(1, 2, 3, 4, 0, 0), listOf("TZS-000001", "TZC-000001", "TZG-000001", "TZV-000001", "TZX-000001", "TZV-1")
            .map { CategoryNodeResolver.levelOf(it) })
    }
}
