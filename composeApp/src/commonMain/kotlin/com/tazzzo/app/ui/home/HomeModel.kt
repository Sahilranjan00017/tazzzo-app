package com.tazzzo.app.ui.home

import com.tazzzo.app.data.address.CustomerAddress
import com.tazzzo.app.data.cart.CartState
import com.tazzzo.app.data.catalog.CatalogNode
import com.tazzzo.app.data.catalog.Pincode
import com.tazzzo.app.data.catalog.ServiceabilityState
import org.jetbrains.compose.resources.DrawableResource
import tazzzo.resources.Res
import tazzzo.resources.cat_home_dairy
import tazzzo.resources.cat_home_fresh
import tazzzo.resources.cat_home_meat_eggs
import tazzzo.resources.cat_home_snacks
import tazzzo.resources.cat_home_staples

/*
 * The Home screen's TRUTHFUL bindings (UI Page reference `Home.jpeg`). Pure functions so the rules are unit-tested:
 * what the location header says, whether a delivery status line exists, what the cart badge shows, which category gets
 * which artwork. Nothing here invents data the backend does not provide (no slots, no recommendations, no pack sizes).
 */

object HomeCopy {
    const val SEARCH_PLACEHOLDER = "Search for groceries, staples, and more..."
    const val HERO_EYEBROW = "GOOD FOOD, EVERYDAY"
    const val HERO_CTA = "Shop now"
    const val QUALITY_EYEBROW = "EVERYDAY ESSENTIALS"
    const val QUALITY_CTA = "Shop essentials"
    /** NOT "Fresh picks for you": the rail is a deterministic catalogue list, not a recommendation, so it never claims to be. */
    const val RAIL_TITLE = "Explore essentials"
    const val RAIL_SEE_ALL = "See all"
    const val BULK_EYEBROW = "BULK SAVINGS"
    const val BULK_CTA = "Shop in bulk"
    const val ORDERS_UNAVAILABLE_TITLE = "Order history isn't available yet"
    const val ORDERS_UNAVAILABLE_BODY = "Your orders will appear here once history is ready."
    const val CONTINUE_SHOPPING = "Continue shopping"
}

/**
 * The locality line of the header. The reference shows "Ejipura": we show the selected address's area line when it has
 * one, else its city, else the delivery PIN. Never a hard-coded place name.
 */
fun homeLocationLabel(selected: CustomerAddress?, pin: Pincode): String {
    val area = selected?.addressLine2?.trim()?.takeIf { it.isNotEmpty() }
    val city = selected?.city?.trim()?.takeIf { it.isNotEmpty() }
    return area ?: city ?: pin.value
}

/**
 * The line under the locality. The reference prints "Next slot in 40 mins"; there is no slot backend, so that is never
 * shown. What IS known is serviceability for the current PIN, so that is what the line says — or nothing (null) when
 * nothing truthful can be said; the layout keeps the line's space either way.
 */
fun deliveryStatusLine(state: ServiceabilityState, pin: Pincode): String? = when (state) {
    is ServiceabilityState.Serviceable -> "Delivering to ${pin.value}"
    is ServiceabilityState.NotServiceable -> "Not delivering to ${pin.value} yet"
    ServiceabilityState.Loading -> "Checking delivery…"
    ServiceabilityState.Unknown, is ServiceabilityState.Failed -> null
}

/** The cart badge: the SERVER cart's item count, or none. Never a local guess. */
fun cartBadgeCount(state: CartState): Int = (state as? CartState.Loaded)?.cart?.itemCount?.coerceAtLeast(0) ?: 0

/**
 * Reference-style circular artwork for a REAL taxonomy node, chosen by name; null = no approved art for this name
 * (the row then shows the neutral category visual). Names are the backend's; nothing is renamed to fit the artwork.
 */
fun homeCategoryArt(node: CatalogNode): DrawableResource? {
    val n = node.name.lowercase()
    return when {
        listOf("staple", "atta", "rice", "dal", "flour", "grain", "pulse").any { it in n } -> Res.drawable.cat_home_staples
        listOf("fresh", "fruit", "vegetable", "veggie", "produce").any { it in n } -> Res.drawable.cat_home_fresh
        listOf("meat", "egg", "chicken", "fish", "poultry", "non-veg", "nonveg").any { it in n } -> Res.drawable.cat_home_meat_eggs
        listOf("dairy", "milk", "paneer", "curd", "bread").any { it in n } -> Res.drawable.cat_home_dairy
        listOf("snack", "munch", "chip", "namkeen", "biscuit").any { it in n } -> Res.drawable.cat_home_snacks
        else -> null
    }
}

/** How many root categories the Home row shows (the reference shows five). */
const val HOME_CATEGORY_COUNT = 5
