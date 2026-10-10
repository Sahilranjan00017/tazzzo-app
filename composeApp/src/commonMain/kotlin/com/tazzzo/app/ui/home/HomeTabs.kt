package com.tazzzo.app.ui.home

import com.tazzzo.app.HomeTab
import com.tazzzo.app.data.catalog.CatalogCapabilities

/**
 * The bottom tabs a catalogue can actually support. In REMOTE mode there is no deals feed, and
 * "Order again" has no reorder contract (it is built from mock history) — so those tabs are not shown
 * (rather than shown over mock data). MOCK mode (debug/demo/tests) shows all six.
 */
internal fun visibleHomeTabs(caps: CatalogCapabilities): List<HomeTab> = HomeTab.entries.filter { tab ->
    when (tab) {
        HomeTab.DEALS -> caps.deals
        HomeTab.ORDER_AGAIN -> caps.reorder
        else -> true                       // HOME, SHOP, ORDERS (real history in REMOTE), PROFILE are always present
    }
}
