package com.tazzzo.app.address

import com.tazzzo.app.auth.JSON_HEADERS
import com.tazzzo.app.auth.bodyText
import com.tazzzo.app.auth.bearer
import com.tazzzo.app.auth.errorJson
import com.tazzzo.app.data.address.AddressBodies
import com.tazzzo.app.data.address.AddressFailure
import com.tazzzo.app.data.address.AddressLabel
import com.tazzzo.app.data.address.AddressServiceability
import com.tazzzo.app.data.address.AddressValidation
import com.tazzzo.app.data.address.AddressValidator
import com.tazzzo.app.data.address.RemoteAddressDataSource
import com.tazzzo.app.data.address.hint
import com.tazzzo.app.data.address.title
import com.tazzzo.app.data.address.toAddressFailure
import com.tazzzo.app.data.remote.ApiError
import com.tazzzo.app.data.remote.ApiException
import io.ktor.client.engine.mock.respond
import io.ktor.client.request.HttpRequestData
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpMethod
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonObject
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue

class RemoteAddressDataSourceTest {
    private val id = "ADDR_aaaaaa1"
    private val json = Json

    private fun valid() = (AddressValidator.validate(input(line2 = "Sector 6")) as AddressValidation.Valid).address

    // ---- routes, auth, headers --------------------------------------------------------------------------------

    @Test fun listIsAnAuthenticatedGetWithTheBearerAndNoCustomerId() = runTest {
        var seen: HttpRequestData? = null
        val ds = RemoteAddressDataSource(authedApi("tok-1") { seen = it; respond(listJson(addrJson(isDefault = true), addrJson(id = "ADDR_bbbbbb2")), HttpStatusCode.OK, JSON_HEADERS) })
        val items = ds.list()
        assertEquals(HttpMethod.Get, seen!!.method); assertEquals("/v1/customer/addresses", seen!!.url.encodedPath)
        assertEquals("Bearer tok-1", seen!!.bearer())
        assertTrue(seen!!.url.parameters.isEmpty(), "no customer id or other parameters")
        assertEquals(listOf("ADDR_aaaaaa1", "ADDR_bbbbbb2"), items.map { it.addressId })          // server order preserved
        assertTrue(items[0].isDefault); assertFalse(items[1].isDefault)
    }

    @Test fun getOneUsesTheIdPath() = runTest {
        var seen: HttpRequestData? = null
        val ds = RemoteAddressDataSource(authedApi { seen = it; respond(addrJson(version = 4), HttpStatusCode.OK, JSON_HEADERS) })
        val a = ds.get(id)
        assertEquals("/v1/customer/addresses/$id", seen!!.url.encodedPath); assertEquals(HttpMethod.Get, seen!!.method)
        assertEquals(4, a.version)
    }

    @Test fun createPostsTheValidatedBodyWithoutIfMatchAndReads201() = runTest {
        var seen: HttpRequestData? = null
        val ds = RemoteAddressDataSource(authedApi { seen = it; respond(addrJson(id = "ADDR_cccccc3", isDefault = true, line2 = "Sector 6"), HttpStatusCode.Created, JSON_HEADERS) })
        val a = ds.create(valid())
        assertEquals(HttpMethod.Post, seen!!.method); assertEquals("/v1/customer/addresses", seen!!.url.encodedPath)
        assertNull(seen!!.headers[HttpHeaders.IfMatch])
        val body = json.parseToJsonElement(seen!!.bodyText()).jsonObject
        assertEquals(AddressBodies.create(valid()), body)
        assertFalse("latitude" in body && "longitude" in body)
        assertEquals("ADDR_cccccc3", a.addressId); assertEquals("Sector 6", a.addressLine2)
    }

    @Test fun patchSendsIfMatchBuiltFromTheVersionAndOnlyTheBody() = runTest {
        var seen: HttpRequestData? = null
        val ds = RemoteAddressDataSource(authedApi { seen = it; respond(addrJson(version = 8), HttpStatusCode.OK, headersOf(HttpHeaders.ContentType to listOf("application/json"), HttpHeaders.ETag to listOf("\"address-8\""))) })
        val body = AddressBodies.patch(ca(), (AddressValidator.validate(input(city = "Mysuru")) as AddressValidation.Valid).address)!!
        val updated = ds.update(id, 7, body)
        assertEquals(HttpMethod.Patch, seen!!.method); assertEquals("/v1/customer/addresses/$id", seen!!.url.encodedPath)
        assertEquals("\"address-7\"", seen!!.headers[HttpHeaders.IfMatch])            // the version the edit was based on
        assertEquals(body, json.parseToJsonElement(seen!!.bodyText()).jsonObject)
        assertEquals(8, updated.version)                                              // the new version comes from the response
    }

    @Test fun deleteSendsIfMatchAndAccepts204() = runTest {
        var seen: HttpRequestData? = null
        val ds = RemoteAddressDataSource(authedApi { seen = it; respond("", HttpStatusCode.NoContent) })
        ds.delete(id, 3)
        assertEquals(HttpMethod.Delete, seen!!.method); assertEquals("\"address-3\"", seen!!.headers[HttpHeaders.IfMatch])
        assertEquals("", seen!!.bodyText())
    }

    @Test fun setDefaultIsAPutWithNoIfMatchAndNoBody() = runTest {
        var seen: HttpRequestData? = null
        val ds = RemoteAddressDataSource(authedApi { seen = it; respond(addrJson(isDefault = true, version = 5), HttpStatusCode.OK, JSON_HEADERS) })
        val a = ds.setDefault(id)
        assertEquals(HttpMethod.Put, seen!!.method); assertEquals("/v1/customer/addresses/$id/default", seen!!.url.encodedPath)
        assertNull(seen!!.headers[HttpHeaders.IfMatch]); assertEquals("", seen!!.bodyText())
        assertTrue(a.isDefault); assertEquals(5, a.version)           // the version is what the server says; never bumped client-side
    }

    @Test fun addressIdsAreValidatedBeforeTheyReachThePath() = runTest {
        val ds = RemoteAddressDataSource(authedApi { respond("{}", HttpStatusCode.OK, JSON_HEADERS) })
        for (bad in listOf("../auth", "ADDR_", "addr-1", "ADDR_a/b")) {
            assertFailsWith<IllegalArgumentException>(bad) { ds.get(bad) }
            assertFailsWith<IllegalArgumentException>(bad) { ds.delete(bad, 1) }
            assertFailsWith<IllegalArgumentException>(bad) { ds.setDefault(bad) }
        }
    }

    // ---- DTO mapping -------------------------------------------------------------------------------------------------

    @Test fun serviceabilityIsATriStateAndNullIsNeverFalse() = runTest {
        suspend fun map(s: String?) = RemoteAddressDataSource(authedApi { respond(addrJson(serviceable = s), HttpStatusCode.OK, JSON_HEADERS) }).get(id).serviceability
        assertEquals(AddressServiceability.SERVICEABLE, map("true"))
        assertEquals(AddressServiceability.NOT_SERVICEABLE, map("false"))
        assertEquals(AddressServiceability.UNKNOWN, map("null"))
        assertEquals(AddressServiceability.UNKNOWN, map(null))                      // block absent
    }

    @Test fun nullableFieldsAndUnknownExtrasAreHandled() = runTest {
        val ds = RemoteAddressDataSource(authedApi { respond(addrJson(extra = ""","fulfillmentLocationId":"FL_1","createdAt":"x","somethingNew":1"""), HttpStatusCode.OK, JSON_HEADERS) })
        val a = ds.get(id)
        assertNull(a.addressLine2); assertNull(a.landmark); assertNull(a.latitude); assertNull(a.longitude)
        assertEquals(AddressLabel.HOME, a.label); assertEquals("560102", a.postalCode.value); assertEquals("+919876543210", a.recipientPhone)
    }

    @Test fun malformedPayloadsAreDecodingErrorsNotGuesses() = runTest {
        for (bad in listOf(addrJson(label = "PARENTS"), addrJson(postal = "060047"), addrJson(version = 0), addrJson(id = "addr-1"))) {
            val e = assertFailsWith<ApiException> { RemoteAddressDataSource(authedApi { respond(bad, HttpStatusCode.OK, JSON_HEADERS) }).get(id) }
            assertIs<ApiError.Decoding>(e.error)
        }
    }

    // ---- failures -----------------------------------------------------------------------------------------------------

    private suspend fun failure(status: HttpStatusCode, body: String): AddressFailure =
        assertFailsWith<ApiException> { RemoteAddressDataSource(authedApi { respond(body, status, JSON_HEADERS) }).list() }.toAddressFailure()

    @Test fun statusCodesMapToTypedFailures() = runTest {
        assertEquals(AddressFailure.Unauthenticated, failure(HttpStatusCode.Unauthorized, errorJson("UNAUTHENTICATED")))
        assertEquals(AddressFailure.InvalidRequest, failure(HttpStatusCode.BadRequest, errorJson("INVALID_REQUEST")))
        assertEquals(AddressFailure.LimitReached, failure(HttpStatusCode.Conflict, errorJson("ADDRESS_LIMIT_REACHED")))
        assertEquals(AddressFailure.PreconditionFailed, failure(HttpStatusCode.PreconditionFailed, errorJson("PRECONDITION_FAILED")))
        assertEquals(AddressFailure.PreconditionRequired, failure(HttpStatusCode(428, "Precondition Required"), errorJson("PRECONDITION_REQUIRED")))
        assertEquals(AddressFailure.NotFound, failure(HttpStatusCode.NotFound, errorJson("NOT_FOUND")))
        assertEquals(AddressFailure.Unavailable, failure(HttpStatusCode.ServiceUnavailable, errorJson("SERVICE_UNAVAILABLE")))
        assertEquals(AddressFailure.Server, failure(HttpStatusCode.InternalServerError, errorJson("INTERNAL")))
    }

    @Test fun transportFailuresAreNetworkAndTimeout() {
        assertEquals(AddressFailure.Network, ApiException(ApiError.Network).toAddressFailure())
        assertEquals(AddressFailure.Timeout, ApiException(ApiError.Timeout).toAddressFailure())
        assertEquals(AddressFailure.Unknown, RuntimeException("x").toAddressFailure())
    }

    @Test fun onlyAmbiguousFailuresMayHaveReachedTheServer() {
        assertTrue(AddressFailure.Network.mayHaveReachedServer && AddressFailure.Timeout.mayHaveReachedServer)
        assertTrue(AddressFailure.Server.mayHaveReachedServer && AddressFailure.Unknown.mayHaveReachedServer)
        for (definite in listOf(AddressFailure.InvalidRequest, AddressFailure.LimitReached, AddressFailure.Unauthenticated, AddressFailure.Unavailable, AddressFailure.NotFound, AddressFailure.PreconditionFailed)) {
            assertFalse(definite.mayHaveReachedServer, "$definite")
        }
    }

    @Test fun errorsAndCopyNeverCarryPersonalDataOrServerText() = runTest {
        val e = assertFailsWith<ApiException> {
            RemoteAddressDataSource(authedApi { respond(errorJson("INVALID_REQUEST").replace("internal detail", "Asha Rao 9876543210 14th Main"), HttpStatusCode.BadRequest, JSON_HEADERS) }).create(valid())
        }
        for (secret in listOf("Asha", "9876543210", "14th Main", "560102", "Bengaluru", "internal detail")) {
            assertFalse(secret in e.message.orEmpty() && !secret.startsWith("internal"), secret)
            assertFalse(secret in e.toString() && secret != "internal detail", secret)
        }
        for (f in listOf(AddressFailure.InvalidRequest, AddressFailure.LimitReached, AddressFailure.PreconditionFailed, AddressFailure.Network, AddressFailure.Server)) {
            for (t in listOf(f.title, f.hint)) for (bad in listOf("400", "409", "412", "428", "INVALID", "ADDRESS_LIMIT", "req_")) assertFalse(bad in t, t)
        }
    }

    @Test fun networkFailureTextDoesNotCarryTheRequestBody() = runTest {
        val e = assertFailsWith<ApiException> {
            RemoteAddressDataSource(authedApi { throw RuntimeException("failed sending Asha Rao 9876543210") }).create(valid())
        }
        assertFalse("Asha" in e.message.orEmpty() || "9876543210" in e.message.orEmpty())
        assertEquals(AddressFailure.Network, e.toAddressFailure())
    }
}
