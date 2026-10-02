package com.tazzzo.app.data.order

import com.tazzzo.app.data.remote.ApiClient
import com.tazzzo.app.data.remote.ApiRequest
import com.tazzzo.app.data.remote.execute
import io.ktor.http.HttpMethod
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive

/** What [OrderStore] needs from the backend. */
interface OrderSource {
    /**
     * Places (or, for a quote that already has an order, REPLAYS) the COD order. Idempotent by (customer, quote) on the
     * server: the replay lookup runs before expiry and every other check, so re-sending the same quote can never create a
     * second order and is the reconciliation for an ambiguous result.
     */
    suspend fun placeCodOrder(quoteId: String): CustomerOrder

    /** Reads one stored order snapshot by id. This is NOT history: there is no list endpoint. */
    suspend fun getOrder(orderId: String): CustomerOrder
}

/**
 * `/v1/customer/orders`, AUTHENTICATED through the recovery-enabled client. The create body is exactly
 * `{"quoteId":…,"paymentMethod":"COD"}`: no Idempotency-Key, no If-Match, no address, cart version, total, slot, coupon or
 * coins. Bodies are never logged.
 */
class RemoteOrderDataSource(private val api: ApiClient) : OrderSource {

    override suspend fun placeCodOrder(quoteId: String): CustomerOrder {
        require(QUOTE_ID.matches(quoteId)) { "invalid quote id" }
        return api.execute<OrderDto>(
            ApiRequest(
                method = HttpMethod.Post, path = BASE,
                body = JsonObject(mapOf("quoteId" to JsonPrimitive(quoteId), "paymentMethod" to JsonPrimitive("COD"))),
                authenticated = true
            )
        ).body.toDomain()
    }

    override suspend fun getOrder(orderId: String): CustomerOrder {
        require(ORDER_ID.matches(orderId)) { "invalid order id" }
        return api.execute<OrderDto>(ApiRequest(method = HttpMethod.Get, path = "$BASE/$orderId", authenticated = true)).body.toDomain()
    }

    companion object {
        const val BASE = "/v1/customer/orders"
        internal val QUOTE_ID = Regex("^CHKQ_[A-Za-z0-9_-]{6,64}$")
        fun isValidQuoteId(id: String) = QUOTE_ID.matches(id)
    }
}
