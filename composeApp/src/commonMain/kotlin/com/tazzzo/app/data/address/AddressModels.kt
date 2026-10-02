package com.tazzzo.app.data.address

import com.tazzzo.app.data.catalog.Pincode
import com.tazzzo.app.data.remote.ApiError
import com.tazzzo.app.data.remote.ApiException

/*
 * The REAL customer-address model (backend `/v1/customer/addresses`). Backend is the authority;
 * nothing here is persisted to disk. Every type that can hold personal data redacts toString.
 */

/** Backend-supported labels only. There is no custom label, so nothing here cannot round-trip. */
enum class AddressLabel(val wire: String, val display: String) {
    HOME("HOME", "Home"), WORK("WORK", "Work"), OTHER("OTHER", "Other");

    companion object {
        fun fromWire(raw: String?): AddressLabel? = entries.firstOrNull { it.wire.equals(raw?.trim(), ignoreCase = true) }
    }
}

/**
 * Whether we deliver to this address's PIN. A real tri-state: [UNKNOWN] means the server could not
 * evaluate it (wire `null`) and is NEVER worded or treated as "not serviceable".
 */
enum class AddressServiceability {
    SERVICEABLE, NOT_SERVICEABLE, UNKNOWN;

    companion object {
        fun of(wire: Boolean?): AddressServiceability = when (wire) {
            true -> SERVICEABLE
            false -> NOT_SERVICEABLE
            null -> UNKNOWN
        }
    }
}

data class CustomerAddress(
    val addressId: String,
    val label: AddressLabel,
    val recipientName: String,
    /** Canonical `+91XXXXXXXXXX`. Delivery contact only — not the login phone. */
    val recipientPhone: String,
    val addressLine1: String,
    val addressLine2: String?,
    val landmark: String?,
    val city: String,
    val state: String,
    val postalCode: Pincode,
    val latitude: Double?,
    val longitude: Double?,
    val isDefault: Boolean,
    /** The server's concurrency version; sent back as `If-Match: "address-<version>"` on PATCH / DELETE. */
    val version: Long,
    val serviceability: AddressServiceability
) {
    override fun toString(): String = "CustomerAddress(***)"
}

/** Everything that can go wrong talking to the address API, as data. Never carries server text. */
sealed interface AddressFailure {
    data object Unauthenticated : AddressFailure
    data object InvalidRequest : AddressFailure
    data object LimitReached : AddressFailure
    /** 412: the address changed since it was read. */
    data object PreconditionFailed : AddressFailure
    /** 428: a client bug (a precondition was not sent). Never silently retried. */
    data object PreconditionRequired : AddressFailure
    data object NotFound : AddressFailure
    data object Unavailable : AddressFailure
    data object Server : AddressFailure
    data object Network : AddressFailure
    data object Timeout : AddressFailure
    data object Unknown : AddressFailure

    val isRetryable: Boolean
        get() = this is Network || this is Timeout || this is Unavailable || this is Server

    /**
     * True when a request that failed this way MAY still have been applied by the server — a lost
     * response, a timeout, a 500, an undecodable body. `POST /v1/customer/addresses` is not
     * idempotent, so after such a failure success can be neither assumed nor ruled out.
     */
    val mayHaveReachedServer: Boolean get() = this is Network || this is Timeout || this is Server || this is Unknown
}

val AddressFailure.title: String
    get() = when (this) {
        AddressFailure.Unauthenticated -> "Please log in"
        AddressFailure.InvalidRequest -> "Check the details"
        AddressFailure.LimitReached -> "Address limit reached"
        AddressFailure.PreconditionFailed -> "This address changed"
        AddressFailure.PreconditionRequired, AddressFailure.Unknown -> "Something went wrong"
        AddressFailure.NotFound -> "Address not found"
        AddressFailure.Unavailable -> "Temporarily unavailable"
        AddressFailure.Server -> "Something went wrong at our end"
        AddressFailure.Network -> "No internet connection"
        AddressFailure.Timeout -> "That took too long"
    }

val AddressFailure.hint: String
    get() = when (this) {
        AddressFailure.Unauthenticated -> "Log in to manage your addresses."
        AddressFailure.InvalidRequest -> "Please review the address and try again."
        AddressFailure.LimitReached -> "You can save up to 10 addresses."
        AddressFailure.PreconditionFailed -> "Review the latest details and try again."
        AddressFailure.NotFound -> "It may already have been removed."
        AddressFailure.Network -> "Check your connection and try again."
        AddressFailure.Timeout -> "Your connection looks slow. Try once more."
        AddressFailure.Unavailable -> "We're busy right now. Please try again shortly."
        AddressFailure.Server, AddressFailure.PreconditionRequired, AddressFailure.Unknown -> "Please try again in a moment."
    }

fun Throwable.toAddressFailure(): AddressFailure {
    val api = (this as? ApiException)?.error ?: return AddressFailure.Unknown
    return when (api) {
        ApiError.Network -> AddressFailure.Network
        ApiError.Timeout -> AddressFailure.Timeout
        is ApiError.Decoding -> AddressFailure.Unknown
        is ApiError.Http -> when {
            api.status == 401 -> AddressFailure.Unauthenticated
            api.code == "ADDRESS_LIMIT_REACHED" -> AddressFailure.LimitReached
            api.status == 412 -> AddressFailure.PreconditionFailed
            api.status == 428 -> AddressFailure.PreconditionRequired
            api.status == 404 -> AddressFailure.NotFound
            api.status == 400 -> AddressFailure.InvalidRequest
            api.status == 503 -> AddressFailure.Unavailable
            api.status in 500..599 -> AddressFailure.Server
            else -> AddressFailure.Unknown
        }
    }
}
