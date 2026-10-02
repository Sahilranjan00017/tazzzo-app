package com.tazzzo.app.data.address

import com.tazzzo.app.data.catalog.Pincode
import com.tazzzo.app.data.auth.PhoneNumber
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive

/** Backend limits, mirrored client-side because the server answers every field error with one generic code. */
// `internal`: this object is app-module only. Exported to Objective-C its LINE_MAX / NAME_MAX constants collide with the
// <limits.h> macros of the same names and the generated framework header fails to compile (iOS app build break).
internal object AddressRules {
    const val NAME_MAX = 80
    const val LINE_MAX = 160
    const val LANDMARK_MAX = 120
    const val CITY_MAX = 80
    const val STATE_MAX = 80
    /** The launch limit. The server config is authoritative — always handle `409` too. */
    const val MAX_ADDRESSES = 10
}

enum class AddressField { LABEL, RECIPIENT_NAME, RECIPIENT_PHONE, ADDRESS_LINE1, ADDRESS_LINE2, LANDMARK, CITY, STATE, POSTAL_CODE }

enum class FieldError { Required, TooLong, Invalid }

/** Raw form input. Redacted `toString`: this is personal data. */
data class AddressInput(
    val label: AddressLabel?,
    val recipientName: String,
    val recipientPhone: String,
    val addressLine1: String,
    val addressLine2: String,
    val landmark: String,
    val city: String,
    val state: String,
    val postalCode: String
) {
    override fun toString(): String = "AddressInput(***)"

    companion object {
        fun from(a: CustomerAddress) = AddressInput(
            a.label, a.recipientName, a.recipientPhone, a.addressLine1, a.addressLine2.orEmpty(), a.landmark.orEmpty(),
            a.city, a.state, a.postalCode.value
        )
    }
}

/** A fully validated, canonical address ready to send. No coordinates: there is no input for them. */
class ValidAddress(
    val label: AddressLabel,
    val recipientName: String,
    val recipientPhone: String,
    val addressLine1: String,
    val addressLine2: String?,
    val landmark: String?,
    val city: String,
    val state: String,
    val postalCode: Pincode
) {
    override fun toString(): String = "ValidAddress(***)"
}

sealed interface AddressValidation {
    class Valid(val address: ValidAddress) : AddressValidation {
        override fun toString(): String = "Valid(***)"
    }
    data class Invalid(val errors: Map<AddressField, FieldError>) : AddressValidation
}

object AddressValidator {
    // +91 / 0 / bare, then a 10-digit Indian mobile starting 6-9 (the backend's three accepted shapes).
    private val PHONE = Regex("^(?:\\+91|0)?[6-9][0-9]{9}$")

    fun validate(input: AddressInput): AddressValidation {
        val errors = LinkedHashMap<AddressField, FieldError>()
        if (input.label == null) errors[AddressField.LABEL] = FieldError.Required

        val name = required(input.recipientName, AddressRules.NAME_MAX, AddressField.RECIPIENT_NAME, errors)
        val line1 = required(input.addressLine1, AddressRules.LINE_MAX, AddressField.ADDRESS_LINE1, errors)
        val line2 = optional(input.addressLine2, AddressRules.LINE_MAX, AddressField.ADDRESS_LINE2, errors)
        val landmark = optional(input.landmark, AddressRules.LANDMARK_MAX, AddressField.LANDMARK, errors)
        val city = required(input.city, AddressRules.CITY_MAX, AddressField.CITY, errors)
        val state = required(input.state, AddressRules.STATE_MAX, AddressField.STATE, errors)

        var phone: String? = null
        val rawPhone = input.recipientPhone.trim()
        if (rawPhone.isEmpty()) errors[AddressField.RECIPIENT_PHONE] = FieldError.Required
        else {
            val compact = rawPhone.filter { it != ' ' && it != '-' }      // typing convenience; the wire form is canonical
            // The regex guarantees the last 10 characters are the mobile number; the wire form is always +91XXXXXXXXXX.
            if (PHONE.matches(compact)) phone = PhoneNumber.parse(compact.takeLast(10))?.e164
            if (phone == null) errors[AddressField.RECIPIENT_PHONE] = FieldError.Invalid
        }

        val pin = input.postalCode.trim()
        var postal: Pincode? = null
        if (pin.isEmpty()) errors[AddressField.POSTAL_CODE] = FieldError.Required
        else {
            postal = Pincode.parse(pin)
            if (postal == null) errors[AddressField.POSTAL_CODE] = FieldError.Invalid
        }

        if (errors.isNotEmpty()) return AddressValidation.Invalid(errors)
        return AddressValidation.Valid(
            ValidAddress(input.label!!, name!!, phone!!, line1!!, line2, landmark, city!!, state!!, postal!!)
        )
    }

    private fun required(raw: String, max: Int, field: AddressField, errors: MutableMap<AddressField, FieldError>): String? {
        val t = raw.trim()
        when {
            t.isEmpty() -> errors[field] = FieldError.Required
            codePoints(t) > max -> errors[field] = FieldError.TooLong
            t.any { it.isIsoControl() } -> errors[field] = FieldError.Invalid
            else -> return t
        }
        return null
    }

    /** Blank -> null (absent). Otherwise the same limits. */
    private fun optional(raw: String, max: Int, field: AddressField, errors: MutableMap<AddressField, FieldError>): String? {
        val t = raw.trim()
        if (t.isEmpty()) return null
        when {
            codePoints(t) > max -> errors[field] = FieldError.TooLong
            t.any { it.isIsoControl() } -> errors[field] = FieldError.Invalid
            else -> return t
        }
        return null
    }

    /** Unicode code points (a surrogate pair counts once), as the backend counts them. */
    internal fun codePoints(s: String): Int {
        var n = 0
        var i = 0
        while (i < s.length) {
            val c = s[i]
            i += if (c.isHighSurrogate() && i + 1 < s.length && s[i + 1].isLowSurrogate()) 2 else 1
            n++
        }
        return n
    }

    private fun Char.isIsoControl(): Boolean = code <= 0x1F || code in 0x7F..0x9F
}

/** Builders for the wire bodies. Coordinates are never sent. */
object AddressBodies {
    fun create(a: ValidAddress): JsonObject = JsonObject(buildMap {
        put("label", JsonPrimitive(a.label.wire))
        put("recipientName", JsonPrimitive(a.recipientName))
        put("recipientPhone", JsonPrimitive(a.recipientPhone))
        put("addressLine1", JsonPrimitive(a.addressLine1))
        a.addressLine2?.let { put("addressLine2", JsonPrimitive(it)) }
        a.landmark?.let { put("landmark", JsonPrimitive(it)) }
        put("city", JsonPrimitive(a.city))
        put("state", JsonPrimitive(a.state))
        put("postalCode", JsonPrimitive(a.postalCode.value))
    })

    /**
     * Only the fields that changed. A cleared optional field is sent as an explicit `null` (the only
     * way the backend clears it). Null when nothing changed, so no request is made at all.
     */
    fun patch(original: CustomerAddress, a: ValidAddress): JsonObject? {
        val body = LinkedHashMap<String, kotlinx.serialization.json.JsonElement>()
        if (a.label != original.label) body["label"] = JsonPrimitive(a.label.wire)
        if (a.recipientName != original.recipientName) body["recipientName"] = JsonPrimitive(a.recipientName)
        if (a.recipientPhone != original.recipientPhone) body["recipientPhone"] = JsonPrimitive(a.recipientPhone)
        if (a.addressLine1 != original.addressLine1) body["addressLine1"] = JsonPrimitive(a.addressLine1)
        if (a.addressLine2 != original.addressLine2) body["addressLine2"] = a.addressLine2?.let { JsonPrimitive(it) } ?: JsonNull
        if (a.landmark != original.landmark) body["landmark"] = a.landmark?.let { JsonPrimitive(it) } ?: JsonNull
        if (a.city != original.city) body["city"] = JsonPrimitive(a.city)
        if (a.state != original.state) body["state"] = JsonPrimitive(a.state)
        if (a.postalCode != original.postalCode) body["postalCode"] = JsonPrimitive(a.postalCode.value)
        return if (body.isEmpty()) null else JsonObject(body)
    }
}
