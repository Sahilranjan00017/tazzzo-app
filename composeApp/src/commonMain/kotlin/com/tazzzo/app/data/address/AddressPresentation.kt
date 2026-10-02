package com.tazzzo.app.data.address

/** App-written copy for the address surfaces. Never server text, codes or ids. */

/** Tri-state badge. UNKNOWN is "unavailable", never "not serviceable". No ETA is invented. */
val AddressServiceability.badge: String
    get() = when (this) {
        AddressServiceability.SERVICEABLE -> "Delivers here"
        AddressServiceability.NOT_SERVICEABLE -> "Not serviceable"
        AddressServiceability.UNKNOWN -> "Availability unavailable"
    }

val AddressNotice.message: String
    get() = when (this) {
        AddressNotice.StaleRefreshed -> "This address changed. Review the latest details and try again."
        AddressNotice.NotFoundRefreshed -> "That address was already removed. Your list has been refreshed."
        AddressNotice.AmbiguousCreate ->
            "We couldn't confirm whether the address was saved. Check your saved addresses before trying again."
    }

val FieldError.message: String
    get() = when (this) {
        FieldError.Required -> "Required"
        FieldError.TooLong -> "Too long"
        FieldError.Invalid -> "Please check this"
    }

const val ADDRESS_LIMIT_MESSAGE = "You can save up to 10 addresses."

/** One line for a result the screen should tell the customer about; null when there is nothing to say. */
fun AddressActionResult.userMessage(): String? = when (this) {
    AddressActionResult.Success, AddressActionResult.Busy -> null
    is AddressActionResult.ValidationFailed, AddressActionResult.Rejected -> "Please check the details and try again."
    AddressActionResult.LimitReached -> ADDRESS_LIMIT_MESSAGE
    AddressActionResult.Stale -> AddressNotice.StaleRefreshed.message
    AddressActionResult.NotFound -> AddressNotice.NotFoundRefreshed.message
    AddressActionResult.AmbiguousCreate -> AddressNotice.AmbiguousCreate.message
    AddressActionResult.AuthRequired -> "Please log in again to manage your addresses."
    is AddressActionResult.Failed -> failure.title + ". " + failure.hint
}

/** "Deliver to Home · 560047?" */
fun CustomerAddress.suggestionText(): String = "Deliver to ${label.display} · ${postalCode.value}?"
