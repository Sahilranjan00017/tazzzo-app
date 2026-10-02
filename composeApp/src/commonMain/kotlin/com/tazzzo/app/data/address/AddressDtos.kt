package com.tazzzo.app.data.address

import com.tazzzo.app.data.catalog.Pincode
import com.tazzzo.app.data.remote.ApiError
import com.tazzzo.app.data.remote.ApiException
import kotlinx.serialization.Serializable

// Wire shapes of the RUNNING backend (`customer.address.*Dto`). Only the fields the API sends.
// `createdAt`/`updatedAt` do not exist in the API; `fulfillmentLocationId` and other internals are never sent.

@Serializable internal data class AddressServiceabilityDto(val serviceable: Boolean? = null)

@Serializable internal data class AddressDto(
    val addressId: String,
    val label: String,
    val recipientName: String,
    val recipientPhone: String,
    val addressLine1: String,
    val addressLine2: String? = null,
    val landmark: String? = null,
    val city: String,
    val state: String,
    val postalCode: String,
    val latitude: Double? = null,
    val longitude: Double? = null,
    val isDefault: Boolean = false,
    val version: Long,
    val serviceability: AddressServiceabilityDto? = null
) {
    // Never print personal data.
    override fun toString(): String = "AddressDto(***)"
}

@Serializable internal data class AddressListDto(val items: List<AddressDto> = emptyList())

internal fun AddressDto.toDomain(): CustomerAddress {
    fun malformed(): Nothing = throw ApiException(ApiError.Decoding())
    val lbl = AddressLabel.fromWire(label) ?: malformed()
    val pin = Pincode.parse(postalCode) ?: malformed()
    if (version < 1 || !addressId.startsWith("ADDR_")) malformed()
    return CustomerAddress(
        addressId = addressId, label = lbl, recipientName = recipientName, recipientPhone = recipientPhone,
        addressLine1 = addressLine1, addressLine2 = addressLine2?.takeIf { it.isNotBlank() },
        landmark = landmark?.takeIf { it.isNotBlank() }, city = city, state = state, postalCode = pin,
        latitude = latitude, longitude = longitude, isDefault = isDefault, version = version,
        serviceability = AddressServiceability.of(serviceability?.serviceable)
    )
}
