package com.tazzzo.app.catalog

import com.russhwolf.settings.MapSettings
import com.tazzzo.app.data.catalog.CatalogFailure
import com.tazzzo.app.data.catalog.LaunchContext
import com.tazzzo.app.data.catalog.PersistentPinStore
import com.tazzzo.app.data.catalog.PinStore
import com.tazzzo.app.data.catalog.Pincode
import com.tazzzo.app.data.catalog.ServiceabilityChecker
import com.tazzzo.app.data.catalog.ServiceabilityResult
import com.tazzzo.app.data.catalog.ServiceabilityState
import com.tazzzo.app.data.local.PersistentStore
import com.tazzzo.app.data.remote.ApiError
import com.tazzzo.app.data.remote.ApiException
import io.ktor.client.engine.mock.respond
import io.ktor.client.request.HttpRequestData
import io.ktor.http.HttpStatusCode
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue

class ServiceabilityStateTest {

    private class MemoryPins(var value: String? = null) : PinStore {
        override fun load() = value
        override fun save(pin: String) { value = pin }
    }

    private class Fake : ServiceabilityChecker {
        val asked = mutableListOf<String>()
        var gate: CompletableDeferred<Unit>? = null
        var answer: (Pincode) -> ServiceabilityResult = { ServiceabilityResult(true, "SA-1", 1, null, null) }
        var error: Throwable? = null
        override suspend fun check(pin: Pincode): ServiceabilityResult {
            asked += pin.value; gate?.await(); error?.let { throw it }; return answer(pin)
        }
    }

    private fun TestScope.ctx(f: Fake, pins: PinStore = MemoryPins()) = LaunchContext(pins, f, backgroundScope)

    // ---- PIN validation ----------------------------------------------------------------------------

    @Test fun validPins() {
        assertEquals("560047", Pincode.parse("560047")!!.value)
        assertEquals("560047", Pincode.parse(" 560047 ")!!.value) // trimmed, as the backend does
        assertEquals("560047", Pincode.LAUNCH.value)
        assertTrue(Pincode.isValid("110001") && Pincode.isValid("999999"))
    }

    @Test fun leadingZeroIsInvalid() { assertNull(Pincode.parse("060047")); assertNull(Pincode.parse("000000")) }

    @Test fun wrongLengthAndNonDigitsAreInvalid() {
        for (bad in listOf("", "56004", "5600477", "56004a", "560 047", "+560047", "56.047", null)) assertNull(Pincode.parse(bad), "$bad")
    }

    // ---- no lat/lng, PIN only (the wire itself) -------------------------------------------------------

    @Test fun theServiceabilityRequestCarriesOnlyThePin() = runTest {
        var seen: HttpRequestData? = null
        serviceabilitySource { seen = it; respond(serviceabilityJson(true), HttpStatusCode.OK, JSON) }.check(Pincode.LAUNCH)
        assertEquals(setOf("pin"), seen!!.url.parameters.names())
    }

    // ---- launch PIN + persistence -----------------------------------------------------------------------

    @Test fun startsAtTheLaunchPinAndPersistsNothingUntilChanged() = runTest {
        val pins = MemoryPins(); val c = ctx(Fake(), pins)
        assertEquals("560047", c.pin.value.value); assertNull(pins.value)
        assertIs<ServiceabilityState.Unknown>(c.state.value)
    }

    @Test fun aPersistedValidPinIsRestoredAndAJunkOneIsIgnored() = runTest {
        assertEquals("560102", ctx(Fake(), MemoryPins("560102")).pin.value.value)
        assertEquals("560047", ctx(Fake(), MemoryPins("12")).pin.value.value)
        assertEquals("560047", ctx(Fake(), MemoryPins("060047")).pin.value.value)
    }

    @Test fun thePinPersistsThroughPersistentStoreAsNonSecretState() = runTest {
        val settings = MapSettings(); val store = PersistentStore(settings)
        val c = ctx(Fake(), PersistentPinStore(store))
        assertTrue(c.setPin("560102"))
        assertEquals("560102", PersistentStore(settings).launchPin)
    }

    // ---- states ---------------------------------------------------------------------------------------------

    @Test fun unknownLoadingThenServiceable() = runTest {
        val f = Fake().apply { gate = CompletableDeferred() }; val c = ctx(f)
        c.refresh(); runCurrent()
        assertIs<ServiceabilityState.Loading>(c.state.value)
        f.gate!!.complete(Unit); runCurrent()
        val s = assertIs<ServiceabilityState.Serviceable>(c.state.value)
        assertNull(s.result.etaMinutesMin, "ETA is not required to be serviceable")
        assertEquals(listOf("560047"), f.asked)
    }

    @Test fun notServiceable() = runTest {
        val f = Fake().apply { answer = { ServiceabilityResult(false, null, null, null, null) } }; val c = ctx(f)
        c.refresh(); runCurrent()
        assertIs<ServiceabilityState.NotServiceable>(c.state.value)
    }

    @Test fun failedCarriesATypedRetryableFailure() = runTest {
        val f = Fake().apply { error = ApiException(ApiError.Http(429, "RATE_LIMITED", retryAfterSeconds = 9)) }; val c = ctx(f)
        c.refresh(); runCurrent()
        assertEquals(ServiceabilityState.Failed(CatalogFailure.RateLimited(9)), c.state.value)
        f.error = null; c.refresh(); runCurrent()
        assertIs<ServiceabilityState.Serviceable>(c.state.value)
    }

    @Test fun changingThePinResetsAndRechecks() = runTest {
        val f = Fake(); val c = ctx(f)
        c.refresh(); runCurrent()
        assertTrue(c.setPin("560102")); runCurrent()
        assertEquals(listOf("560047", "560102"), f.asked); assertEquals("560102", c.pin.value.value)
    }

    @Test fun anInvalidPinChangesNothingAndMakesNoRequest() = runTest {
        val pins = MemoryPins(); val f = Fake(); val c = ctx(f, pins)
        assertFalse(c.setPin("060047")); assertFalse(c.setPin("abc")); runCurrent()
        assertEquals("560047", c.pin.value.value); assertTrue(f.asked.isEmpty()); assertNull(pins.value)
    }

    @Test fun aSlowAnswerForTheOldPinCannotOverwriteTheNewOne() = runTest {
        val f = Fake().apply { gate = CompletableDeferred(); answer = { p -> ServiceabilityResult(p.value == "560102", null, null, null, null) } }
        val c = ctx(f)
        c.refresh(); runCurrent()
        c.setPin("560102"); runCurrent()
        f.gate!!.complete(Unit); runCurrent()
        assertIs<ServiceabilityState.Serviceable>(c.state.value) // the 560102 result, not the superseded 560047 one
    }
}
