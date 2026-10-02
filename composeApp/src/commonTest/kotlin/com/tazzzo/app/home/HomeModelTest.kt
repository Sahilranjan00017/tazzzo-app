package com.tazzzo.app.home

import com.tazzzo.app.HomeTab
import com.tazzzo.app.address.ca
import com.tazzzo.app.cart.cartOf
import com.tazzzo.app.cart.line
import com.tazzzo.app.data.cart.CartFailure
import com.tazzzo.app.data.cart.CartState
import com.tazzzo.app.data.catalog.CatalogCapabilities
import com.tazzzo.app.data.catalog.CatalogFailure
import com.tazzzo.app.data.catalog.CatalogNode
import com.tazzzo.app.data.catalog.Pincode
import com.tazzzo.app.data.catalog.ServiceabilityState
import com.tazzzo.app.ui.home.HomeCopy
import com.tazzzo.app.ui.home.cartBadgeCount
import com.tazzzo.app.ui.home.deliveryStatusLine
import com.tazzzo.app.ui.home.homeCategoryArt
import com.tazzzo.app.ui.home.homeLocationLabel
import com.tazzzo.app.ui.home.visibleHomeTabs
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** UI-02 Home: the truthful bindings behind the reference layout. */
class HomeModelTest {
    private val pin = Pincode.parse("560047")!!

    @Test fun theLocationLineIsTheRealSelectedAddressAreaThenCityThenPin() {
        assertEquals("Ejipura", homeLocationLabel(ca(id = "ADDR_1").copy(addressLine2 = "Ejipura", city = "Bengaluru"), pin))
        assertEquals("Bengaluru", homeLocationLabel(ca(id = "ADDR_1").copy(addressLine2 = null, city = "Bengaluru"), pin))
        assertEquals("560047", homeLocationLabel(null, pin))
    }

    @Test fun noDeliverySlotIsEverFabricated() {
        for (s in listOf(ServiceabilityState.Unknown, ServiceabilityState.Loading, ServiceabilityState.Failed(CatalogFailure.Network))) {
            val line = deliveryStatusLine(s, pin).orEmpty().lowercase()
            assertFalse("slot" in line); assertFalse(" min" in line)
        }
        assertNull(deliveryStatusLine(ServiceabilityState.Unknown, pin))
    }

    @Test fun theCartBadgeIsTheServerCartItemCountOrNothing() {
        assertEquals(3, cartBadgeCount(CartState.Loaded(cartOf(5, line("TZP-1", 2, unit = 50), line("TZP-2", 1, unit = 20)))))
        assertEquals(0, cartBadgeCount(CartState.Idle)); assertEquals(0, cartBadgeCount(CartState.SignedOut))
        assertEquals(0, cartBadgeCount(CartState.Failed(CartFailure.Network)))
    }

    @Test fun theRailIsNeverPresentedAsAPersonalRecommendation() {
        assertFalse("for you" in HomeCopy.RAIL_TITLE.lowercase()); assertFalse("pick" in HomeCopy.RAIL_TITLE.lowercase())
        assertEquals("Explore essentials", HomeCopy.RAIL_TITLE)
    }

    @Test fun categoryArtIsChosenByRealNameAndFallsBackToNeutral() {
        assertNotNull(homeCategoryArt(CatalogNode("n1", "Staples & Grains")))
        assertNotNull(homeCategoryArt(CatalogNode("n2", "Fresh Vegetables")))
        assertNotNull(homeCategoryArt(CatalogNode("n3", "Meat & Eggs")))
        assertNotNull(homeCategoryArt(CatalogNode("n4", "Dairy")))
        assertNotNull(homeCategoryArt(CatalogNode("n5", "Snacks")))
        assertNull(homeCategoryArt(CatalogNode("n6", "Pooja Needs")))
    }

    @Test fun bannerCopyMakesNoUnbackedPromise() {
        for (t in listOf(HomeCopy.HERO_EYEBROW, HomeCopy.QUALITY_EYEBROW, HomeCopy.BULK_EYEBROW, HomeCopy.HERO_CTA, HomeCopy.QUALITY_CTA, HomeCopy.BULK_CTA))
            for (w in listOf("%", "free delivery", "minutes", "guaranteed", "organic", "farm")) assertFalse(w in t.lowercase(), "'$w' in '$t'")
    }

    @Test fun theBottomNavIsHomeShopOrdersProfileInProductionAndOrdersStaysTruthful() {
        assertEquals(listOf(HomeTab.HOME, HomeTab.SHOP, HomeTab.ORDERS, HomeTab.PROFILE), visibleHomeTabs(CatalogCapabilities.REMOTE))
        assertEquals(listOf("Home", "Shop", "Orders", "Profile"), visibleHomeTabs(CatalogCapabilities.REMOTE).map { it.label })
        assertFalse(CatalogCapabilities.REMOTE.orderHistoryIntegration)                 // so ORDERS can only show the honest state
        assertTrue("isn't available yet" in HomeCopy.ORDERS_UNAVAILABLE_TITLE)
        assertFalse(CatalogCapabilities.REMOTE.orderIntegration)
    }
}
