package com.tazzzo.app.data.order

import com.tazzzo.app.data.checkout.Iso8601
import com.tazzzo.app.data.checkout.PayableMoneyDto
import com.tazzzo.app.data.checkout.toDomain
import com.tazzzo.app.data.model.Money
import com.tazzzo.app.data.remote.ApiError
import com.tazzzo.app.data.remote.ApiException
import kotlinx.serialization.Serializable

// Wire shape of the RUNNING backend (`customer.order.CustomerOrderDto`), only the fields it sends.

@Serializable internal data class OrderItemDto(
    val skuId: String, val title: String? = null, val brandCode: String? = null, val quantity: Int,
    val unitPricePaise: Long, val lineTotalPaise: Long
)

@Serializable internal data class OrderAddressDto(
    val label: String? = null, val recipientName: String? = null, val recipientPhone: String? = null, val addressLine1: String? = null,
    val addressLine2: String? = null, val landmark: String? = null, val city: String? = null, val state: String? = null,
    val postalCode: String? = null
)

@Serializable internal data class OrderDto(
    val orderId: String,
    val status: String? = null,
    val paymentMethod: String? = null,
    val paymentCondition: String? = null,
    val items: List<OrderItemDto> = emptyList(),
    val itemCount: Int = 0,
    val subtotalPaise: Long,
    val currency: String = "INR",
    val deliveryAddress: OrderAddressDto? = null,
    val createdAt: String? = null,
    val confirmedAt: String? = null,
    val money: PayableMoneyDto? = null,
    val requestId: String? = null
)

private fun malformed(): Nothing = throw ApiException(ApiError.Decoding())

private fun paise(v: Long): Money { if (v < 0) malformed(); return Money.ofPaise(v) }

internal val ORDER_ID = Regex("^ORD_[A-Za-z0-9_-]{6,64}$")

internal fun OrderDto.toDomain(): CustomerOrder {
    if (!ORDER_ID.matches(orderId)) malformed()
    return CustomerOrder(
        orderId = orderId,
        status = CustomerOrderStatus.of(status),
        paymentMethod = CustomerPaymentMethod.of(paymentMethod),
        paymentCondition = OrderPaymentCondition.of(paymentCondition),
        items = items.map {
            if (it.skuId.isBlank() || it.quantity < 1) malformed()
            CustomerOrderItem(it.skuId, it.title?.takeIf { t -> t.isNotBlank() } ?: "Item", it.brandCode, it.quantity, paise(it.unitPricePaise), paise(it.lineTotalPaise))
        },
        itemCount = itemCount.coerceAtLeast(0),
        subtotal = paise(subtotalPaise),
        currency = currency,
        money = money.toDomain(subtotalPaise),                    // inconsistent money or a disagreeing subtotal = contract failure
        deliveryAddress = deliveryAddress?.let {
            OrderDeliveryAddress(it.label, it.recipientName, it.recipientPhone, it.addressLine1, it.addressLine2, it.landmark, it.city, it.state, it.postalCode)
        },
        createdAtMillis = createdAt?.let { Iso8601.parseMillis(it) },
        confirmedAtMillis = confirmedAt?.let { Iso8601.parseMillis(it) }
    )
}
