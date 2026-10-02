package com.tazzzo.app.ui.home

import com.tazzzo.app.HomeTab
import com.tazzzo.app.data.catalog.CatalogCapabilities

/**
 * The bottom tabs a catalogue can actually support. In REMOTE mode there is no deals feed, and
 * "Order again" needs orders/cart that do not exist yet — so those tabs are not shown (rather than
 * shown over mock data). MOCK mode (debug/demo/tests) shows all five.
 */
internal fun visibleHomeTabs(caps: CatalogCapabilities): List<HomeTab> = HomeTab.entries.filter { tab ->
    when (tab) {
        HomeTab.DEALS -> caps.deals
        HomeTab.ORDER_AGAIN -> caps.cartIntegration
        else -> true
    }
}
