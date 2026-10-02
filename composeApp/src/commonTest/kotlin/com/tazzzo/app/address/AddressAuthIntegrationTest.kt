package com.tazzzo.app.address

import com.tazzzo.app.auth.BASE
import com.tazzzo.app.auth.InMemorySecureTokenStore
import com.tazzzo.app.auth.JSON_HEADERS
import com.tazzzo.app.auth.apiClient
import com.tazzzo.app.auth.bearer
import com.tazzzo.app.auth.errorJson
import com.tazzzo.app.auth.refreshJson
import com.tazzzo.app.auth.tokens
import com.tazzzo.app.data.address.AddressBook
import com.tazzzo.app.data.address.AddressFailure
import com.tazzzo.app.data.address.BookState
import com.tazzzo.app.data.address.DeliveryLocation
import com.tazzzo.app.data.address.RemoteAddressDataSource
import com.tazzzo.app.data.address.SessionLocationBinding
import com.tazzzo.app.data.address.toAddressFailure
import com.tazzzo.app.data.auth.AuthSessionManager
import com.tazzzo.app.data.auth.RemoteAuthDataSource
import com.tazzzo.app.data.remote.ApiClient
import com.tazzzo.app.data.remote.ApiException
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.http.HttpStatusCode
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** The address API rides the PR-03A authenticated client: bearer, one refresh on 401, one retry, definitive rejection ends the session. */
class AddressAuthIntegrationTest {
    private val now = 1_000_000L

    private class Wire {
        var refreshCalls = 0
        val addressBearers = mutableListOf<String?>()
        var refreshStatus = HttpStatusCode.OK
        var addressesAlways401 = false
    }

    private fun TestScope.stack(w: Wire, store: InMemorySecureTokenStore): Pair<RemoteAddressDataSource, AuthSessionManager> {
        val authApi = apiClient {
            w.refreshCalls++
            if (w.refreshStatus == HttpStatusCode.OK) respond(refreshJson("fresh", "SES_abc.refNew"), HttpStatusCode.OK, JSON_HEADERS)
            else respond(errorJson("UNAUTHENTICATED"), w.refreshStatus, JSON_HEADERS)
        }
        val m = AuthSessionManager(RemoteAuthDataSource(authApi), store, backgroundScope, nowMs = { now })
        val gateway = ApiClient(baseUrl = BASE, tokenProvider = m, recovery = m, engine = MockEngine { req ->
            w.addressBearers += req.bearer()
            if (w.addressesAlways401 || req.bearer() != "Bearer fresh") respond(errorJson("UNAUTHENTICATED"), HttpStatusCode.Unauthorized, JSON_HEADERS)
            else respond(listJson(addrJson(isDefault = true)), HttpStatusCode.OK, JSON_HEADERS)
        })
        return RemoteAddressDataSource(gateway) to m
    }

    @Test fun theBearerIsAttachedToAddressCalls() = runTest {
        val w = Wire(); val (ds, m) = stack(w, InMemorySecureTokenStore(tokens("fresh", "SES_abc.r", now + 600_000))); m.restore()
        assertEquals(1, ds.list().size)
        assertEquals(listOf<String?>("Bearer fresh"), w.addressBearers); assertEquals(0, w.refreshCalls)
    }

    @Test fun anExpiredAccessTokenRefreshesOnceAndTheCallIsRetriedOnce() = runTest {
        val w = Wire(); val (ds, m) = stack(w, InMemorySecureTokenStore(tokens("stale", "SES_abc.r", now + 600_000))); m.restore()
        assertEquals(1, ds.list().size)
        assertEquals(listOf<String?>("Bearer stale", "Bearer fresh"), w.addressBearers)
        assertEquals(1, w.refreshCalls); assertTrue(m.isAuthenticated)
    }

    @Test fun aSecondRejectionStopsTheRecoveryLoop() = runTest {
        val w = Wire().apply { addressesAlways401 = true }
        val (ds, m) = stack(w, InMemorySecureTokenStore(tokens("stale", "SES_abc.r", now + 600_000))); m.restore()
        val e = assertFailsWith<ApiException> { ds.list() }
        assertEquals(AddressFailure.Unauthenticated, e.toAddressFailure())
        assertEquals(2, w.addressBearers.size); assertEquals(1, w.refreshCalls)
    }

    @Test fun aDefinitiveAuthRejectionEndsTheSessionAndWipesTheAddressBook() = runTest {
        val w = Wire().apply { refreshStatus = HttpStatusCode.Unauthorized }
        val store = InMemorySecureTokenStore(tokens("stale", "SES_abc.r", now + 600_000))
        val (ds, m) = stack(w, store); m.restore()

        val fake = FakeAddressSource().apply { server.add(ca("ADDR_home001", isDefault = true, postal = "560102")) }
        val pins = FakePins(); val sel = MemorySelection()
        val book = AddressBook(backgroundScope, fake) { m.isAuthenticated }
        val location = DeliveryLocation(backgroundScope, pins, sel, book)
        SessionLocationBinding(backgroundScope, m.active, book, location).start(initiallyAuthenticated = true)
        runCurrent()
        assertTrue(book.state.value is BookState.Loaded)
        location.selectAddress(ca("ADDR_home001", postal = "560102"))

        assertFailsWith<ApiException> { ds.list() }          // 401 -> refresh rejected (definitive)
        runCurrent()
        assertFalse(m.isAuthenticated); assertEquals(null, store.current)
        assertEquals(BookState.SignedOut, book.state.value, "no addresses may outlive the session")
        assertEquals(null, location.selectedAddressId.value); assertEquals("560047", pins.pin.value.value)
    }

    @Test fun aTransientRefreshFailureKeepsTheSessionAndTheBook() = runTest {
        val w = Wire().apply { refreshStatus = HttpStatusCode.ServiceUnavailable }
        val (ds, m) = stack(w, InMemorySecureTokenStore(tokens("stale", "SES_abc.r", now + 600_000))); m.restore()
        val fake = FakeAddressSource().apply { server.add(ca()) }
        val book = AddressBook(backgroundScope, fake) { m.isAuthenticated }
        book.load(); runCurrent()
        assertFailsWith<ApiException> { ds.list() }
        runCurrent()
        assertTrue(m.isAuthenticated); assertTrue(book.state.value is BookState.Loaded)
    }
}
