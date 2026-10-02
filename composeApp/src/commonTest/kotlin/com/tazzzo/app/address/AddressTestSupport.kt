package com.tazzzo.app.address

import com.tazzzo.app.auth.BASE
import com.tazzzo.app.data.address.AddressInput
import com.tazzzo.app.data.address.AddressLabel
import com.tazzzo.app.data.address.AddressServiceability
import com.tazzzo.app.data.address.AddressSource
import com.tazzzo.app.data.address.CustomerAddress
import com.tazzzo.app.data.address.SelectionStore
import com.tazzzo.app.data.address.ValidAddress
import com.tazzzo.app.data.catalog.LocationPin
import com.tazzzo.app.data.catalog.Pincode
import com.tazzzo.app.data.remote.ApiClient
import com.tazzzo.app.data.remote.ApiError
import com.tazzzo.app.data.remote.ApiException
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.MockRequestHandleScope
import io.ktor.client.request.HttpRequestData
import io.ktor.client.request.HttpResponseData
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.serialization.json.JsonObject

fun pin(v: String) = Pincode.parse(v)!!

fun ca(
    id: String = "ADDR_aaaaaa1",
    label: AddressLabel = AddressLabel.HOME,
    postal: String = "560102",
    isDefault: Boolean = false,
    version: Long = 1,
    serviceability: AddressServiceability = AddressServiceability.SERVICEABLE,
    line2: String? = null,
    landmark: String? = null
) = CustomerAddress(
    addressId = id, label = label, recipientName = "Asha Rao", recipientPhone = "+919876543210",
    addressLine1 = "22, 14th Main", addressLine2 = line2, landmark = landmark, city = "Bengaluru", state = "Karnataka",
    postalCode = pin(postal), latitude = null, longitude = null, isDefault = isDefault, version = version, serviceability = serviceability
)

fun input(
    label: AddressLabel? = AddressLabel.HOME,
    name: String = "Asha Rao",
    phone: String = "9876543210",
    line1: String = "22, 14th Main",
    line2: String = "",
    landmark: String = "",
    city: String = "Bengaluru",
    state: String = "Karnataka",
    postal: String = "560102"
) = AddressInput(label, name, phone, line1, line2, landmark, city, state, postal)

fun http(status: Int, code: String? = null, retryAfter: Long? = null) =
    ApiException(ApiError.Http(status, code, retryAfterSeconds = retryAfter))

/** Wire JSON of one address in the RUNNING backend's shape. `serviceable` is the literal true / false / null (or omitted). */
fun addrJson(
    id: String = "ADDR_aaaaaa1", label: String = "HOME", version: Long = 1, isDefault: Boolean = false,
    serviceable: String? = "true", postal: String = "560102", extra: String = "", line2: String? = null, landmark: String? = null
): String = buildString {
    append("""{"addressId":"$id","label":"$label","recipientName":"Asha Rao","recipientPhone":"+919876543210",""")
    append(""""addressLine1":"22, 14th Main",""")
    append(""""addressLine2":${line2?.let { "\"$it\"" } ?: "null"},"landmark":${landmark?.let { "\"$it\"" } ?: "null"},""")
    append(""""city":"Bengaluru","state":"Karnataka","postalCode":"$postal","latitude":null,"longitude":null,""")
    append(""""isDefault":$isDefault,"version":$version,""")
    if (serviceable != null) append(""""serviceability":{"serviceable":$serviceable},""")
    append(""""requestId":"req_1"$extra}""")
}

fun listJson(vararg items: String) = """{"items":[${items.joinToString(",")}],"requestId":"req_l"}"""

/** An authenticated client with a fixed bearer, for data-source contract tests. */
fun authedApi(token: String? = "acc1", handler: suspend MockRequestHandleScope.(HttpRequestData) -> HttpResponseData) =
    ApiClient(baseUrl = BASE, tokenProvider = { token }, engine = MockEngine(handler))

/** A scripted, in-memory "server" the AddressBook can be tested against. */
class FakeAddressSource : AddressSource {
    val server = mutableListOf<CustomerAddress>()
    val calls = mutableListOf<String>()
    var listError: Throwable? = null
    var createError: Throwable? = null
    /** When [createError] is set: did the server nevertheless apply the create (a lost response)? */
    var createAppliedDespiteError = false
    var updateError: Throwable? = null
    var deleteError: Throwable? = null
    var defaultError: Throwable? = null
    var createGate: CompletableDeferred<Unit>? = null
    var lastPatch: JsonObject? = null
    private var seq = 100

    fun callsOf(prefix: String) = calls.count { it.startsWith(prefix) }

    override suspend fun list(): List<CustomerAddress> {
        calls += "list"; listError?.let { throw it }
        return server.toList()
    }

    override suspend fun create(address: ValidAddress): CustomerAddress {
        calls += "create"
        createGate?.await()
        val made = CustomerAddress(
            "ADDR_new${seq++}", address.label, address.recipientName, address.recipientPhone, address.addressLine1,
            address.addressLine2, address.landmark, address.city, address.state, address.postalCode, null, null,
            isDefault = server.isEmpty(), version = 1, serviceability = AddressServiceability.SERVICEABLE
        )
        createError?.let { e -> if (createAppliedDespiteError) server.add(made); throw e }
        server.add(made)
        return made
    }

    override suspend fun update(addressId: String, version: Long, body: JsonObject): CustomerAddress {
        calls += "update:$addressId:v$version"; lastPatch = body
        updateError?.let { throw it }
        val i = server.indexOfFirst { it.addressId == addressId }
        if (i < 0) throw http(404, "NOT_FOUND")
        if (server[i].version != version) throw http(412, "PRECONDITION_FAILED")
        server[i] = server[i].copy(version = version + 1)
        return server[i]
    }

    override suspend fun delete(addressId: String, version: Long) {
        calls += "delete:$addressId:v$version"
        deleteError?.let { throw it }
        val i = server.indexOfFirst { it.addressId == addressId }
        if (i < 0) throw http(404, "NOT_FOUND")
        if (server[i].version != version) throw http(412, "PRECONDITION_FAILED")
        val wasDefault = server[i].isDefault
        server.removeAt(i)
        // The backend, not the client, promotes the replacement default.
        if (wasDefault && server.isNotEmpty()) server[0] = server[0].copy(isDefault = true)
    }

    override suspend fun setDefault(addressId: String): CustomerAddress {
        calls += "default:$addressId"
        defaultError?.let { throw it }
        val target = server.firstOrNull { it.addressId == addressId } ?: throw http(404, "NOT_FOUND")
        val updated = server.map { it.copy(isDefault = it.addressId == addressId) }       // version untouched, like the backend
        server.clear(); server.addAll(updated.sortedByDescending { it.isDefault })
        return server.first { it.addressId == target.addressId }
    }
}

class FakePins(initial: String = "560047") : LocationPin {
    private val _pin = MutableStateFlow(pin(initial))
    override val pin: StateFlow<Pincode> = _pin
    val writes = mutableListOf<String>()
    override fun setPin(raw: String): Boolean {
        val p = Pincode.parse(raw) ?: return false
        writes += p.value; _pin.value = p; return true
    }
}

class MemorySelection(override var selectedAddressId: String? = null) : SelectionStore
