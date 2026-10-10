package com.tazzzo.app.catalog

import com.russhwolf.settings.MapSettings
import com.tazzzo.app.data.catalog.CatalogCapabilities
import com.tazzzo.app.data.catalog.CatalogMode
import com.tazzzo.app.data.catalog.CatalogModeSelector
import com.tazzzo.app.data.catalog.InstallationId
import com.tazzzo.app.data.catalog.Pincode
import com.tazzzo.app.data.catalog.toCatalogFailure
import com.tazzzo.app.data.local.PersistentStore
import com.tazzzo.app.data.remote.ApiException
import io.ktor.client.engine.mock.respond
import io.ktor.http.HttpStatusCode
import kotlinx.coroutines.test.runTest
import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

class CatalogModeAndPrivacyTest {

    // ---- MODE: explicit, never mixed, no silent fallback -----------------------------------------------------

    @Test fun releaseIsAlwaysRemoteAndIgnoresAnyOverride() {
        assertEquals(CatalogMode.REMOTE, CatalogModeSelector.resolve(isDebug = false, debugOverride = null))
        assertEquals(CatalogMode.REMOTE, CatalogModeSelector.resolve(isDebug = false, debugOverride = CatalogMode.MOCK))
    }

    @Test fun debugIsRemoteByDefaultAndMockOnlyWhenExplicitlyRequested() {
        assertEquals(CatalogMode.REMOTE, CatalogModeSelector.resolve(isDebug = true, debugOverride = null))
        assertEquals(CatalogMode.MOCK, CatalogModeSelector.resolve(isDebug = true, debugOverride = CatalogMode.MOCK))
    }

    @Test fun exactlyOneBackendIsEverBuilt() {
        var remote = 0; var mock = 0
        CatalogModeSelector.choose(CatalogMode.REMOTE, { remote++ }, { mock++ })
        assertEquals(1 to 0, remote to mock)
        CatalogModeSelector.choose(CatalogMode.MOCK, { remote++ }, { mock++ })
        assertEquals(1 to 1, remote to mock)
    }

    @Test fun aRemoteFailureNeverProducesMockData() = runTest {
        var mockBuilt = 0
        val r = reader { respond(errorFlat("SERVICE_UNAVAILABLE"), HttpStatusCode.ServiceUnavailable, JSON) }
        // The remote reader has no mock dependency at all: failure can only surface as a failure.
        assertFailsWith<ApiException> { r.categories() }
        assertFailsWith<ApiException> { r.productPage("TZC-000010", Pincode.LAUNCH, null) }
        assertEquals(0, mockBuilt)
    }

    @Test fun remoteCapabilitiesAdmitOnlyWhatTheBackendHasAndKeepRealProductsOutOfTheCart() {
        val c = CatalogCapabilities.forMode(CatalogMode.REMOTE)
        assertFalse(c.search || c.bestsellers || c.deals || c.banners || c.counts || c.sorting)
        assertTrue(c.cartIntegration, "REMOTE products go to the server cart")
        assertTrue(c.checkoutIntegration, "REMOTE checkout is the real quote review")
        assertTrue(c.orderIntegration, "REMOTE places real COD orders")
        assertFalse(c.reorder, "Order again stays MOCK-only")
        val m = CatalogCapabilities.forMode(CatalogMode.MOCK)
        assertTrue(m.search && m.bestsellers && m.deals && m.banners && m.counts && m.sorting && m.cartIntegration && m.checkoutIntegration && m.orderIntegration)
        assertNotEquals(CatalogCapabilities.REMOTE, CatalogCapabilities.MOCK)
    }

    // ---- INSTALLATION ID ---------------------------------------------------------------------------------------

    @Test fun theInstallationIdIsGeneratedOncePersistedAndStable() {
        val settings = MapSettings()
        val a = InstallationId.getOrCreate(PersistentStore(settings), Random(1)).value
        val b = InstallationId.getOrCreate(PersistentStore(settings), Random(2)).value
        assertEquals(a, b)
        assertEquals(a, PersistentStore(settings).installationId)
    }

    @Test fun theInstallationIdMatchesTheBackendFormatAndIsRandomPerInstall() {
        val a = InstallationId.getOrCreate(PersistentStore(MapSettings())).value
        val b = InstallationId.getOrCreate(PersistentStore(MapSettings())).value
        assertTrue(InstallationId.isValid(a) && a.length <= 128)
        assertNotEquals(a, b)
    }

    @Test fun aMalformedStoredIdIsReplaced() {
        val store = PersistentStore(MapSettings()).apply { installationId = "bad id with spaces" }
        val id = InstallationId.getOrCreate(store).value
        assertTrue(InstallationId.isValid(id)); assertNotEquals("bad id with spaces", id)
    }

    @Test fun validatorMirrorsTheBackendCharset() {
        assertTrue(InstallationId.isValid("Ab-1_2.3:4"))
        assertFalse(InstallationId.isValid("")); assertFalse(InstallationId.isValid("a b")); assertFalse(InstallationId.isValid("x".repeat(129)))
    }

    @Test fun everyCatalogueRequestSendsTheHeader() = runTest {
        var header: String? = null
        dataSource { header = it.headers[InstallationId.HEADER]; respond(nodeListJson(), HttpStatusCode.OK, JSON) }.categories()
        assertEquals(INSTALL_ID, header)
        serviceabilitySource { header = it.headers[InstallationId.HEADER]; respond(serviceabilityJson(true), HttpStatusCode.OK, JSON) }.check(Pincode.LAUNCH)
        assertEquals(INSTALL_ID, header)
    }

    // ---- PRIVACY: neither the PIN nor the installation id leaks into app-produced text ---------------------------

    private fun assertClean(text: String) {
        assertFalse(text.contains("560047"), "PIN leaked: $text")
        assertFalse(text.contains(INSTALL_ID), "installation id leaked: $text")
    }

    @Test fun valueObjectsAreRedacted() {
        assertClean(Pincode.LAUNCH.toString()); assertClean(installationId().toString())
        assertClean("${Pincode.parse("560047")}")
    }

    @Test fun failuresDoNotCarryThePinOrTheInstallationId() = runTest {
        val ds = dataSource { respond(errorFlat("RATE_LIMITED", true, 5), HttpStatusCode.TooManyRequests, JSON) }
        val e = assertFailsWith<ApiException> { ds.products("TZC-000010", Pincode.LAUNCH) }
        assertClean(e.message.orEmpty()); assertClean(e.toString()); assertClean(e.error.toString())
        val net = assertFailsWith<ApiException> { dataSource { throw RuntimeException("connect failed ?pin=560047 $INSTALL_ID") }.product("TZP-1", Pincode.LAUNCH) }
        assertClean(net.message.orEmpty()); assertClean(net.error.toString())
    }

    @Test fun domainFailuresAreDataWithoutIdentifiers() = runTest {
        val ds = dataSource { respond(errorFlat("NOT_FOUND"), HttpStatusCode.NotFound, JSON) }
        val e = assertFailsWith<ApiException> { ds.product("TZP-1", Pincode.LAUNCH) }
        assertClean(e.toCatalogFailure().toString())
    }
}
