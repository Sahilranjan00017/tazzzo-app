package com.tazzzo.app.data.cart

import com.tazzzo.app.data.catalog.StockState
import com.tazzzo.app.data.catalog.safeImageUrl
import com.tazzzo.app.data.model.Money
import com.tazzzo.app.data.remote.ApiError
import com.tazzzo.app.data.remote.ApiException
import kotlinx.serialization.Serializable

// Wire shapes of the RUNNING backend (`customer.cart.CartResponseDto`). Only the fields it sends.
// No productId, no cart/customer id, no fulfillment location, no price snapshot exist in this API.

@Serializable internal data class CartProductDto(val title: String? = null, val brandCode: String? = null, val imageUrl: String? = null)

@Serializable internal data class CartPriceDto(val unitPricePaise: Long, val mrpPaise: Long? = null, val currency: String? = null)

@Serializable internal data class CartAvailabilityDto(val stockState: String? = null, val maxOrderQuantity: Int = 0, val serviceable: Boolean? = null)

@Serializable internal data class CartItemDto(
    val skuId: String,
    val quantity: Int,
    val addedAt: String? = null,
    val updatedAt: String? = null,
    val product: CartProductDto? = null,
    val price: CartPriceDto? = null,
    val availability: CartAvailabilityDto? = null,
    val lineTotalPaise: Long? = null,
    val buyable: Boolean = false,
    val issues: List<String> = emptyList()
)

@Serializable internal data class CartDto(
    val version: Long,
    val items: List<CartItemDto> = emptyList(),
    val itemCount: Int = 0,
    val distinctItemCount: Int = 0,
    val subtotalPaise: Long = 0,
    val expiresAt: String? = null
)

private fun malformed(): Nothing = throw ApiException(ApiError.Decoding())

/** A negative amount is a contract violation, not a price. */
private fun paise(v: Long?): Money? {
    if (v == null) return null
    if (v < 0) malformed()
    return Money.ofPaise(v)
}

internal fun CartItemDto.toDomain(): CartItem {
    if (quantity < 0 || skuId.isBlank()) malformed()
    return CartItem(
        skuId = skuId, quantity = quantity, addedAt = addedAt, updatedAt = updatedAt,
        title = product?.title?.takeIf { it.isNotBlank() },
        brandCode = product?.brandCode?.takeIf { it.isNotBlank() },
        imageUrl = safeImageUrl(product?.imageUrl),
        unitPrice = paise(price?.unitPricePaise), mrp = paise(price?.mrpPaise), lineTotal = paise(lineTotalPaise),
        stockState = StockState.fromWire(availability?.stockState),
        maxOrderQuantity = (availability?.maxOrderQuantity ?: 0).coerceAtLeast(0),
        serviceable = availability?.serviceable,
        buyable = buyable,
        issues = issues.map { LineIssue.of(it) }
    )
}

internal fun CartDto.toDomain(): ServerCart {
    if (version < 0) malformed()
    return ServerCart(
        version = version, items = items.map { it.toDomain() }, itemCount = itemCount.coerceAtLeast(0),
        distinctItemCount = distinctItemCount.coerceAtLeast(0), subtotal = paise(subtotalPaise) ?: Money.ZERO, expiresAt = expiresAt
    )
}
