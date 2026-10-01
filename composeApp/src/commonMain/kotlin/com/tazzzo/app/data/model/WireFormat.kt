package com.tazzzo.app.data.model

import kotlinx.serialization.KSerializer
import kotlinx.serialization.SerializationException
import kotlinx.serialization.Serializable
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder

/**
 * Hand-written serializers for the two sealed types whose documented JSON is
 * FLAT.
 *
 * kotlinx's default polymorphic encoding for a sealed type is
 * `{"type":"...","value":{...}}` (or a class-discriminator key with the
 * subclass's own fields nested under it). The contract documents neither of
 * those shapes — they document one flat object with a status/type string beside
 * the payload fields. These serializers produce exactly the documented shape.
 *
 * Both are written as a surrogate + mapping rather than a raw JsonElement
 * dance, so they work with any format kotlinx supports and stay readable.
 *
 * If the backend team changes a status string, change it HERE and in
 * docs/BACKEND_INTEGRATION_READINESS.md in the same commit. The round-trip
 * tests pin every value.
 */

// ---------------------------------------------------------------------------
// Availability
//   {"status":"IN_STOCK|LOW_STOCK|OUT_OF_STOCK|NOT_SERVICEABLE","remaining":3}
//   `remaining` is present only for LOW_STOCK and is ignored otherwise.
// ---------------------------------------------------------------------------

@Serializable
private data class AvailabilitySurrogate(
    val status: String,
    val remaining: Int? = null
)

object AvailabilitySerializer : KSerializer<Availability> {
    private val delegate = AvailabilitySurrogate.serializer()
    override val descriptor: SerialDescriptor = delegate.descriptor

    const val IN_STOCK = "IN_STOCK"
    const val LOW_STOCK = "LOW_STOCK"
    const val OUT_OF_STOCK = "OUT_OF_STOCK"
    const val NOT_SERVICEABLE = "NOT_SERVICEABLE"

    override fun serialize(encoder: Encoder, value: Availability) {
        val surrogate = when (value) {
            is Availability.InStock -> AvailabilitySurrogate(IN_STOCK)
            is Availability.LowStock -> AvailabilitySurrogate(LOW_STOCK, value.remaining)
            is Availability.OutOfStock -> AvailabilitySurrogate(OUT_OF_STOCK)
            is Availability.NotServiceable -> AvailabilitySurrogate(NOT_SERVICEABLE)
        }
        encoder.encodeSerializableValue(delegate, surrogate)
    }

    override fun deserialize(decoder: Decoder): Availability {
        val surrogate = decoder.decodeSerializableValue(delegate)
        return when (surrogate.status) {
            IN_STOCK -> Availability.InStock
            LOW_STOCK -> Availability.LowStock(
                // A LOW_STOCK with no usable remaining count is not "a little
                // bit in stock" — it is unsellable. Refusing to guess here is
                // what stops the app selling stock it cannot confirm.
                surrogate.remaining
                    ?: throw SerializationException("LOW_STOCK requires a 'remaining' count")
            )
            OUT_OF_STOCK -> Availability.OutOfStock
            NOT_SERVICEABLE -> Availability.NotServiceable
            else -> throw SerializationException(
                "Unknown availability status '${surrogate.status}'"
            )
        }
    }
}

// ---------------------------------------------------------------------------
// CartIssue
//   {"type":"OUT_OF_STOCK|QUANTITY_REDUCED|PRICE_CHANGED",
//    "productId":"p8","productName":"Toned Milk Pouch",
//    "requested":3,"available":1,          // QUANTITY_REDUCED
//    "oldPricePaise":2900,"newPricePaise":3200}  // PRICE_CHANGED (integer paise)
//
// The three type codes are the ones in the readiness report §5. The envelope
// key ("type") and the per-issue field names are specified HERE because the
// report names the codes but not the encoding; this file is the reference.
// ---------------------------------------------------------------------------

@Serializable
private data class CartIssueSurrogate(
    val type: String,
    val productId: String,
    val productName: String,
    val requested: Int? = null,
    val available: Int? = null,
    val oldPricePaise: Long? = null,
    val newPricePaise: Long? = null
)

object CartIssueSerializer : KSerializer<CartIssue> {
    private val delegate = CartIssueSurrogate.serializer()
    override val descriptor: SerialDescriptor = delegate.descriptor

    const val OUT_OF_STOCK = "OUT_OF_STOCK"
    const val QUANTITY_REDUCED = "QUANTITY_REDUCED"
    const val PRICE_CHANGED = "PRICE_CHANGED"

    override fun serialize(encoder: Encoder, value: CartIssue) {
        val surrogate = when (value) {
            is CartIssue.OutOfStock -> CartIssueSurrogate(
                OUT_OF_STOCK, value.productId, value.productName
            )
            is CartIssue.QuantityReduced -> CartIssueSurrogate(
                QUANTITY_REDUCED, value.productId, value.productName,
                requested = value.requested, available = value.available
            )
            is CartIssue.PriceChanged -> CartIssueSurrogate(
                PRICE_CHANGED, value.productId, value.productName,
                oldPricePaise = value.oldPrice.paise, newPricePaise = value.newPrice.paise
            )
        }
        encoder.encodeSerializableValue(delegate, surrogate)
    }

    override fun deserialize(decoder: Decoder): CartIssue {
        val s = decoder.decodeSerializableValue(delegate)
        // Every branch demands its own fields. A malformed issue must fail
        // loudly: silently degrading a PRICE_CHANGED into "something is wrong
        // with this line" is how a customer ends up paying a price nobody
        // disclosed.
        return when (s.type) {
            OUT_OF_STOCK -> CartIssue.OutOfStock(s.productId, s.productName)
            QUANTITY_REDUCED -> CartIssue.QuantityReduced(
                s.productId, s.productName,
                requested = s.requested
                    ?: throw SerializationException("QUANTITY_REDUCED requires 'requested'"),
                available = s.available
                    ?: throw SerializationException("QUANTITY_REDUCED requires 'available'")
            )
            PRICE_CHANGED -> CartIssue.PriceChanged(
                s.productId, s.productName,
                oldPrice = Money.ofPaise(
                    s.oldPricePaise ?: throw SerializationException("PRICE_CHANGED requires 'oldPricePaise'")
                ),
                newPrice = Money.ofPaise(
                    s.newPricePaise ?: throw SerializationException("PRICE_CHANGED requires 'newPricePaise'")
                )
            )
            else -> throw SerializationException("Unknown cart issue type '${s.type}'")
        }
    }
}
