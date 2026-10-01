package com.tazzzo.app.data.remote

import com.tazzzo.app.config.AppEnvironment
import io.ktor.client.HttpClient
import io.ktor.client.engine.HttpClientEngine
import io.ktor.client.network.sockets.ConnectTimeoutException
import io.ktor.client.network.sockets.SocketTimeoutException
import io.ktor.client.plugins.HttpRequestTimeoutException
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.request.header
import io.ktor.client.request.request
import io.ktor.client.request.setBody
import io.ktor.client.request.url
import io.ktor.client.statement.HttpResponse
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpMethod
import io.ktor.http.appendPathSegments
import io.ktor.http.contentType
import io.ktor.http.isSuccess
import io.ktor.serialization.kotlinx.json.json
import kotlinx.coroutines.CancellationException
import kotlinx.serialization.DeserializationStrategy
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.serializer

/** One call, described as data. Built by repositories; nothing here touches the wire. */
data class ApiRequest(
    val method: HttpMethod,
    /** Path under the gateway, e.g. `/v1/categories`. */
    val path: String,
    val query: Map<String, String?> = emptyMap(),
    val body: JsonElement? = null,
    /** False for anonymous endpoints: the token provider is not even consulted. */
    val authenticated: Boolean = true,
    /** Full header value, e.g. from [IfMatch.of]. */
    val ifMatch: String? = null,
    /** Validated against the backend's format when the call is made. */
    val idempotencyKey: String? = null
)

/** A decoded success. [etag] is surfaced because cart/address/profile versions ride on it. */
data class ApiResponse<out T>(val body: T, val status: Int, val etag: String?, val requestId: String? = null)

/**
 * The single HTTP entry point for the app's remote data sources.
 *
 * Contract:
 *  - Success returns [ApiResponse]; every failure throws [ApiException] carrying
 *    a typed [ApiError]. Cancellation is never swallowed.
 *  - No automatic retries. Replaying a mutating call is a decision for the
 *    caller (with an idempotency key), never for the transport.
 *  - Nothing is logged. No logging plugin is installed, so tokens, OTPs, phone
 *    numbers and addresses cannot reach a log from here.
 *  - `Authorization` is added only when the request is authenticated *and* the
 *    provider returns a non-blank token.
 *
 * Authentication, refresh and token storage are intentionally not here.
 */
class ApiClient(
    private val baseUrl: String = AppEnvironment.gatewayBaseUrl,
    private val tokenProvider: AccessTokenProvider? = null,
    engine: HttpClientEngine? = null,
    val json: Json = defaultJson,
    connectTimeoutMs: Long = CONNECT_TIMEOUT_MS,
    socketTimeoutMs: Long = SOCKET_TIMEOUT_MS,
    requestTimeoutMs: Long = REQUEST_TIMEOUT_MS
) {
    private val client: HttpClient = run {
        val configure: io.ktor.client.HttpClientConfig<*>.() -> Unit = {
            expectSuccess = false
            followRedirects = false
            install(ContentNegotiation) { json(json) }
            install(HttpTimeout) {
                this.connectTimeoutMillis = connectTimeoutMs
                this.socketTimeoutMillis = socketTimeoutMs
                this.requestTimeoutMillis = requestTimeoutMs
            }
        }
        if (engine != null) HttpClient(engine, configure) else HttpClient(configure)
    }

    /** Sends [request] and decodes a 2xx body with [deserializer]. */
    suspend fun <T> execute(request: ApiRequest, deserializer: DeserializationStrategy<T>): ApiResponse<T> {
        val response = send(request)
        val text = readBody(response)
        val requestId = response.headers["X-Request-Id"]?.takeIf { it.length <= 128 }
        return try {
            ApiResponse(json.decodeFromString(deserializer, text), response.status.value, response.headers[HttpHeaders.ETag], requestId)
        } catch (e: Exception) {
            if (e is CancellationException) throw e
            throw ApiException(ApiError.Decoding(requestId), e)
        }
    }

    /** For 2xx responses with no body (204 logout/delete). */
    suspend fun executeUnit(request: ApiRequest): ApiResponse<Unit> {
        val response = send(request)
        return ApiResponse(Unit, response.status.value, response.headers[HttpHeaders.ETag])
    }

    private suspend fun readBody(response: HttpResponse): String = try {
        response.bodyAsText()
    } catch (e: Throwable) {
        if (e is CancellationException) throw e
        throw ApiException(transportError(e), e)
    }

    /** Performs the call and throws [ApiException] for transport failures and non-2xx statuses. */
    private suspend fun send(request: ApiRequest): HttpResponse {
        request.idempotencyKey?.let { IdempotencyKey.require(it) }
        val token = if (request.authenticated) tokenProvider?.accessToken()?.takeIf { it.isNotBlank() } else null

        val response = try {
            client.request {
                method = request.method
                url(baseUrl)
                url {
                    appendPathSegments(request.path.trim('/').split('/'))
                    request.query.forEach { (k, v) -> if (v != null) parameters.append(k, v) }
                }
                header(HttpHeaders.Accept, ContentType.Application.Json.toString())
                if (token != null) header(HttpHeaders.Authorization, "Bearer $token")
                request.ifMatch?.let { header(HttpHeaders.IfMatch, it) }
                request.idempotencyKey?.let { header("Idempotency-Key", it) }
                if (request.body != null) {
                    contentType(ContentType.Application.Json)
                    setBody(request.body)
                }
            }
        } catch (e: Throwable) {
            // Ktor may surface a timeout as a CancellationException wrapping the
            // timeout; only a cancellation with no timeout cause is the caller's.
            if (e is CancellationException && !isTimeout(e)) throw e
            throw ApiException(transportError(e), e)
        }

        if (!response.status.isSuccess()) {
            val body = runCatching { response.bodyAsText() }.getOrNull()
            throw ApiException(
                ErrorEnvelope.parse(response.status.value, body, response.headers[HttpHeaders.RetryAfter], json)
            )
        }
        return response
    }

    /** Releases the underlying engine. Call when the owning scope ends. */
    fun close() = client.close()

    internal companion object {
        const val CONNECT_TIMEOUT_MS = 10_000L
        const val SOCKET_TIMEOUT_MS = 30_000L
        const val REQUEST_TIMEOUT_MS = 45_000L

        val defaultJson: Json = Json {
            ignoreUnknownKeys = true
            explicitNulls = false
            isLenient = false
        }

        /**
         * Timeouts first; every other failure before a response exists is a
         * network failure. That includes the Darwin engine's NSError wrapper,
         * which is not an IOException.
         */
        fun transportError(e: Throwable): ApiError = if (isTimeout(e)) ApiError.Timeout else ApiError.Network

        fun isTimeout(e: Throwable): Boolean =
            generateSequence(e) { it.cause }.take(4).any {
                it is HttpRequestTimeoutException || it is ConnectTimeoutException || it is SocketTimeoutException
            }
    }
}

/** Convenience for typed calls: `client.execute<CategoriesResponse>(request)`. */
suspend inline fun <reified T> ApiClient.execute(request: ApiRequest): ApiResponse<T> =
    execute(request, json.serializersModule.serializer<T>())
