package com.tazzzo.app

import com.tazzzo.app.data.catalog.CatalogMode

/**
 * Which implementation a destination renders, decided in ONE place (App.kt routes by it). Exhaustive over [Screen], so a new
 * destination cannot be added without classifying it — and the release-configuration test asserts that REMOTE (every
 * release build) never renders a MOCK screen.
 */
enum class RouteImpl {
    /** The same screen in both modes (splash, onboarding, login, the tab shell, the address form, legal, support). */
    SHARED,
    /** The REMOTE implementation (real backend). */
    REMOTE,
    /** The MOCK demo implementation (debug builds with the mock catalogue only). */
    MOCK,
    /** A truthful "not available" page: the destination has no REMOTE implementation and no REMOTE caller. */
    UNAVAILABLE
}

fun routeImpl(screen: Screen, mode: CatalogMode): RouteImpl {
    val remote = mode == CatalogMode.REMOTE
    return when (screen) {
        Screen.Splash, Screen.Onboarding, Screen.Login, Screen.Home, is Screen.AddressForm,
        is Screen.Legal, is Screen.SupportCase, is Screen.SupportNew -> RouteImpl.SHARED
        is Screen.CategoryDetail, is Screen.ProductDetail, Screen.Search, Screen.Cart, Screen.Checkout, is Screen.OrderSuccess,
        Screen.Orders, Screen.Help, Screen.Addresses, Screen.About, is Screen.OrderDetail ->
            if (remote) RouteImpl.REMOTE else RouteImpl.MOCK
        // MOCK-only features (mock history, mock coins, Club, the voice demo): REMOTE has no caller and shows a truthful page.
        Screen.Coins, Screen.MasterList, Screen.Club, Screen.ClubCheckout, Screen.Voice ->
            if (remote) RouteImpl.UNAVAILABLE else RouteImpl.MOCK
    }
}
