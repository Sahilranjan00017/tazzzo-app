package com.tazzzo.app.data.remote

import kotlin.random.Random

/**
 * `If-Match` values. The backend uses one opaque-prefix family per resource
 * (`profile-`, `address-`, `cart-`) and rejects a wrong prefix with 400.
 * Quotes are optional on input; this helper always sends them, as the server's
 * own `ETag` does.
 */
object IfMatch {
    const val PROFILE = "profile"
    const val ADDRESS = "address"
    const val CART = "cart"

    /** `"cart-7"` from prefix `cart` and version `7`. */
    fun of(prefix: String, version: Long): String {
        require(prefix.isNotEmpty() && prefix.all { it.isLetter() }) { "Invalid If-Match prefix" }
        require(version >= 0) { "Version must not be negative" }
        return "\"$prefix-$version\""
    }

    /** Reuses an `ETag` header value exactly as the server sent it. */
    fun fromEtag(etag: String): String {
        require(etag.isNotBlank()) { "Blank ETag" }
        return etag
    }
}

/**
 * `Idempotency-Key` values. Only the checkout-quote endpoint uses one; the key
 * must be reused verbatim on a retry of the same intent and replaced when the
 * cart or address changes. Format is the backend's `^[A-Za-z0-9_-]{8,64}$`.
 */
object IdempotencyKey {
    private val FORMAT = Regex("^[A-Za-z0-9_-]{8,64}$")

    fun isValid(key: String): Boolean = FORMAT.matches(key)

    fun require(key: String): String {
        require(isValid(key)) { "Idempotency-Key must match ${FORMAT.pattern}" }
        return key
    }

    /** 32 hex characters. Uniqueness, not secrecy, is the requirement. */
    fun generate(random: Random = Random.Default): String =
        random.nextBytes(16).joinToString("") { (it.toInt() and 0xFF).toString(16).padStart(2, '0') }
}

/**
 * Supplies the current access token. Returning null (or blank) means "no
 * session": no `Authorization` header is sent.
 */
fun interface AccessTokenProvider {
    suspend fun accessToken(): String?
}

/**
 * Asked once when an authenticated request is rejected with 401.
 * [rejectedToken] is the access token that was refused. Return true only if a
 * newer token is now available, so the request may be re-sent once.
 */
fun interface AuthRecovery {
    suspend fun recover(rejectedToken: String): Boolean
}
