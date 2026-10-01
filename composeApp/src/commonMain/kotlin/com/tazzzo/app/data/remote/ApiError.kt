package com.tazzzo.app.data.remote

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.longOrNull

/**
 * Everything that can go wrong between "send a request" and "hold a decoded
 * response", as data rather than as an exception message.
 *
 * Deliberately absent: the backend's human-readable `message`. It is not
 * customer copy, may name internal concepts, and must never reach a screen.
 * The code and request id are what support and the UI mapping need.
 */
sealed interface ApiError {
    /** Server request id, when the response carried one. Safe to show in support flows. */
    val requestId: String? get() = null

    /** The server answered with a non-2xx status. */
    data class Http(
        val status: Int,
        /** Backend error code such as `PRICE_CHANGED`, when present and well-formed. */
        val code: String? = null,
        override val requestId: String? = null,
        val retryable: Boolean? = null,
        val retryAfterSeconds: Long? = null,
        /** Per-item detail, e.g. checkout `CHECKOUT_ITEM_UNAVAILABLE` lines. */
        val items: List<ItemError> = emptyList()
    ) : ApiError

    /** The request never produced a response (offline, DNS, TLS, connection reset). */
    data object Network : ApiError

    /** A connect, socket or whole-request timeout fired. */
    data object Timeout : ApiError

    /** A 2xx response whose body could not be decoded into the expected type. */
    data class Decoding(override val requestId: String? = null) : ApiError
}

/** One entry of a structured per-item error list (`items[{skuId, reason}]`). */
data class ItemError(val skuId: String?, val reason: String?)

/**
 * Thrown by [ApiClient]. The [message] is built only from [error] fields that
 * passed sanitising, so it is safe to log; it is still not customer copy.
 */
class ApiException(val error: ApiError, cause: Throwable? = null) : Exception(describe(error), cause) {
    private companion object {
        fun describe(e: ApiError): String = when (e) {
            is ApiError.Http -> "HTTP ${e.status}" + (e.code?.let { " $it" } ?: "")
            ApiError.Network -> "Network failure"
            ApiError.Timeout -> "Timeout"
            is ApiError.Decoding -> "Response decoding failed"
        }
    }
}

/**
 * Parses an error response body without assuming one envelope.
 *
 * The backend has several shapes — commerce reads
 * `{code, message, requestId, retryable, retryAfterSeconds, details}`, OTP
 * `{code, message, requestId, retryAfterSeconds}`, session/profile/address/cart/
 * order `{code, message, requestId}`, checkout adding `items[]`, the older
 * `/catalog/v1` `{code, message, request_id}`, and the legacy nested
 * `{error:{code, message, request_id}}`. Every field is therefore
 * optional and looked up independently; a body that is empty, not JSON, or not
 * an object yields an [ApiError.Http] carrying only the status.
 *
 * Values are only kept when they look like identifiers, so free text can never
 * be smuggled through `code`, `requestId` or item fields.
 */
internal object ErrorEnvelope {
    private val CODE = Regex("^[A-Za-z0-9_]{1,64}$")
    private val ID = Regex("^[A-Za-z0-9_.:\\-]{1,128}$")

    fun parse(status: Int, body: String?, retryAfterHeader: String?, json: Json): ApiError.Http {
        val root = body
            ?.takeIf { it.isNotBlank() }
            ?.let { runCatching { json.parseToJsonElement(it) }.getOrNull() } as? JsonObject
        // The legacy gateway envelope nests everything: `{"error":{"code","message","request_id"}}`
        // (framework errors, 404 NO_SUCH_ENDPOINT). Flat envelopes are read as-is.
        val obj = (root?.get("error") as? JsonObject)?.takeIf { root.get("code") == null } ?: root

        val retryAfter = obj?.get("retryAfterSeconds")?.asLong()
            ?: retryAfterHeader?.trim()?.toLongOrNull()

        return ApiError.Http(
            status = status,
            code = obj?.get("code")?.asString(CODE),
            requestId = (obj?.get("requestId") ?: obj?.get("request_id"))?.asString(ID),
            retryable = (obj?.get("retryable") as? JsonPrimitive)?.takeIf { !it.isString }?.booleanOrNull,
            retryAfterSeconds = retryAfter?.takeIf { it >= 0 },
            items = (obj?.get("items") as? JsonArray).orEmpty().mapNotNull { it.asItem() }
        )
    }

    private fun JsonElement.asString(pattern: Regex): String? =
        (this as? JsonPrimitive)?.takeIf { it.isString }?.contentOrNull?.takeIf { pattern.matches(it) }

    private fun JsonElement.asLong(): Long? =
        (this as? JsonPrimitive)?.takeIf { !it.isString }?.longOrNull

    private fun JsonElement.asItem(): ItemError? {
        val o = this as? JsonObject ?: return null
        val sku = o["skuId"]?.asString(ID)
        val reason = o["reason"]?.asString(CODE)
        return if (sku == null && reason == null) null else ItemError(sku, reason)
    }
}
