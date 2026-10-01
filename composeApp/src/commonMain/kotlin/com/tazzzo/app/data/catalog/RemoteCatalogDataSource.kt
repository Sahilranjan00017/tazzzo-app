package com.tazzzo.app.data.catalog

import com.tazzzo.app.data.remote.ApiClient
import com.tazzzo.app.data.remote.ApiRequest
import com.tazzzo.app.data.remote.Conditional
import com.tazzzo.app.data.remote.execute
import com.tazzzo.app.data.remote.executeConditional
import io.ktor.http.HttpMethod
import kotlinx.serialization.json.JsonObject

/**
 * Read-only access to the public commerce endpoints (`/v1 routes`).
 *
 * Every call is anonymous (`authenticated = false`: no token is read or sent),
 * carries the installation id, and never sends `lat`, `lng` or `release` — the
 * backend rejects the first two and the app has no use for the third.
 *
 * `404` and the other error statuses are thrown as [com.tazzzo.app.data.remote.ApiException];
 * deciding that a category's 404 means "empty" belongs to [CatalogReader].
 */
class RemoteCatalogDataSource(
    private val api: ApiClient,
    private val installationId: () -> InstallationId
) {
    private fun get(path: String, query: Map<String, String?> = emptyMap(), ifNoneMatch: String? = null) = ApiRequest(
        method = HttpMethod.Get, path = path, query = query, authenticated = false,
        headers = mapOf(InstallationId.HEADER to installationId().value), ifNoneMatch = ifNoneMatch
    )

    /** `GET /v1/categories` (the 7 super categories), revalidated with [etag] when given. */
    suspend fun categories(etag: String? = null): Conditional<TaxonomyPage> =
        mapNodes(api.executeConditional<NodeListDto>(get("/v1/categories", ifNoneMatch = etag)))

    /** `GET /v1/categories/{id}/children`. */
    suspend fun children(nodeId: String, etag: String? = null): Conditional<TaxonomyPage> {
        requireNodeId(nodeId)
        return mapNodes(api.executeConditional<NodeListDto>(get("/v1/categories/$nodeId/children", ifNoneMatch = etag)))
    }

    /**
     * `GET /v1/categories/{id}/products`. [cursor] is the opaque `nextCursor` of the previous page
     * for the SAME node, page size and PIN; the app passes it back untouched.
     */
    suspend fun products(nodeId: String, pin: Pincode?, cursor: String? = null, pageSize: Int = DEFAULT_PAGE_SIZE): ProductPage {
        requireNodeId(nodeId)
        require(pageSize in 1..MAX_PAGE_SIZE) { "page_size must be 1..$MAX_PAGE_SIZE" }
        val query = linkedMapOf<String, String?>("page_size" to pageSize.toString(), "cursor" to cursor, "pin" to pin?.value)
        return api.execute<PagedProductsDto>(get("/v1/categories/$nodeId/products", query)).body.toDomain()
    }

    /** `GET /v1/products/{id}`. The flat body is the card plus `gallery` / `attributes`. */
    suspend fun product(productId: String, pin: Pincode?): CatalogProductDetail {
        require(PRODUCT_ID.matches(productId)) { "invalid product id" }
        val response = api.execute<JsonObject>(get("/v1/products/$productId", mapOf("pin" to pin?.value))).body
        return try {
            toDetail(
                api.json.decodeFromJsonElement(ProductCardDto.serializer(), response),
                api.json.decodeFromJsonElement(ProductDetailExtrasDto.serializer(), response)
            )
        } catch (e: kotlinx.coroutines.CancellationException) {
            throw e
        } catch (e: Exception) {
            if (e is com.tazzzo.app.data.remote.ApiException) throw e
            throw com.tazzzo.app.data.remote.ApiException(com.tazzzo.app.data.remote.ApiError.Decoding(), e)
        }
    }

    private fun mapNodes(c: Conditional<NodeListDto>): Conditional<TaxonomyPage> = when (c) {
        is Conditional.NotModified -> c
        is Conditional.Modified -> Conditional.Modified(
            com.tazzzo.app.data.remote.ApiResponse(c.response.body.toDomain(), c.response.status, c.response.etag, c.response.requestId)
        )
    }

    companion object {
        const val DEFAULT_PAGE_SIZE = 20
        const val MAX_PAGE_SIZE = 50
        private val NODE_ID = Regex("^TZ[SCGV]-[0-9]{6}$")
        private val PRODUCT_ID = Regex("^TZP-[0-9]+$")

        private fun requireNodeId(id: String) = require(NODE_ID.matches(id)) { "invalid category id" }
    }
}

/** Answers "do we deliver to this PIN?". */
fun interface ServiceabilityChecker {
    suspend fun check(pin: Pincode): ServiceabilityResult
}

/** `GET /v1/serviceability?pin=` — anonymous, PIN only, no lat/lng. */
class RemoteServiceabilityDataSource(
    private val api: ApiClient,
    private val installationId: () -> InstallationId
) : ServiceabilityChecker {
    override suspend fun check(pin: Pincode): ServiceabilityResult = api.execute<ServiceabilityDto>(
        ApiRequest(
            method = HttpMethod.Get, path = "/v1/serviceability", query = mapOf("pin" to pin.value),
            authenticated = false, headers = mapOf(InstallationId.HEADER to installationId().value)
        )
    ).body.toDomain()
}
