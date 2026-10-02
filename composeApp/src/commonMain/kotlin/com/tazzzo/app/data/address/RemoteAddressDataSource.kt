package com.tazzzo.app.data.address

import com.tazzzo.app.data.remote.ApiClient
import com.tazzzo.app.data.remote.ApiRequest
import com.tazzzo.app.data.remote.IfMatch
import com.tazzzo.app.data.remote.execute
import io.ktor.http.HttpMethod
import kotlinx.serialization.json.JsonObject

/** What [AddressBook] needs from the backend. */
interface AddressSource {
    suspend fun list(): List<CustomerAddress>
    suspend fun create(address: ValidAddress): CustomerAddress
    suspend fun update(addressId: String, version: Long, body: JsonObject): CustomerAddress
    suspend fun delete(addressId: String, version: Long)
    suspend fun setDefault(addressId: String): CustomerAddress
}

/**
 * `/v1/customer/addresses`. Every call is AUTHENTICATED through the recovery-enabled client, so a
 * stale access token refreshes once (PR-03A) and a definitive rejection ends the session. The
 * customer id is never sent: the backend takes it from the token.
 *
 *  - PATCH and DELETE send `If-Match: "address-<version>"`; create and set-default do not.
 *  - Request and response bodies are never logged or put in exception messages.
 */
class RemoteAddressDataSource(private val api: ApiClient) : AddressSource {

    private fun request(method: HttpMethod, path: String, body: JsonObject? = null, version: Long? = null) = ApiRequest(
        method = method, path = path, body = body, authenticated = true,
        ifMatch = version?.let { IfMatch.of(IfMatch.ADDRESS, it) }
    )

    override suspend fun list(): List<CustomerAddress> =
        api.execute<AddressListDto>(request(HttpMethod.Get, BASE)).body.items.map { it.toDomain() }

    suspend fun get(addressId: String): CustomerAddress {
        requireId(addressId)
        return api.execute<AddressDto>(request(HttpMethod.Get, "$BASE/$addressId")).body.toDomain()
    }

    override suspend fun create(address: ValidAddress): CustomerAddress =
        api.execute<AddressDto>(request(HttpMethod.Post, BASE, AddressBodies.create(address))).body.toDomain()

    override suspend fun update(addressId: String, version: Long, body: JsonObject): CustomerAddress {
        requireId(addressId)
        return api.execute<AddressDto>(request(HttpMethod.Patch, "$BASE/$addressId", body, version)).body.toDomain()
    }

    override suspend fun delete(addressId: String, version: Long) {
        requireId(addressId)
        api.executeUnit(request(HttpMethod.Delete, "$BASE/$addressId", version = version))
    }

    override suspend fun setDefault(addressId: String): CustomerAddress {
        requireId(addressId)
        return api.execute<AddressDto>(request(HttpMethod.Put, "$BASE/$addressId/default")).body.toDomain()
    }

    companion object {
        const val BASE = "/v1/customer/addresses"
        private val ID = Regex("^ADDR_[A-Za-z0-9_-]{6,64}$")
        private fun requireId(id: String) = require(ID.matches(id)) { "invalid address id" }
    }
}
