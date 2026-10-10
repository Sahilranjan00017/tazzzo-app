package com.tazzzo.app.data.order

import com.tazzzo.app.data.catalog.Page
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

    /** Reads one stored order snapshot by id. */
    suspend fun getOrder(orderId: String): CustomerOrder

    /** One page of the customer's own order history, newest first. [cursor] is the previous page's opaque `nextCursor`. */
    suspend fun listOrders(cursor: String?, pageSize: Int = RemoteOrderDataSource.DEFAULT_PAGE_SIZE): Page<CustomerOrderSummary>
}

/**
 * `/v1/customer/orders`, AUTHENTICATED through the recovery-enabled client. The create body is exactly
 * `{"quoteId":…,"paymentMethod":"COD"}`: no Idempotency-Key, no If-Match, no address, cart version, total, slot, coupon or
 * coins (`deliverySlotId` is optional on the backend unless `tazzzo.checkout.delivery-slot-required`, default false). The list
 * sends only `page_size` and `cursor` (anything else is a 400). Bodies are never logged.
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

    override suspend fun listOrders(cursor: String?, pageSize: Int): Page<CustomerOrderSummary> {
        require(pageSize in 1..MAX_PAGE_SIZE) { "page_size must be 1..$MAX_PAGE_SIZE" }
        require(cursor == null || (cursor.isNotEmpty() && cursor.length <= MAX_CURSOR)) { "invalid cursor" }
        val page = api.execute<OrderPageDto>(
            ApiRequest(
                method = HttpMethod.Get, path = BASE,
                query = linkedMapOf("page_size" to pageSize.toString(), "cursor" to cursor), authenticated = true
            )
        ).body
        val next = page.nextCursor?.takeIf { it.isNotEmpty() && it.length <= MAX_CURSOR }
        return Page(page.items.map { it.toDomain() }, next, hasMore = next != null)
    }

    companion object {
        const val BASE = "/v1/customer/orders"
        const val DEFAULT_PAGE_SIZE = 20
        /** The backend's `OrderLifecycleService.MAX_PAGE_SIZE`. */
        const val MAX_PAGE_SIZE = 50
        /** The backend rejects a longer cursor (400); a longer `nextCursor` is treated as the end of the list. */
        const val MAX_CURSOR = 128
        internal val QUOTE_ID = Regex("^CHKQ_[A-Za-z0-9_-]{6,64}$")
        fun isValidQuoteId(id: String) = QUOTE_ID.matches(id)
    }
}
