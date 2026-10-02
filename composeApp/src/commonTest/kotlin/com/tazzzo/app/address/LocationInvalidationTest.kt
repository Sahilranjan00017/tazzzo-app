package com.tazzzo.app.address

import com.tazzzo.app.catalog.cp
import com.tazzzo.app.catalog.detailOf
import com.tazzzo.app.data.address.AddressBook
import com.tazzzo.app.data.address.DeliveryLocation
import com.tazzzo.app.data.catalog.CatalogProduct
import com.tazzzo.app.data.catalog.LaunchContext
import com.tazzzo.app.data.catalog.Page
import com.tazzzo.app.data.catalog.PagedLoader
import com.tazzzo.app.data.catalog.PagedState
import com.tazzzo.app.data.catalog.PdpState
import com.tazzzo.app.data.catalog.PinStore
import com.tazzzo.app.data.catalog.Pincode
import com.tazzzo.app.data.catalog.ProductDetailHolder
import com.tazzzo.app.data.catalog.ProductListHolder
import com.tazzzo.app.data.catalog.ProductListKey
import com.tazzzo.app.data.catalog.ServiceabilityChecker
import com.tazzzo.app.data.catalog.ServiceabilityResult
import com.tazzzo.app.data.catalog.ServiceabilityState
import com.tazzzo.app.data.catalog.StockState
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertTrue

/**
 * Scenario: the app starts on PIN 560047, the customer logs in and picks a saved address in another PIN.
 * Nothing based on 560047 — stock, price, buyability, serviceability — may stay visible as current truth
 * while the 560102 request is running.
 */
class LocationInvalidationTest {
    private class Memory(var v: String? = null) : PinStore { override fun load() = v; override fun save(pin: String) { v = pin } }

    private class World(
        val launch: LaunchContext, val location: DeliveryLocation, val plp: ProductListHolder, val pdp: ProductDetailHolder,
        val plpGate: MutableMap<String, CompletableDeferred<Unit>>, val pdpGate: MutableMap<String, CompletableDeferred<Unit>>,
        val checks: MutableList<String>
    )

    private fun TestScope.world(): World {
        val checks = mutableListOf<String>()
        val serviceGate = HashMap<String, CompletableDeferred<Unit>>()
        val checker = ServiceabilityChecker { p -> checks += p.value; serviceGate[p.value]?.await(); ServiceabilityResult(true, "SA", 1, null, null) }
        val launch = LaunchContext(Memory(), checker, backgroundScope)
        val source = FakeAddressSource().apply { server.add(ca("ADDR_other001", postal = "560102")) }
        val book = AddressBook(backgroundScope, source) { true }
        val location = DeliveryLocation(backgroundScope, launch, MemorySelection(), book)

        val plpGate = HashMap<String, CompletableDeferred<Unit>>()
        val pager = PagedLoader<ProductListKey, CatalogProduct>(backgroundScope, { it.skuId }) { key, _ ->
            plpGate[key.pin!!.value]?.await()
            Page(listOf(cp("S-${key.pin!!.value}", price = com.tazzzo.app.data.model.Money.ofPaise(1_000), stock = if (key.pin!!.value == "560047") StockState.IN_STOCK else StockState.OUT_OF_STOCK, buyable = key.pin!!.value == "560047")), null, false)
        }
        val plp = ProductListHolder(backgroundScope, pager, launch.pin)
        val pdpGate = HashMap<String, CompletableDeferred<Unit>>()
        val pdp = ProductDetailHolder(backgroundScope, { id, p -> pdpGate[p!!.value]?.await(); detailOf(cp("S-${p.value}", id, buyable = p.value == "560047")) }, launch.pin)
        return World(launch, location, plp, pdp, plpGate, pdpGate, checks)
    }

    @Test fun selectingAnAddressInAnotherPinClearsThePlpImmediatelyThenReloadsForTheNewPin() = runTest {
        val w = world()
        w.plp.open("TZC-000010"); runCurrent()
        val before = assertIs<PagedState.Content<CatalogProduct>>(w.plp.state.value)
        assertTrue(before.items.single().buyable)                                  // buyable at 560047

        w.plpGate["560102"] = CompletableDeferred()                                  // hold the new-location answer back
        w.location.selectAddress(ca("ADDR_other001", postal = "560102")); runCurrent()

        assertEquals(PagedState.LoadingFirst, w.plp.state.value, "old-location products must not stay visible while the new ones load")
        w.plpGate.getValue("560102").complete(Unit); runCurrent()
        val after = assertIs<PagedState.Content<CatalogProduct>>(w.plp.state.value)
        assertEquals("S-560102", after.items.single().skuId); assertFalse(after.items.single().buyable)
    }

    @Test fun thePdpClearsStockPriceAndBuyabilityImmediately() = runTest {
        val w = world()
        w.pdp.open("TZP-1"); runCurrent()
        assertTrue(assertIs<PdpState.Content>(w.pdp.state.value).detail.product.buyable)

        w.pdpGate["560102"] = CompletableDeferred()
        w.location.selectAddress(ca("ADDR_other001", postal = "560102")); runCurrent()

        assertEquals(PdpState.Loading, w.pdp.state.value, "the old location's price / stock / buyability must be gone")
        w.pdpGate.getValue("560102").complete(Unit); runCurrent()
        assertFalse(assertIs<PdpState.Content>(w.pdp.state.value).detail.product.buyable)
    }

    @Test fun serviceabilityGoesToLoadingAndResolvesAgainstTheNewPin() = runTest {
        val w = world()
        w.launch.refresh(); runCurrent()
        assertIs<ServiceabilityState.Serviceable>(w.launch.state.value)
        w.location.selectAddress(ca("ADDR_other001", postal = "560102"))
        assertEquals(ServiceabilityState.Loading, w.launch.state.value)             // immediately, not after the answer
        runCurrent()
        assertEquals(listOf("560047", "560102"), w.checks)
        assertIs<ServiceabilityState.Serviceable>(w.launch.state.value)
    }

    @Test fun aSlowAnswerForTheOldLocationCanNeverOverwriteTheNewOne() = runTest {
        val w = world()
        w.plpGate["560047"] = CompletableDeferred()
        w.plp.open("TZC-000010"); runCurrent()                                       // 560047 request stuck
        w.location.selectAddress(ca("ADDR_other001", postal = "560102")); runCurrent()
        w.plpGate.getValue("560047").complete(Unit); runCurrent()                    // the stale answer lands afterwards
        val s = assertIs<PagedState.Content<CatalogProduct>>(w.plp.state.value)
        assertEquals("S-560102", s.items.single().skuId)
    }

    @Test fun aManualPinEditInvalidatesThePlpTheSameWay() = runTest {
        val w = world()
        w.plp.open("TZC-000010"); runCurrent()
        w.plpGate["110001"] = CompletableDeferred()
        assertTrue(w.location.setManualPin("110001")); runCurrent()
        assertEquals(PagedState.LoadingFirst, w.plp.state.value)
    }

    @Test fun theLaunchContextHoldsTheOnlyActivePin() = runTest {
        val w = world()
        w.location.selectAddress(ca("ADDR_other001", postal = "560102"))
        assertEquals("560102", w.launch.pin.value.value)
        w.location.setManualPin("110001")
        assertEquals("110001", w.launch.pin.value.value)
        w.location.onSignedOut()
        assertEquals(Pincode.LAUNCH_VALUE, w.launch.pin.value.value)
    }
}
