package com.tazzzo.app.home

import com.tazzzo.app.HomeTab
import com.tazzzo.app.RouteImpl
import com.tazzzo.app.Screen
import com.tazzzo.app.config.AppEnvironment
import com.tazzzo.app.config.BuildEnvironment
import com.tazzzo.app.data.catalog.CatalogCapabilities
import com.tazzzo.app.data.catalog.CatalogMode
import com.tazzzo.app.data.catalog.CatalogModeSelector
import com.tazzzo.app.data.order.OrderLaunchGate
import com.tazzzo.app.routeImpl
import com.tazzzo.app.ui.home.visibleHomeTabs
import com.tazzzo.app.ui.profile.ProfileEntry
import com.tazzzo.app.ui.profile.profileEntries
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

/**
 * The RELEASE configuration, proven through pure functions: a release build resolves to REMOTE + PROD, REMOTE places real
 * orders and has real history and search, and no destination renders a MOCK screen in REMOTE (App.kt routes through
 * [routeImpl], which is exhaustive over [Screen]).
 *
 * Mutation notes: classifying any REMOTE route as MOCK fails [noRemoteRouteRendersAMockScreen]; turning REMOTE ordering off
 * fails [releaseRemoteHasTheDayOneCapabilities]; showing Deals/Order again in REMOTE fails [remoteTabsAreTheFourRealOnes].
 */
class ReleaseConfigurationTest {
    /** One instance of every destination (the `when` in routeImpl is exhaustive, so a new Screen must be added here too). */
    private val everyScreen: List<Screen> = listOf(
        Screen.Splash, Screen.Onboarding, Screen.Login, Screen.Home, Screen.CategoryDetail("TZC-000001"), Screen.ProductDetail("TZP-1"),
        Screen.Search, Screen.Cart, Screen.Checkout, Screen.OrderSuccess("ORD_abc123"), Screen.Orders, Screen.Coins, Screen.Help,
        Screen.Addresses, Screen.AddressForm(), Screen.MasterList, Screen.About, Screen.Club, Screen.ClubCheckout,
        Screen.OrderDetail("ORD_abc123"), Screen.Voice, Screen.Legal("terms"), Screen.SupportCase("SUP_abcdefghijklmnopqrstu"), Screen.SupportNew()
    )

    @Test fun aReleaseBuildIsRemoteAndProduction() {
        for (o in listOf(null, CatalogMode.MOCK, CatalogMode.REMOTE)) assertEquals(CatalogMode.REMOTE, CatalogModeSelector.resolve(isDebug = false, debugOverride = o))
        assertEquals(BuildEnvironment.PROD, AppEnvironment.resolve(isDebug = false, override = BuildEnvironment.DEV))
    }

    @Test fun releaseRemoteHasTheDayOneCapabilities() {
        val c = CatalogCapabilities.REMOTE
        assertTrue(c.orderIntegration); assertTrue(OrderLaunchGate.enabled(c))
        assertTrue(c.orderHistoryIntegration); assertTrue(c.search); assertTrue(c.cartIntegration); assertTrue(c.checkoutIntegration)
        assertFalse(c.deals); assertFalse(c.reorder); assertFalse(c.bestsellers); assertFalse(c.banners)
    }

    @Test fun noRemoteRouteRendersAMockScreen() {
        for (s in everyScreen) assertNotEquals(RouteImpl.MOCK, routeImpl(s, CatalogMode.REMOTE), s.toString())
        // The real surfaces really are the REMOTE implementations, and MOCK keeps its demo screens.
        for (s in listOf(Screen.Search, Screen.Cart, Screen.Checkout, Screen.Orders, Screen.Help, Screen.About, Screen.OrderDetail("ORD_abc123")))
            assertEquals(RouteImpl.REMOTE, routeImpl(s, CatalogMode.REMOTE), s.toString())
        for (s in listOf(Screen.Coins, Screen.MasterList, Screen.Club, Screen.ClubCheckout, Screen.Voice)) {
            assertEquals(RouteImpl.UNAVAILABLE, routeImpl(s, CatalogMode.REMOTE), s.toString())
            assertEquals(RouteImpl.MOCK, routeImpl(s, CatalogMode.MOCK), s.toString())
        }
    }

    @Test fun remoteTabsAreTheFourRealOnes() {
        assertEquals(listOf(HomeTab.HOME, HomeTab.SHOP, HomeTab.ORDERS, HomeTab.PROFILE), visibleHomeTabs(CatalogCapabilities.REMOTE))
    }

    @Test fun theRemoteProfileHasNoComingSoonRows() {
        val titles = (profileEntries(true) + profileEntries(false)).map { it.title.lowercase() }
        for (w in listOf("coins", "genie", "voice", "club", "shopping list")) assertTrue(titles.none { w in it }, w)
        assertTrue(ProfileEntry.TERMS in profileEntries(false) && ProfileEntry.PRIVACY in profileEntries(false))   // legal works signed out
    }
}
