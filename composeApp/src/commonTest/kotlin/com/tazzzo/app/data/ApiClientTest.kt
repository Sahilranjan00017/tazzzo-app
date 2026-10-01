package com.tazzzo.app.data

import com.tazzzo.app.data.remote.AccessTokenProvider
import com.tazzzo.app.data.remote.ApiClient
import com.tazzzo.app.data.remote.ApiError
import com.tazzzo.app.data.remote.ApiException
import com.tazzzo.app.data.remote.ApiRequest
import com.tazzzo.app.data.remote.IdempotencyKey
import com.tazzzo.app.data.remote.IfMatch
import com.tazzzo.app.data.remote.ItemError
import com.tazzzo.app.data.remote.execute
import com.tazzzo.app.ui.state.LoadError
import com.tazzzo.app.ui.state.toLoadError
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.MockRequestHandleScope
import io.ktor.client.engine.mock.respond
import io.ktor.client.network.sockets.ConnectTimeoutException
import io.ktor.client.plugins.HttpRequestTimeoutException
import io.ktor.client.request.HttpRequestData
import io.ktor.client.request.HttpResponseData
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpMethod
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue

@Serializable
private data class Cat(val id: String, val name: String)

class ApiClientTest {
    private val jsonHeaders = headersOf(HttpHeaders.ContentType, "application/json")

    private fun client(
        token: AccessTokenProvider? = null,
        handler: suspend MockRequestHandleScope.(HttpRequestData) -> HttpResponseData
    ) = ApiClient(baseUrl = "https://api.example.test", tokenProvider = token, engine = MockEngine(handler))

    private fun get(path: String = "/v1/categories", authenticated: Boolean = true) =
        ApiRequest(HttpMethod.Get, path, authenticated = authenticated)

    private suspend fun errorOf(c: ApiClient, request: ApiRequest = get()): ApiError =
        assertFailsWith<ApiException> { c.execute<Cat>(request) }.error

    private suspend fun httpError(
        status: HttpStatusCode,
        body: String,
        headers: io.ktor.http.Headers = jsonHeaders
    ): ApiError.Http = assertIs(errorOf(client { respond(body, status, headers) }))

    // --- success + decoding ---------------------------------------------------

    @Test fun decodesSuccessAndIgnoresUnknownFields() = runTest {
        val c = client {
            respond("""{"id":"c1","name":"Dairy","somethingNew":{"x":1}}""", HttpStatusCode.OK,
                headersOf(HttpHeaders.ContentType to listOf("application/json"), HttpHeaders.ETag to listOf("\"cart-3\"")))
        }
        val r = c.execute<Cat>(get())
        assertEquals(Cat("c1", "Dairy"), r.body)
        assertEquals(200, r.status)
        assertEquals("\"cart-3\"", r.etag)
    }

    @Test fun malformedSuccessBodyIsADecodingError() = runTest {
        val e = errorOf(client { respond("""{"id": 5}""", HttpStatusCode.OK, jsonHeaders) })
        assertIs<ApiError.Decoding>(e)
    }

    @Test fun noContentResponseSucceedsForUnitCalls() = runTest {
        val c = client { respond("", HttpStatusCode.NoContent) }
        assertEquals(204, c.executeUnit(ApiRequest(HttpMethod.Post, "/v1/auth/logout")).status)
    }

    @Test fun buildsUrlFromBaseAndQuery() = runTest {
        var seen: String? = null
        val c = client { seen = it.url.toString(); respond("""{"id":"a","name":"b"}""", HttpStatusCode.OK, jsonHeaders) }
        c.execute<Cat>(ApiRequest(HttpMethod.Get, "/v1/serviceability", query = mapOf("pin" to "560047", "skip" to null)))
        assertEquals("https://api.example.test/v1/serviceability?pin=560047", seen)
    }

    // --- error statuses -------------------------------------------------------

    @Test fun status400() {
        runTest {
            val e = httpError(HttpStatusCode.BadRequest, """{"code":"INVALID_REQUEST","message":"secret internals","requestId":"req-1"}""")
            assertEquals(400, e.status); assertEquals("INVALID_REQUEST", e.code); assertEquals("req-1", e.requestId)
        }
    }

    @Test fun status401() = runTest {
        val e = httpError(HttpStatusCode.Unauthorized, """{"code":"UNAUTHENTICATED","message":"x","requestId":"r"}""")
        assertEquals(401, e.status); assertEquals("UNAUTHENTICATED", e.code)
    }

    @Test fun status404() = runTest {
        assertEquals("NOT_FOUND", httpError(HttpStatusCode.NotFound, """{"code":"NOT_FOUND","requestId":"r"}""").code)
    }

    @Test fun status409PreservesCodeAndStructuredItems() = runTest {
        val e = httpError(
            HttpStatusCode.Conflict,
            """{"code":"CHECKOUT_ITEM_UNAVAILABLE","message":"m","requestId":"req-9",
               "items":[{"skuId":"TZP-12","reason":"OUT_OF_STOCK"},{"skuId":"TZP-13","reason":"INSUFFICIENT_STOCK","extra":1},{"junk":true}]}"""
        )
        assertEquals(409, e.status)
        assertEquals("CHECKOUT_ITEM_UNAVAILABLE", e.code)
        assertEquals("req-9", e.requestId)
        assertEquals(listOf(ItemError("TZP-12", "OUT_OF_STOCK"), ItemError("TZP-13", "INSUFFICIENT_STOCK")), e.items)
    }

    @Test fun status409OrderConflictCodes() = runTest {
        for (code in listOf("PRICE_CHANGED", "STOCK_UNAVAILABLE", "NOT_SERVICEABLE", "ADDRESS_CHANGED",
            "RESERVATION_EXPIRED", "CART_VERSION_ALREADY_PURCHASED", "PRODUCT_UNAVAILABLE")) {
            assertEquals(code, httpError(HttpStatusCode.Conflict, """{"code":"$code","requestId":"r"}""").code)
        }
    }

    @Test fun status410() = runTest {
        val e = httpError(HttpStatusCode.Gone, """{"code":"QUOTE_EXPIRED","requestId":"r"}""")
        assertEquals(410, e.status); assertEquals("QUOTE_EXPIRED", e.code)
    }

    @Test fun status412And428() = runTest {
        assertEquals("PRECONDITION_FAILED", httpError(HttpStatusCode.PreconditionFailed, """{"code":"PRECONDITION_FAILED","requestId":"r"}""").code)
        val e = httpError(HttpStatusCode(428, "Precondition Required"), """{"code":"PRECONDITION_REQUIRED","requestId":"r"}""")
        assertEquals(428, e.status); assertEquals("PRECONDITION_REQUIRED", e.code)
    }

    @Test fun status429UsesBodyRetryAfterOverHeader() = runTest {
        val e = httpError(
            HttpStatusCode.TooManyRequests,
            """{"code":"OTP_RATE_LIMITED","requestId":"r","retryAfterSeconds":42}""",
            headersOf(HttpHeaders.ContentType to listOf("application/json"), HttpHeaders.RetryAfter to listOf("7"))
        )
        assertEquals(42L, e.retryAfterSeconds)
    }

    @Test fun status429FallsBackToRetryAfterHeader() = runTest {
        val e = httpError(
            HttpStatusCode.TooManyRequests, """{"code":"RATE_LIMITED","requestId":"r"}""",
            headersOf(HttpHeaders.ContentType to listOf("application/json"), HttpHeaders.RetryAfter to listOf("7"))
        )
        assertEquals(7L, e.retryAfterSeconds)
    }

    @Test fun status500And503PreserveRetryable() = runTest {
        val e500 = httpError(HttpStatusCode.InternalServerError, """{"code":"INTERNAL","requestId":"r"}""")
        assertEquals(500, e500.status); assertNull(e500.retryable)
        val e503 = httpError(HttpStatusCode.ServiceUnavailable, """{"code":"SERVICE_UNAVAILABLE","requestId":"r","retryable":true,"retryAfterSeconds":5}""")
        assertEquals(true, e503.retryable); assertEquals(5L, e503.retryAfterSeconds)
        assertEquals(false, httpError(HttpStatusCode.NotFound, """{"code":"NOT_FOUND","retryable":false}""").retryable)
    }

    // --- tolerant envelopes ---------------------------------------------------

    @Test fun legacyCatalogEnvelopeUsesSnakeCaseRequestId() = runTest {
        assertEquals("legacy-1", httpError(HttpStatusCode.NotFound, """{"code":"NOT_FOUND","message":"m","request_id":"legacy-1"}""").requestId)
    }

    @Test fun emptyNonJsonAndNonObjectBodiesStillYieldStatus() = runTest {
        for (body in listOf("", "<html>Bad Gateway</html>", "[1,2]", "\"str\"", "null")) {
            val e = httpError(HttpStatusCode.BadGateway, body)
            assertEquals(502, e.status); assertNull(e.code); assertNull(e.requestId); assertTrue(e.items.isEmpty())
        }
    }

    @Test fun wronglyTypedFieldsAreIgnoredNotFatal() = runTest {
        val e = httpError(HttpStatusCode.Conflict,
            """{"code":123,"requestId":{"a":1},"retryable":"yes","retryAfterSeconds":"soon","items":"nope"}""")
        assertNull(e.code); assertNull(e.requestId); assertNull(e.retryable); assertNull(e.retryAfterSeconds); assertTrue(e.items.isEmpty())
    }

    @Test fun freeTextCannotSmuggleThroughCodeOrRequestId() = runTest {
        val e = httpError(HttpStatusCode.BadRequest, """{"code":"phone +919999999999 invalid","requestId":"a b c"}""")
        assertNull(e.code); assertNull(e.requestId)
        assertFalse(ApiException(e).message.orEmpty().contains("9999"))
    }

    // --- transport failures ---------------------------------------------------

    @Test fun timeoutsAreClassifiedAsTimeout() = runTest {
        val url = "https://api.example.test/v1/categories"
        assertEquals(ApiError.Timeout, errorOf(client { throw HttpRequestTimeoutException(url, 1L) }))
        assertEquals(ApiError.Timeout, errorOf(client { throw ConnectTimeoutException("connect timed out") }))
        // A timeout wrapped in a cancellation (how Ktor can surface it) is still a timeout.
        assertEquals(ApiError.Timeout, errorOf(client { throw kotlinx.coroutines.CancellationException("x", HttpRequestTimeoutException(url, 1L)) }))
    }

    @Test fun otherTransportFailuresAreNetwork() = runTest {
        assertEquals(ApiError.Network, errorOf(client { throw RuntimeException("connection reset by peer") }))
    }

    // --- headers --------------------------------------------------------------

    @Test fun bearerOmittedWhenNoProviderOrNoToken() = runTest {
        var header: String? = "unset"
        val capture: suspend MockRequestHandleScope.(HttpRequestData) -> HttpResponseData = {
            header = it.headers[HttpHeaders.Authorization]; respond("""{"id":"a","name":"b"}""", HttpStatusCode.OK, jsonHeaders)
        }
        client(null, capture).execute<Cat>(get()); assertNull(header)
        header = "unset"; client({ null }, capture).execute<Cat>(get()); assertNull(header)
        header = "unset"; client({ "  " }, capture).execute<Cat>(get()); assertNull(header)
    }

    @Test fun bearerAddedWhenTokenPresent() = runTest {
        var header: String? = null
        val c = client({ "tok-123" }) { header = it.headers[HttpHeaders.Authorization]; respond("""{"id":"a","name":"b"}""", HttpStatusCode.OK, jsonHeaders) }
        c.execute<Cat>(get()); assertEquals("Bearer tok-123", header)
    }

    @Test fun anonymousRequestsNeverConsultTheTokenProvider() = runTest {
        var consulted = false
        var header: String? = null
        val c = client({ consulted = true; "tok" }) { header = it.headers[HttpHeaders.Authorization]; respond("""{"id":"a","name":"b"}""", HttpStatusCode.OK, jsonHeaders) }
        c.execute<Cat>(get(authenticated = false))
        assertFalse(consulted); assertNull(header)
    }

    @Test fun ifMatchAndIdempotencyKeyAreSent() = runTest {
        var ifMatch: String? = null; var key: String? = null; var method: HttpMethod? = null; var ct: String? = null
        val c = client { ifMatch = it.headers[HttpHeaders.IfMatch]; key = it.headers["Idempotency-Key"]; method = it.method
            ct = it.body.contentType?.toString(); respond("""{"id":"a","name":"b"}""", HttpStatusCode.OK, jsonHeaders) }
        c.execute<Cat>(ApiRequest(HttpMethod.Post, "/v1/customer/checkout/quote",
            body = JsonObject(mapOf("addressId" to JsonPrimitive("ADDR_abc123"))),
            ifMatch = IfMatch.of(IfMatch.CART, 7), idempotencyKey = "abcdef12-key"))
        assertEquals("\"cart-7\"", ifMatch); assertEquals("abcdef12-key", key); assertEquals(HttpMethod.Post, method)
        assertTrue(ct.orEmpty().startsWith("application/json"))
    }

    @Test fun invalidIdempotencyKeyIsRejectedBeforeSending() = runTest {
        var called = false
        val c = client { called = true; respond("{}", HttpStatusCode.OK, jsonHeaders) }
        assertFailsWith<IllegalArgumentException> {
            c.execute<Cat>(ApiRequest(HttpMethod.Post, "/x", idempotencyKey = "short"))
        }
        assertFalse(called)
    }

    @Test fun headerHelpers() {
        assertEquals("\"cart-0\"", IfMatch.of(IfMatch.CART, 0))
        assertEquals("\"address-12\"", IfMatch.of(IfMatch.ADDRESS, 12))
        assertEquals("\"profile-1\"", IfMatch.of(IfMatch.PROFILE, 1))
        assertEquals("\"cart-3\"", IfMatch.fromEtag("\"cart-3\""))
        assertFailsWith<IllegalArgumentException> { IfMatch.of("cart", -1) }
        assertTrue(IdempotencyKey.isValid("abcdEFGH-_12"))
        assertFalse(IdempotencyKey.isValid("short")); assertFalse(IdempotencyKey.isValid("has space 123")); assertFalse(IdempotencyKey.isValid("x".repeat(65)))
        val generated = IdempotencyKey.generate()
        assertTrue(IdempotencyKey.isValid(generated)); assertFalse(generated == IdempotencyKey.generate())
    }

    // --- UiState mapping ------------------------------------------------------

    @Test fun loadErrorMappingIsTypedNotTextBased() {
        fun kind(t: Throwable) = t.toLoadError().kind
        assertEquals(LoadError.Kind.Timeout, kind(ApiException(ApiError.Timeout)))
        assertEquals(LoadError.Kind.Network, kind(ApiException(ApiError.Network)))
        assertEquals(LoadError.Kind.Unauthorized, kind(ApiException(ApiError.Http(401))))
        assertEquals(LoadError.Kind.Unauthorized, kind(ApiException(ApiError.Http(403))))
        assertEquals(LoadError.Kind.Server, kind(ApiException(ApiError.Http(500))))
        assertEquals(LoadError.Kind.Server, kind(ApiException(ApiError.Http(503))))
        assertEquals(LoadError.Kind.Server, kind(ApiException(ApiError.Http(429))))
        assertEquals(LoadError.Kind.Unknown, kind(ApiException(ApiError.Http(404))))
        assertEquals(LoadError.Kind.Unknown, kind(ApiException(ApiError.Http(409, "PRICE_CHANGED"))))
        assertEquals(LoadError.Kind.Unknown, kind(ApiException(ApiError.Decoding())))
        // Message text is no longer interpreted.
        assertEquals(LoadError.Kind.Unknown, kind(RuntimeException("timeout while connecting to network, 401 server 500")))
    }
}
