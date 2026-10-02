package com.tazzzo.app.data.checkout

import com.tazzzo.app.data.remote.ApiClient
import com.tazzzo.app.data.remote.ApiRequest
import com.tazzzo.app.data.remote.IdempotencyKey
import com.tazzzo.app.data.remote.IfMatch
import com.tazzzo.app.data.remote.execute
import io.ktor.http.HttpMethod
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive

/** What [CheckoutQuoteStore] needs from the backend. */
interface QuoteSource {
    /** Creates (or, with the same key and fingerprint, replays) a quote. Sends ONLY the cart version, the key and the addressId. */
    suspend fun createQuote(cartVersion: Long, addressId: String, idempotencyKey: String): CheckoutQuote

    /** Reads the STORED quote. It is not a revalidation: no stock check, no repricing, no address refresh. */
    suspend fun getQuote(quoteId: String): CheckoutQuote
}

/**
 * `/v1/customer/checkout`, AUTHENTICATED through the recovery-enabled client. A quote request carries no payment method,
 * slot, coupon, membership, coins, PIN, coordinates, fulfillment id or client total. Bodies are never logged.
 */
class RemoteCheckoutDataSource(private val api: ApiClient) : QuoteSource {

    override suspend fun createQuote(cartVersion: Long, addressId: String, idempotencyKey: String): CheckoutQuote {
        require(cartVersion >= 0) { "invalid cart version" }
        require(ADDRESS_ID.matches(addressId)) { "invalid address id" }
        return api.execute<QuoteDto>(
            ApiRequest(
                method = HttpMethod.Post, path = "$BASE/quote",
                body = JsonObject(mapOf("addressId" to JsonPrimitive(addressId))),
                authenticated = true,
                ifMatch = IfMatch.of(IfMatch.CART, cartVersion),
                idempotencyKey = IdempotencyKey.require(idempotencyKey)
            )
        ).body.toDomain()
    }

    override suspend fun getQuote(quoteId: String): CheckoutQuote {
        require(QUOTE_ID.matches(quoteId)) { "invalid quote id" }
        return api.execute<QuoteDto>(ApiRequest(method = HttpMethod.Get, path = "$BASE/quotes/$quoteId", authenticated = true)).body.toDomain()
    }

    companion object {
        const val BASE = "/v1/customer/checkout"
        private val ADDRESS_ID = Regex("^ADDR_[A-Za-z0-9_-]{6,64}$")
        private val QUOTE_ID = Regex("^CHKQ_[A-Za-z0-9_-]{6,64}$")
    }
}
