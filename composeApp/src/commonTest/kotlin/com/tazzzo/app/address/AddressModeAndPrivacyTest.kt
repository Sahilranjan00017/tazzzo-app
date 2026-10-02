package com.tazzzo.app.address

import com.russhwolf.settings.MapSettings
import com.tazzzo.app.TazzzoAppState
import com.tazzzo.app.data.address.AddressBook
import com.tazzzo.app.data.address.DeliveryLocation
import com.tazzzo.app.data.address.PersistentSelectionStore
import com.tazzzo.app.data.catalog.CatalogMode
import com.tazzzo.app.data.catalog.CatalogSource
import com.tazzzo.app.data.local.PersistentStore
import com.tazzzo.app.data.repository.ServiceLocator
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class AddressModeAndPrivacyTest {
    private inline fun <T> inMode(mode: CatalogMode?, block: () -> T): T {
        val before = CatalogSource.debugOverride
        CatalogSource.debugOverride = mode
        try { return block() } finally { CatalogSource.debugOverride = before }
    }

    // ---- no mock address leak ---------------------------------------------------------------------------------

    @Test fun remoteModeRefusesTheMockAddressRepository() = runTest {
        inMode(null) {
            assertFailsWith<UnsupportedOperationException> { ServiceLocator.addresses.getAddresses() }
            assertFailsWith<UnsupportedOperationException> { ServiceLocator.addresses.addAddress("Home", "22, 14th Main, HSR", "", "560102") }
        }
    }

    @Test fun theMockAddressesExistOnlyInExplicitMockMode() = runTest {
        inMode(CatalogMode.MOCK) {
            val list = ServiceLocator.addresses.getAddresses()
            assertEquals(3, list.size)
            assertEquals("addr-1", list.first().id)
        }
    }

    @Test fun noHardCodedProductionAddressInRemoteMode() {
        inMode(null) { assertEquals("", TazzzoAppState(store = null).user.address) }
        inMode(CatalogMode.MOCK) { assertEquals("HSR Layout, Bengaluru", TazzzoAppState(store = null).user.address) }
    }

    // ---- persistence ------------------------------------------------------------------------------------------------

    @Test fun legacyAddressDataIsRemovedOnRestore() = runTest {
        val settings = MapSettings().apply {
            putString("tazzzo.addresses.v1", """[{"id":"a1","label":"Home","line1":"22, 14th Main","line2":"","pincode":"560102","isServiceable":true}]""")
            putString("tazzzo.session.v1", """{"name":"Asha","phone":"","isGuest":true,"coinBalance":40,"address":"HSR Layout, Bengaluru"}""")
        }
        val app = TazzzoAppState(store = PersistentStore(settings))
        app.restoreFromDisk()
        assertNull(settings.getStringOrNull("tazzzo.addresses.v1"), "the plain-settings address list must be gone")
        assertFalse("address" in settings.getString("tazzzo.session.v1", ""), "the saved session must no longer carry an address")
        assertTrue("Asha" in settings.getString("tazzzo.session.v1", ""))      // the rest of the session survives
        assertEquals("Asha", PersistentStore(settings).loadSession()!!.name)
    }

    @Test fun purgeIsIdempotentAndReportsWhatItRemoved() {
        val settings = MapSettings().apply { putString("tazzzo.addresses.v1", "[]") }
        val st = PersistentStore(settings)
        assertTrue(st.purgeLegacyAddressData()); assertFalse(st.purgeLegacyAddressData())
    }

    @Test fun theSavedSessionHasNoAddressField() {
        val settings = MapSettings(); val st = PersistentStore(settings)
        st.saveSession(PersistentStore.SavedSession("Asha", "", false, 5))
        assertFalse("address" in settings.getString("tazzzo.session.v1", ""))
        // and an old payload that still has one decodes (the field is simply ignored)
        settings.putString("tazzzo.session.v1", """{"name":"Old","phone":"","isGuest":true,"coinBalance":1,"address":"x"}""")
        assertEquals("Old", st.loadSession()!!.name)
    }

    @Test fun onlyThePinAndTheOpaqueSelectedIdEverReachPlainSettings() = runTest {
        val settings = MapSettings(); val store = PersistentStore(settings)
        val fake = FakeAddressSource().apply {
            server.add(ca("ADDR_home001", isDefault = true, postal = "560102", line2 = "Sector 6", landmark = "Near the park"))
        }
        val book = AddressBook(backgroundScope, fake) { true }
        val location = DeliveryLocation(backgroundScope, FakePins(), PersistentSelectionStore(store), book)
        book.load(); runCurrent()
        location.selectAddress(ca("ADDR_home001", postal = "560102"))
        book.create(input(name = "Rohan Mehta", phone = "9123456789", line1 = "7 Lake Road", city = "Pune", state = "Maharashtra", postal = "411001")); runCurrent()
        book.setDefault(ca("ADDR_home001")); runCurrent()

        // Snapshot WHILE addresses are live and one is selected (not after sign-out, when it would be trivially empty).
        val everything = settings.keys.joinToString { k -> "$k=${settings.getStringOrNull(k)}" }
        assertEquals("ADDR_home001", settings.getStringOrNull("tazzzo.selectedAddress.v1"))
        assertEquals(1, settings.keys.size, "expected exactly the selected-address id, got ${settings.keys}")
        for (secret in listOf("Asha", "Rohan", "9876543210", "9123456789", "14th Main", "Lake Road", "Sector 6", "park", "Bengaluru", "Pune", "Karnataka", "Maharashtra", "411001")) {
            assertFalse(secret in everything, "'$secret' reached plain settings: $everything")
        }
        location.onSignedOut(); runCurrent()
        assertTrue(settings.keys.isEmpty(), "sign-out leaves nothing: ${settings.keys}")
    }

    @Test fun theSelectedIdIsClearedOnLogout() = runTest {
        val settings = MapSettings(); val store = PersistentStore(settings)
        val book = AddressBook(backgroundScope, FakeAddressSource(), { true })
        val location = DeliveryLocation(backgroundScope, FakePins(), PersistentSelectionStore(store), book)
        location.selectAddress(ca("ADDR_home001", postal = "560102"))
        assertEquals("ADDR_home001", store.selectedAddressId)
        location.onSignedOut()
        assertNull(store.selectedAddressId); assertNull(settings.getStringOrNull("tazzzo.selectedAddress.v1"))
    }

    @Test fun aPersistedSelectionIsRestoredByTheNextProcess() = runTest {
        val settings = MapSettings()
        PersistentStore(settings).selectedAddressId = "ADDR_home001"
        val location = DeliveryLocation(backgroundScope, FakePins(), PersistentSelectionStore(PersistentStore(settings)), AddressBook(backgroundScope, FakeAddressSource(), { true }))
        assertNotNull(location.selectedAddressId.value)
    }
}
