package com.tazzzo.app.catalog

import com.russhwolf.settings.MapSettings
import com.tazzzo.app.HomeTab
import com.tazzzo.app.TazzzoAppState
import com.tazzzo.app.applyDevCatalogMode
import com.tazzzo.app.data.catalog.CatalogCapabilities
import com.tazzzo.app.data.catalog.CatalogMode
import com.tazzzo.app.data.catalog.CatalogSource
import com.tazzzo.app.data.local.PersistentStore
import com.tazzzo.app.data.repository.ServiceLocator
import com.tazzzo.app.ui.home.visibleHomeTabs
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** REMOTE is the default; mock commerce data is never reachable from it, and never falls back. */
class RemoteModeWiringTest {

    private inline fun <T> inMode(mode: CatalogMode?, block: () -> T): T {
        val before = CatalogSource.debugOverride
        CatalogSource.debugOverride = mode
        try { return block() } finally { CatalogSource.debugOverride = before }
    }

    @Test fun theDefaultModeIsRemote() {
        inMode(null) {
            assertEquals(CatalogMode.REMOTE, ServiceLocator.catalogMode)
            assertEquals(CatalogCapabilities.REMOTE, ServiceLocator.catalogCapabilities)
        }
    }

    @Test fun mockIsOnlyEverEnteredByExplicitSelection() {
        inMode(CatalogMode.MOCK) {
            assertEquals(CatalogMode.MOCK, ServiceLocator.catalogMode)
            assertEquals(CatalogCapabilities.MOCK, ServiceLocator.catalogCapabilities)
        }
        inMode(null) { assertEquals(CatalogMode.REMOTE, ServiceLocator.catalogMode) }
    }

    @Test fun developmentFlagsAreOffSoApplyingThemLeavesTheModeRemote() {
        inMode(null) {
            applyDevCatalogMode()                          // no demo / mock flag is set in the unit-test process
            assertNull(CatalogSource.debugOverride)
            assertEquals(CatalogMode.REMOTE, ServiceLocator.catalogMode)
        }
    }

    // ---- no silent fallback to mock data ----------------------------------------------------------------------------

    @Test fun theLegacyMockCatalogueRefusesInRemoteModeInsteadOfAnsweringWithMockProducts() = runTest {
        inMode(null) {
            val c = ServiceLocator.catalog
            assertFailsWith<UnsupportedOperationException> { c.getCategories() }
            assertFailsWith<UnsupportedOperationException> { c.getProduct("p8") }
            assertFailsWith<UnsupportedOperationException> { c.getProducts("fruits") }
            assertFailsWith<UnsupportedOperationException> { c.getBestsellers() }
            assertFailsWith<UnsupportedOperationException> { c.getDeals() }
            assertFailsWith<UnsupportedOperationException> { c.getBanners() }
            assertFailsWith<UnsupportedOperationException> { c.getCounts() }
            assertFailsWith<UnsupportedOperationException> { c.search("milk") }
        }
    }

    @Test fun theMockCatalogueIsAvailableOnlyWhenMockWasExplicitlySelected() = runTest {
        inMode(CatalogMode.MOCK) {
            assertTrue(ServiceLocator.catalog.getProducts("fruits").isNotEmpty())
            assertTrue(ServiceLocator.catalog.search("milk").isNotEmpty())
        }
    }

    // ---- unsupported surfaces disappear --------------------------------------------------------------------------------

    @Test fun remoteModeShowsHomeShopOrdersProfileAndHidesDealsAndOrderAgain() {
        assertEquals(listOf(HomeTab.HOME, HomeTab.SHOP, HomeTab.ORDERS, HomeTab.PROFILE), visibleHomeTabs(CatalogCapabilities.REMOTE))
    }

    @Test fun mockModeKeepsEveryTab() {
        assertEquals(HomeTab.entries.toList(), visibleHomeTabs(CatalogCapabilities.MOCK))
    }

    @Test fun everyMockOnlyCapabilityIsOffInRemoteMode() {
        val c = CatalogCapabilities.forMode(CatalogMode.REMOTE)
        assertFalse(c.search); assertFalse(c.deals); assertFalse(c.bestsellers); assertFalse(c.banners)
        assertFalse(c.counts); assertFalse(c.sorting)
        assertTrue(c.cartIntegration); assertTrue(c.checkoutIntegration); assertFalse(c.orderIntegration)
    }

    // ---- the cart guard ------------------------------------------------------------------------------------------------------

    @Test fun remoteModeNeverRestoresAMockCartNorTouchesTheMockCatalogue() = runTest {
        inMode(null) {
            val store = PersistentStore(MapSettings())
            store.saveCart(listOf(PersistentStore.SavedCartLine("p8", 2, 2_900L)))   // a cart left by a mock/demo session
            val app = TazzzoAppState(store = store)
            app.restoreFromDisk()                                                    // would hit the (refusing) mock guard if it tried
            assertTrue(app.cartLines().isEmpty(), "no mock product may enter the cart in REMOTE mode")
        }
    }

    @Test fun mockModeStillRestoresItsCart() = runTest {
        inMode(CatalogMode.MOCK) {
            val store = PersistentStore(MapSettings())
            store.saveCart(listOf(PersistentStore.SavedCartLine("p8", 2, 2_900L)))
            val app = TazzzoAppState(store = store)
            app.restoreFromDisk()
            assertEquals(1, app.cartLines().size)
        }
    }

    @Test fun theGuidedTourThatSpotlightsMockHomeWidgetsIsSkippedInRemoteMode() {
        inMode(null) {
            val app = TazzzoAppState(store = PersistentStore(MapSettings()))
            app.requestGuidedTourIfFirstTime()
            assertFalse(app.guidedJourneyPending)
        }
        inMode(CatalogMode.MOCK) {
            val app = TazzzoAppState(store = PersistentStore(MapSettings()))
            app.requestGuidedTourIfFirstTime()
            assertTrue(app.guidedJourneyPending)
        }
    }
}
