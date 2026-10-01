package com.tazzzo.app.catalog

import com.tazzzo.app.data.catalog.CatalogFailure
import com.tazzzo.app.data.catalog.Pincode
import com.tazzzo.app.data.catalog.toCatalogFailure
import com.tazzzo.app.data.remote.ApiError
import com.tazzzo.app.data.remote.ApiException
import io.ktor.client.engine.mock.respond
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue

class CatalogErrorsTest {
    private val pin = Pincode.parse("560047")!!

    private suspend fun failureOf(status: HttpStatusCode, body: String, headers: io.ktor.http.Headers = JSON): CatalogFailure {
        val ds = dataSource { respond(body, status, headers) }
        return assertFailsWith<ApiException> { ds.products("TZC-000010", pin) }.toCatalogFailure()
    }

    private suspend fun httpOf(status: HttpStatusCode, body: String, headers: io.ktor.http.Headers = JSON): ApiError.Http {
        val ds = dataSource { respond(body, status, headers) }
        return assertIs(assertFailsWith<ApiException> { ds.products("TZC-000010", pin) }.error)
    }

    @Test fun invalidRequest400() = runTest {
        val e = httpOf(HttpStatusCode.BadRequest, errorFlat("INVALID_REQUEST"))
        assertEquals(400, e.status); assertEquals("INVALID_REQUEST", e.code); assertEquals("req_e", e.requestId); assertEquals(false, e.retryable)
        assertEquals(CatalogFailure.InvalidRequest, failureOf(HttpStatusCode.BadRequest, errorFlat("INVALID_REQUEST")))
    }

    @Test fun invalidCursor() = runTest {
        assertEquals(CatalogFailure.InvalidCursor, failureOf(HttpStatusCode.BadRequest, errorFlat("INVALID_CURSOR")))
    }

    @Test fun notFound404() = runTest {
        assertEquals(CatalogFailure.NotFound, failureOf(HttpStatusCode.NotFound, errorFlat("NOT_FOUND")))
    }

    @Test fun rateLimited429CarriesRetryAfterFromBodyOrHeader() = runTest {
        val body = httpOf(HttpStatusCode.TooManyRequests, errorFlat("RATE_LIMITED", true, 12))
        assertEquals(12L, body.retryAfterSeconds); assertEquals(true, body.retryable)
        assertEquals(CatalogFailure.RateLimited(12), failureOf(HttpStatusCode.TooManyRequests, errorFlat("RATE_LIMITED", true, 12)))
        // header only (body without retryAfterSeconds)
        val hdr = headersOf(HttpHeaders.ContentType to listOf("application/json"), HttpHeaders.RetryAfter to listOf("30"))
        assertEquals(CatalogFailure.RateLimited(30), failureOf(HttpStatusCode.TooManyRequests, errorFlat("RATE_LIMITED", true), hdr))
        assertTrue(CatalogFailure.RateLimited(1).isRetryable)
    }

    @Test fun serviceUnavailable503HasNoRetryHint() = runTest {
        val e = httpOf(HttpStatusCode.ServiceUnavailable, errorFlat("SERVICE_UNAVAILABLE", true))
        assertNull(e.retryAfterSeconds)
        assertEquals(CatalogFailure.Unavailable, failureOf(HttpStatusCode.ServiceUnavailable, errorFlat("SERVICE_UNAVAILABLE", true)))
        assertTrue(CatalogFailure.Unavailable.isRetryable)
    }

    @Test fun internal500IsServer() = runTest {
        assertEquals(CatalogFailure.Server, failureOf(HttpStatusCode.InternalServerError, errorFlat("INTERNAL")))
    }

    @Test fun legacyNestedEnvelopeIsParsed() = runTest {
        val e = httpOf(HttpStatusCode.NotFound, LEGACY_404)
        assertEquals(404, e.status); assertEquals("NO_SUCH_ENDPOINT", e.code); assertEquals("req_l", e.requestId)
        val mna = httpOf(HttpStatusCode.MethodNotAllowed, """{"error":{"code":"METHOD_NOT_ALLOWED","message":"x","request_id":"req_m"}}""")
        assertEquals("METHOD_NOT_ALLOWED", mna.code)
    }

    @Test fun currentFlatEnvelopeStillWinsWhenBothCouldApply() = runTest {
        val e = httpOf(HttpStatusCode.BadRequest, """{"code":"INVALID_REQUEST","requestId":"r1","error":{"code":"OTHER"}}""")
        assertEquals("INVALID_REQUEST", e.code)
    }

    @Test fun garbageErrorBodiesStillYieldAStatus() = runTest {
        assertEquals(CatalogFailure.Unavailable, failureOf(HttpStatusCode.ServiceUnavailable, "<html>bad gateway</html>"))
        assertEquals(CatalogFailure.Unknown, failureOf(HttpStatusCode.Forbidden, ""))
    }

    @Test fun transportFailures() = runTest {
        val ds = dataSource { throw RuntimeException("offline") }
        assertEquals(CatalogFailure.Network, assertFailsWith<ApiException> { ds.products("TZC-000010", pin) }.toCatalogFailure())
        assertEquals(CatalogFailure.Unknown, RuntimeException("x").toCatalogFailure())
        assertFalse(CatalogFailure.InvalidRequest.isRetryable); assertFalse(CatalogFailure.NotFound.isRetryable)
    }

    // ---- reader semantics: 404 = nothing here, everything else propagates --------------------------

    @Test fun aCategoryList404IsAnEmptyPage() = runTest {
        val page = reader { respond(errorFlat("NOT_FOUND"), HttpStatusCode.NotFound, JSON) }.productPage("TZC-000010", pin, null)
        assertTrue(page.items.isEmpty()); assertFalse(page.hasMore)
    }

    @Test fun otherListErrorsPropagateAndNeverBecomeEmpty() = runTest {
        for (status in listOf(HttpStatusCode.TooManyRequests, HttpStatusCode.ServiceUnavailable, HttpStatusCode.BadRequest)) {
            val r = reader { respond(errorFlat("X"), status, JSON) }
            assertFailsWith<ApiException>("$status") { r.productPage("TZC-000010", pin, null) }
        }
    }

    @Test fun pdp404IsNullButOtherFailuresPropagate() = runTest {
        assertNull(reader { respond(errorFlat("NOT_FOUND"), HttpStatusCode.NotFound, JSON) }.product("TZP-1", pin))
        assertFailsWith<ApiException> { reader { respond(errorFlat("SERVICE_UNAVAILABLE"), HttpStatusCode.ServiceUnavailable, JSON) }.product("TZP-1", pin) }
    }
}
