package com.tazzzo.app.data.catalog

import com.tazzzo.app.data.model.Money
import com.tazzzo.app.data.remote.ApiError
import com.tazzzo.app.data.remote.ApiException
import kotlinx.serialization.json.JsonElement

/*
 * Domain types for the REAL catalogue (`/v1 routes`). They are deliberately separate
 * from the legacy mock-era `data.model.Product` (rupee `Int`): that model is
 * migrated to Money in PR-04B and wired in PR-04C. Nothing here is read by a
 * screen yet.
 *
 * Absent really means absent. The running backend never sends brand name, pack
 * size, unit, rating, badges, description, ETA, ... so none of them exist here.
 */

/**
 * The platform product-id grammar (backend `ContentBlock.PRODUCT_ID`, cart `skuId`, OpenAPI), numeric ids included. The
 * ONE copy in the app: rail ids, `product:` links, `GET /v1/products/{id}` and the cart all validate with it. It admits
 * no `/`, `.`, `%`, `?` or whitespace, so an id is always ONE unescaped path segment.
 */
internal val PRODUCT_ID = Regex("^TZP-[A-Za-z0-9-]{1,40}$")

/** A taxonomy node as the backend gives it: an id and a name, nothing else. */
data class CatalogNode(val id: String, val name: String)

data class TaxonomyPage(val resolvedReleaseId: String, val items: List<CatalogNode>)

/** Stock position. [UNKNOWN] is NOT out of stock: it means "cannot say" (no/unserviceable PIN, no inventory row). */
enum class StockState {
    IN_STOCK, LOW_STOCK, OUT_OF_STOCK, UNKNOWN;

    companion object {
        /** A value this build does not know is [UNKNOWN] — never a guess at availability. */
        fun fromWire(raw: String?): StockState = entries.firstOrNull { it.name == raw } ?: UNKNOWN
    }
}

/**
 * One catalogue card. Money is integer paise ([Money]); optional prices are null
 * when the canonical price is not active, and then [buyable] is false.
 *
 * [buyable] is the server's decision and the ONLY purchase-eligibility signal.
 * It is never derived from [stockState] here.
 */
data class CatalogProduct(
    val skuId: String,
    val productId: String,
    val name: String,
    val brandCode: String?,
    val thumbnailUrl: String?,
    val sellingPrice: Money?,
    val mrp: Money?,
    val discountPercent: Int?,
    val discountAmount: Money?,
    val verticalId: String?,
    val stockState: StockState,
    val lowStockRemaining: Int?,
    val maxOrderQuantity: Int,
    val minimumOrderQuantity: Int,
    /** Null = the server was not given a location, so it cannot say. */
    val serviceable: Boolean?,
    val buyable: Boolean
) {
    val hasPrice: Boolean get() = sellingPrice != null
}

enum class ImageRole { PRIMARY, GALLERY }

data class ProductImage(
    val url: String,
    val role: ImageRole,
    val order: Int,
    val alt: String?,
    val width: Int?,
    val height: Int?
)

data class ProductAttribute(val key: String, val label: String, val value: JsonElement, val unit: String?)

data class CatalogProductDetail(
    val product: CatalogProduct,
    val gallery: List<ProductImage>,
    val attributes: List<ProductAttribute>,
    val resolvedReleaseId: String
)

data class ServiceAreaSummary(val serviceAreaId: String?, val serviceable: Boolean)

data class ProductPage(
    val resolvedReleaseId: String,
    val serviceArea: ServiceAreaSummary?,
    val items: List<CatalogProduct>,
    /** Opaque. Never inspected, parsed or constructed by the app. */
    val nextCursor: String?,
    val hasMore: Boolean
) {
    companion object {
        /** A category with nothing to list (the backend answers 404 for it). */
        fun empty(releaseId: String = "") = ProductPage(releaseId, null, emptyList(), null, false)
    }
}

/**
 * `GET /v1/serviceability`. [serviceable] is never null here (unlike on a card).
 * ETA is not populated today and is never required.
 */
data class ServiceabilityResult(
    val serviceable: Boolean,
    val serviceAreaId: String?,
    val serviceAreaVersion: Long?,
    val etaMinutesMin: Int?,
    val etaMinutesMax: Int?
)

/**
 * A six-digit Indian PIN. A coarse location: its [toString] is redacted so it
 * cannot reach a log or an error string.
 */
class Pincode private constructor(val value: String) {
    override fun toString(): String = "Pincode(***)"
    override fun equals(other: Any?): Boolean = other is Pincode && other.value == value
    override fun hashCode(): Int = value.hashCode()

    companion object {
        private val FORMAT = Regex("^[1-9][0-9]{5}$")

        /** The initial launch context. */
        const val LAUNCH_VALUE = "560047"
        val LAUNCH: Pincode = Pincode(LAUNCH_VALUE)

        fun isValid(raw: String): Boolean = FORMAT.matches(raw)

        /** Null unless [raw] (trimmed) matches `^[1-9][0-9]{5}$`. */
        fun parse(raw: String?): Pincode? = raw?.trim()?.takeIf { FORMAT.matches(it) }?.let { Pincode(it) }
    }
}

/** What went wrong reading the catalogue, as data for the UI. Never carries codes, ids or PINs. */
sealed interface CatalogFailure {
    data object Network : CatalogFailure
    data object Timeout : CatalogFailure
    data class RateLimited(val retryAfterSeconds: Long?) : CatalogFailure
    /** 503: no retry hint from the server, the caller applies its own backoff. */
    data object Unavailable : CatalogFailure
    data object Server : CatalogFailure
    data object NotFound : CatalogFailure
    data object InvalidRequest : CatalogFailure
    data object InvalidCursor : CatalogFailure
    data object Unknown : CatalogFailure

    val isRetryable: Boolean
        get() = this is Network || this is Timeout || this is RateLimited || this is Unavailable || this is Server
}

fun Throwable.toCatalogFailure(): CatalogFailure {
    val api = (this as? ApiException)?.error ?: return CatalogFailure.Unknown
    return when (api) {
        ApiError.Network -> CatalogFailure.Network
        ApiError.Timeout -> CatalogFailure.Timeout
        is ApiError.Decoding -> CatalogFailure.Unknown
        is ApiError.Http -> when {
            api.code == "INVALID_CURSOR" -> CatalogFailure.InvalidCursor
            api.status == 429 || api.code == "RATE_LIMITED" -> CatalogFailure.RateLimited(api.retryAfterSeconds)
            api.status == 404 || api.code == "NOT_FOUND" -> CatalogFailure.NotFound
            api.status == 503 || api.code == "SERVICE_UNAVAILABLE" -> CatalogFailure.Unavailable
            api.status in 500..599 -> CatalogFailure.Server
            api.status == 400 || api.code == "INVALID_REQUEST" -> CatalogFailure.InvalidRequest
            else -> CatalogFailure.Unknown
        }
    }
}
