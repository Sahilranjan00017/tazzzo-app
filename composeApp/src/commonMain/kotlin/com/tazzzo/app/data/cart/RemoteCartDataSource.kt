package com.tazzzo.app.data.cart

import com.tazzzo.app.data.remote.ApiClient
import com.tazzzo.app.data.remote.ApiRequest
import com.tazzzo.app.data.remote.IfMatch
import com.tazzzo.app.data.remote.execute
import io.ktor.http.HttpMethod
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive

/** What [CartStore] needs from the backend. Every call returns the COMPLETE authoritative cart. */
interface CartSource {
    suspend fun get(addressId: String?): ServerCart
    /** Absolute quantity, `>= 1`. Never used to remove (quantity 0 is a 400 on the server). */
    suspend fun setQuantity(skuId: String, quantity: Int, version: Long, addressId: String?): ServerCart
    suspend fun removeItem(skuId: String, version: Long, addressId: String?): ServerCart
    suspend fun clear(version: Long, addressId: String?): ServerCart
}

/**
 * `/v1/customer/cart`, AUTHENTICATED through the recovery-enabled client (PR-03A bearer, one refresh on a 401).
 *
 *  - Every mutation sends `If-Match: "cart-<version>"` built from the latest version the server returned.
 *  - The only location input is `?addressId=` of a saved address. The app never sends a PIN, coordinates
 *    or any fulfillment id.
 *  - Bodies and responses are never logged or put in exception messages.
 */
class RemoteCartDataSource(private val api: ApiClient) : CartSource {

    private fun request(method: HttpMethod, path: String, addressId: String?, body: JsonObject? = null, version: Long? = null): ApiRequest {
        addressId?.let { require(ADDRESS_ID.matches(it)) { "invalid address id" } }
        return ApiRequest(
            method = method, path = path, body = body, authenticated = true,
            query = mapOf("addressId" to addressId),                       // null -> omitted
            ifMatch = version?.let { IfMatch.of(IfMatch.CART, it) }
        )
    }

    private suspend fun run(r: ApiRequest): ServerCart = api.execute<CartDto>(r).body.toDomain()

    override suspend fun get(addressId: String?): ServerCart = run(request(HttpMethod.Get, BASE, addressId))

    override suspend fun setQuantity(skuId: String, quantity: Int, version: Long, addressId: String?): ServerCart {
        requireSku(skuId)
        require(quantity >= 1) { "quantity must be >= 1; removal is a DELETE" }
        return run(request(HttpMethod.Put, "$BASE/items/$skuId", addressId, JsonObject(mapOf("quantity" to JsonPrimitive(quantity))), version))
    }

    override suspend fun removeItem(skuId: String, version: Long, addressId: String?): ServerCart {
        requireSku(skuId)
        return run(request(HttpMethod.Delete, "$BASE/items/$skuId", addressId, version = version))
    }

    override suspend fun clear(version: Long, addressId: String?): ServerCart =
        run(request(HttpMethod.Delete, BASE, addressId, version = version))

    companion object {
        const val BASE = "/v1/customer/cart"
        private val SKU = Regex("^TZP-[0-9]{1,18}$")
        private val ADDRESS_ID = Regex("^ADDR_[A-Za-z0-9_-]{6,64}$")
        fun isValidSku(skuId: String) = SKU.matches(skuId)
        private fun requireSku(id: String) = require(isValidSku(id)) { "invalid sku id" }
    }
}
