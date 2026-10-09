package com.tazzzo.app.content

import com.tazzzo.app.data.catalog.CatalogNode
import com.tazzzo.app.data.content.CategoryNodeResolver
import com.tazzzo.app.data.remote.ApiError
import com.tazzzo.app.data.remote.ApiException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** A grid id at ANY taxonomy level is named with ONE `GET /v1/categories/{id}` — cached, budgeted, and 429-aware. */
class CategoryNodeResolverTest {
    private val visible = mutableSetOf("TZS-000001", "TZC-000011", "TZG-000121", "TZV-001211")
    private val asked = mutableListOf<String>()
    private val failing = mutableSetOf<String>()
    private var failure: ApiError = ApiError.Network
    private var now = 1_000_000L

    private fun resolver(max: Int = CategoryNodeResolver.DEFAULT_MAX_REQUESTS) = CategoryNodeResolver(
        node = { id ->
            asked += id
            if (id in failing) throw ApiException(failure)
            if (id in visible) CatalogNode(id, "Name $id") else null
        },
        maxRequests = max, nowMs = { now }
    )

    private fun rateLimited(retryAfter: Long?) = ApiError.Http(429, "RATE_LIMITED", null, true, retryAfter)

    @Test fun everyLevelIsNamedWithOneReadPerIdInPublishedOrder() = runTest {
        val ids = listOf("TZV-001211", "TZC-000011", "TZS-000001", "TZG-000121")
        val r = resolver().resolve(ids)
        assertEquals(ids, asked, "one read per id, sequential, published order — no root or children walk")
        assertEquals(ids, r.keys.toList())
        assertEquals("Name TZV-001211", r["TZV-001211"]?.name)
    }

    @Test fun aNamedNodeIsServedFromMemoryForFiveMinutes() = runTest {
        val res = resolver()
        res.resolve(listOf("TZC-000011")); asked.clear()
        now += 299_000
        assertEquals("Name TZC-000011", res.resolve(listOf("TZC-000011"))["TZC-000011"]?.name)
        assertTrue(asked.isEmpty(), "a Home re-read inside max-age costs nothing")
        now += 1_000
        res.resolve(listOf("TZC-000011")); assertEquals(listOf("TZC-000011"), asked, "then it is read again (a rename shows)")
    }

    @Test fun a404IsAnsweredAsGoneAndIsNotReadAgainForFiveMinutes() = runTest {
        val res = resolver()
        val r = res.resolve(listOf("TZC-999999", "TZS-000001"))
        assertTrue("TZC-999999" in r.keys); assertNull(r["TZC-999999"], "404 = gone: the holder drops the tile")
        assertEquals("Name TZS-000001", r["TZS-000001"]?.name, "a 404 does not end the call")
        asked.clear(); now += 299_000
        val again = res.resolve(listOf("TZC-999999"))
        assertTrue(asked.isEmpty()); assertTrue("TZC-999999" in again.keys); assertNull(again["TZC-999999"])
        now += 1_000; res.resolve(listOf("TZC-999999")); assertEquals(listOf("TZC-999999"), asked)
    }

    @Test fun malformedAndDuplicateIdsCostNothing() = runTest {
        val r = resolver().resolve(listOf("TZP-1", "bogus", "TZC-1", "../x", "TZS-000001", "TZS-000001"))
        assertEquals(listOf("TZS-000001"), asked); assertEquals(setOf("TZS-000001"), r.keys)
    }

    @Test fun theBudgetBoundsOneCallAndIdsBeyondItAreReadByTheNextOne() = runTest {
        val ids = (1..14).map { "TZG-" + (100_000 + it) }
        visible += ids
        val res = resolver()
        assertEquals(12, res.resolve(ids).size); assertEquals(ids.take(12), asked)
        asked.clear()
        val r = res.resolve(ids)
        assertEquals(ids.drop(12), asked, "the two left over are not penalised, and the twelve named are cached")
        assertEquals(14, r.size)
    }

    @Test fun aFailedReadEndsTheCallKeepsWhatWasFoundAndIsRetriedAfterAMinute() = runTest {
        failing += "TZC-000011"
        val res = resolver()
        val r = res.resolve(listOf("TZS-000001", "TZC-000011", "TZG-000121"))
        assertEquals(setOf("TZS-000001"), r.keys, "neither failed nor unread ids are guessed (nor reported gone)")
        assertEquals(listOf("TZS-000001", "TZC-000011"), asked, "no read after the failure")
        asked.clear(); now += 59_000
        res.resolve(listOf("TZC-000011", "TZG-000121")); assertTrue(asked.isEmpty(), "inside the backoff nothing is re-read")
        res.resolve(listOf("TZV-001211")); assertEquals(listOf("TZV-001211"), asked, "a non-429 failure does not block other ids")
        asked.clear(); failing.clear(); now += 1_000
        assertEquals(setOf("TZC-000011", "TZG-000121"), res.resolve(listOf("TZC-000011", "TZG-000121")).keys)
    }

    @Test fun a429StopsEveryReadForItsRetryAfter() = runTest {
        failure = rateLimited(90); failing += "TZC-000011"
        val res = resolver()
        res.resolve(listOf("TZC-000011", "TZG-000121")); assertEquals(listOf("TZC-000011"), asked)
        failing.clear(); asked.clear(); now += 89_000
        res.resolve(listOf("TZV-001211")); assertTrue(asked.isEmpty(), "inside Retry-After not even a new id is read")
        now += 1_000
        assertEquals(setOf("TZV-001211", "TZC-000011", "TZG-000121"), res.resolve(listOf("TZV-001211", "TZC-000011", "TZG-000121")).keys)
    }

    @Test fun aHostileRetryAfterIsCappedAtTwoMinutes() = runTest {
        failure = rateLimited(86_400); failing += "TZS-000001"
        val res = resolver()
        res.resolve(listOf("TZS-000001")); failing.clear(); asked.clear()
        now += 119_000; res.resolve(listOf("TZS-000001")); assertTrue(asked.isEmpty())
        now += 1_000; assertEquals("Name TZS-000001", res.resolve(listOf("TZS-000001"))["TZS-000001"]?.name)
    }

    @Test fun a429WithoutOrWithAShortRetryAfterStillWaitsTheBackoffMinute() = runTest {
        for (retryAfter in listOf(null, 0L, 5L)) {
            failure = rateLimited(retryAfter); failing += "TZS-000001"; asked.clear()
            val res = resolver()
            res.resolve(listOf("TZS-000001")); failing.clear()
            now += 59_000; res.resolve(listOf("TZC-000011")); assertEquals(listOf("TZS-000001"), asked, "Retry-After=$retryAfter")
            now += 1_000; res.resolve(listOf("TZC-000011")); assertEquals(listOf("TZS-000001", "TZC-000011"), asked)
        }
    }

    @Test fun cancellationIsNotSwallowedAsAFailure() = runTest {
        val res = CategoryNodeResolver(node = { throw CancellationException("gone") }, nowMs = { now })
        assertFailsWith<CancellationException> { res.resolve(listOf("TZS-000001")) }
    }
}
