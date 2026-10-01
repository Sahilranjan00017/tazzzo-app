package com.tazzzo.app.auth

import com.tazzzo.app.data.auth.SecureBlobStore
import com.tazzzo.app.data.auth.SecureTokenStore
import com.tazzzo.app.data.auth.StoredTokens
import com.tazzzo.app.data.remote.ApiClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.MockRequestHandleScope
import io.ktor.client.request.HttpRequestData
import io.ktor.client.request.HttpResponseData
import io.ktor.http.HttpHeaders
import io.ktor.http.content.TextContent
import io.ktor.http.headersOf

const val BASE = "https://api.example.test"
val JSON_HEADERS = headersOf(HttpHeaders.ContentType, "application/json")

fun apiClient(handler: suspend MockRequestHandleScope.(HttpRequestData) -> HttpResponseData) =
    ApiClient(baseUrl = BASE, engine = MockEngine(handler))

fun HttpRequestData.bodyText(): String = (body as? TextContent)?.text.orEmpty()
fun HttpRequestData.bearer(): String? = headers[HttpHeaders.Authorization]

fun sessionJson(access: String = "acc1", refresh: String = "SES_abc.ref1", expiresIn: Long = 900) =
    """{"customerId":"CUS_1","accessToken":"$access","accessTokenExpiresIn":$expiresIn,"refreshToken":"$refresh","requestId":"req_1"}"""

fun refreshJson(access: String, refresh: String, expiresIn: Long = 900) =
    """{"accessToken":"$access","accessTokenExpiresIn":$expiresIn,"refreshToken":"$refresh","requestId":"req_2"}"""

fun errorJson(code: String, retryAfter: Long? = null) =
    """{"code":"$code","message":"internal detail","requestId":"req_e"${retryAfter?.let { ""","retryAfterSeconds":$it""" } ?: ""}}"""

fun tokens(access: String = "acc1", refresh: String = "SES_abc.ref1", expiresAtMs: Long = 10_000_000L) =
    StoredTokens(access, refresh, expiresAtMs, "CUS_1")

/** In-memory stand-in for Keystore/Keychain. */
class InMemorySecureTokenStore(var current: StoredTokens? = null) : SecureTokenStore {
    var saves = 0
    var clears = 0
    var failSaves = false
    val history = mutableListOf<StoredTokens>()
    override fun load(): StoredTokens? = current
    override fun save(tokens: StoredTokens) {
        if (failSaves) error("disk full")
        saves++; history += tokens; current = tokens
    }
    override fun clear() { clears++; current = null }
}

/** A raw blob slot that can be told to fail like an undecryptable Keystore entry. */
class FakeBlobStore(var value: String? = null, var failReads: Boolean = false) : SecureBlobStore {
    var deletes = 0
    override fun read(): String? { if (failReads) error("cannot decrypt"); return value }
    override fun write(value: String) { this.value = value }
    override fun delete() { deletes++; value = null }
}
