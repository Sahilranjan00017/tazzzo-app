package com.tazzzo.app.data.catalog

import com.tazzzo.app.data.model.Money
import com.tazzzo.app.data.remote.ApiError
import com.tazzzo.app.data.remote.ApiException
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement

// Wire shapes of the RUNNING backend (`commerce/api/dto`), not of its OpenAPI.
// Optional fields default to null; unknown fields are ignored by the shared Json.

@Serializable internal data class NodeDto(val id: String, val name: String)

@Serializable internal data class NodeListDto(
    val resolvedReleaseId: String,
    val items: List<NodeDto> = emptyList()
)

/** `GET /v1/categories/{id}`: the node flattened with its envelope (backend `NodeDetailDto`). */
@Serializable internal data class NodeDetailDto(val id: String, val name: String, val resolvedReleaseId: String)

@Serializable internal data class ServiceAreaSummaryDto(val serviceAreaId: String? = null, val serviceable: Boolean)

@Serializable internal data class ProductCardDto(
    val skuId: String,
    val productId: String,
    val name: String,
    val brandCode: String? = null,
    val thumbnailUrl: String? = null,
    val sellingPricePaise: Long? = null,
    val mrpPaise: Long? = null,
    val discountPercent: Int? = null,
    val discountAmountPaise: Long? = null,
    val verticalId: String? = null,
    val stockState: String? = null,
    val lowStockRemaining: Int? = null,
    val maxOrderQuantity: Int = 0,
    val minimumOrderQuantity: Int = 1,
    val serviceable: Boolean? = null,
    val buyable: Boolean = false
)

@Serializable internal data class PagedProductsDto(
    val resolvedReleaseId: String,
    val serviceArea: ServiceAreaSummaryDto? = null,
    val items: List<ProductCardDto> = emptyList(),
    val nextCursor: String? = null,
    val hasMore: Boolean = false
)

@Serializable internal data class ProductImageDto(
    val url: String,
    val role: String? = null,
    val order: Int = 0,
    val alt: String? = null,
    val width: Int? = null,
    val height: Int? = null
)

@Serializable internal data class ProductAttributeDto(
    val key: String,
    val label: String,
    val value: JsonElement,
    val unit: String? = null
)

/** The extras on top of the (flat, unwrapped) card fields. */
@Serializable internal data class ProductDetailExtrasDto(
    val gallery: List<ProductImageDto> = emptyList(),
    val attributes: List<ProductAttributeDto> = emptyList(),
    val resolvedReleaseId: String = ""
)

@Serializable internal data class ServiceabilityDto(
    val serviceable: Boolean,
    val serviceAreaId: String? = null,
    val serviceAreaVersion: Long? = null,
    val etaMinutesMin: Int? = null,
    val etaMinutesMax: Int? = null
)

// ---- mapping ---------------------------------------------------------------

private fun malformed(): Nothing = throw ApiException(ApiError.Decoding())

private fun paise(value: Long?): Money? {
    if (value == null) return null
    if (value < 0) malformed() // a negative price is a contract violation, not a price
    return Money.ofPaise(value)
}

/** Only absolute HTTPS URLs are accepted; anything else is "no image" (a neutral placeholder). */
internal fun safeImageUrl(raw: String?): String? =
    raw?.trim()?.takeIf { it.startsWith("https://") && it.length > "https://".length && ' ' !in it }

internal fun ProductCardDto.toDomain(): CatalogProduct {
    val selling = paise(sellingPricePaise)
    return CatalogProduct(
        skuId = skuId,
        productId = productId,
        name = name,
        brandCode = brandCode?.takeIf { it.isNotBlank() },
        thumbnailUrl = safeImageUrl(thumbnailUrl),
        sellingPrice = selling,
        mrp = paise(mrpPaise),
        discountPercent = discountPercent,
        discountAmount = paise(discountAmountPaise),
        verticalId = verticalId,
        stockState = StockState.fromWire(stockState),
        lowStockRemaining = lowStockRemaining,
        maxOrderQuantity = maxOrderQuantity.coerceAtLeast(0),
        minimumOrderQuantity = minimumOrderQuantity,
        serviceable = serviceable,
        // The server decides. The one guard: with no active price there is nothing to buy.
        buyable = buyable && selling != null
    )
}

internal fun NodeListDto.toDomain() = TaxonomyPage(resolvedReleaseId, items.map { CatalogNode(it.id, it.name) })

/** The node read by [requestedId]; a body naming another node is a contract violation, never a name for this one. */
internal fun NodeDetailDto.toDomain(requestedId: String): CatalogNode =
    if (id == requestedId) CatalogNode(id, name) else malformed()

internal fun PagedProductsDto.toDomain() = ProductPage(
    resolvedReleaseId = resolvedReleaseId,
    serviceArea = serviceArea?.let { ServiceAreaSummary(it.serviceAreaId, it.serviceable) },
    items = items.map { it.toDomain() },
    nextCursor = nextCursor?.takeIf { it.isNotEmpty() },
    hasMore = hasMore && !nextCursor.isNullOrEmpty() // "more" without a cursor cannot be followed
)

internal fun ServiceabilityDto.toDomain() =
    ServiceabilityResult(serviceable, serviceAreaId, serviceAreaVersion, etaMinutesMin, etaMinutesMax)

internal fun toDetail(card: ProductCardDto, extras: ProductDetailExtrasDto) = CatalogProductDetail(
    product = card.toDomain(),
    gallery = extras.gallery.mapNotNull { img ->
        val url = safeImageUrl(img.url) ?: return@mapNotNull null
        ProductImage(
            url, if (img.role == "PRIMARY") ImageRole.PRIMARY else ImageRole.GALLERY,
            img.order, img.alt, img.width, img.height
        )
    }.sortedBy { it.order },
    attributes = extras.attributes.map { ProductAttribute(it.key, it.label, it.value, it.unit) },
    resolvedReleaseId = extras.resolvedReleaseId
)
