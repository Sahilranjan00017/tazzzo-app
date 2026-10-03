package com.tazzzo.app.ui.catalog

import com.tazzzo.app.data.catalog.CatalogCapabilities
import com.tazzzo.app.data.catalog.CatalogProduct
import com.tazzzo.app.data.catalog.StockTone
import com.tazzzo.app.data.catalog.discountPercentLabel
import com.tazzzo.app.data.catalog.mrpLabel
import com.tazzzo.app.data.catalog.priceLabel
import com.tazzzo.app.data.catalog.stockLabel

/*
 * The TRUTHFUL bindings behind Shop, category browsing, the product card and Search (UI-03). Pure functions, unit-tested:
 * what a card may say, how the browse path moves through a taxonomy of any depth, and what the Search surface is allowed
 * to be. Nothing here invents stock, pack sizes, discounts, counts, hierarchy or results.
 */

object ShopCopy {
    const val TITLE = "Shop"
    const val SUPPORT = "Browse the full catalogue."
    const val ALL_IN = "All"
    const val NO_CATEGORIES_TITLE = "Nothing to browse yet"
    const val NO_CATEGORIES_BODY = "The catalogue is being prepared. Check back soon."
    const val CATALOGUE_UNAVAILABLE_TITLE = "The catalogue isn't available right now"
    const val CATALOGUE_UNAVAILABLE_BODY = "Please try again in a moment."
    const val EMPTY_SECTION_TITLE = "Nothing here yet"
    const val EMPTY_SECTION_BODY = "There are no products in this section right now."
    const val BACK_TO_SHOP = "Browse the Shop"
    const val SEARCH_TITLE = "Search"
    const val SEARCH_UNAVAILABLE_TITLE = "Search isn't available yet"
    const val SEARCH_UNAVAILABLE_BODY = "We're building it. Until then, every product is a few taps away in the Shop."
    const val END_OF_LIST = "You've seen everything here"
    const val PRICE_UNAVAILABLE = "Price unavailable"
}

/**
 * The browse position inside one category screen: the node the screen opened on, then the ids chosen at each level
 * below it. The list being shown is the LAST id ([current]); every level whose children are known gets a chip row.
 * The depth is whatever the backend's hierarchy gives — nothing here assumes section → category → subcategory.
 */
data class BrowsePath(val rootId: String, val selected: List<String> = emptyList()) {
    val current: String get() = selected.lastOrNull() ?: rootId

    /** The nodes whose children rows are shown: the root, then each selected id in order. */
    val levels: List<String> get() = listOf(rootId) + selected

    /** Choosing [childId] at [level] (0 = the root's children) drops any deeper choices. */
    fun select(level: Int, childId: String): BrowsePath = copy(selected = selected.take(level) + childId)

    /** "All" at [level]: back to the node that owns that row. */
    fun selectAll(level: Int): BrowsePath = copy(selected = selected.take(level))

    fun isSelected(level: Int, childId: String): Boolean = selected.getOrNull(level) == childId
}

/** A chip row for one level: the owner node and its loaded children (empty = no row). */
data class ChipLevel(val level: Int, val ownerId: String, val children: List<com.tazzzo.app.data.catalog.CatalogNode>)

/** The facts the canonical product card renders. Everything optional is null when the backend did not supply it. */
data class ProductCardFacts(
    val name: String,
    val imageUrl: String?,
    val price: String?,
    val mrp: String?,
    val discount: String?,
    /** Only scarcity or unavailability is worth a line on a card; "In stock" and "unknown" are noise. */
    val stockNote: String?,
    val stockTone: StockTone?
)

fun CatalogProduct.cardFacts(): ProductCardFacts {
    val stock = stockLabel()
    val showStock = stock.tone == StockTone.Scarce || stock.tone == StockTone.Unavailable
    return ProductCardFacts(
        name = name,
        imageUrl = thumbnailUrl,
        price = priceLabel(),
        mrp = mrpLabel(),
        discount = discountPercentLabel(),
        stockNote = if (showStock) stock.text else null,
        stockTone = if (showStock) stock.tone else null
    )
}

/** What the Search route is allowed to be for the active catalogue. There is no third option. */
enum class SearchSurface { Unavailable, Available }

fun searchSurface(caps: CatalogCapabilities): SearchSurface =
    if (caps.search) SearchSurface.Available else SearchSurface.Unavailable

/** Grid cards adapt to the width: two columns from 320dp up, more on wide screens. */
const val PRODUCT_GRID_MIN_CELL_DP = 140
const val CATEGORY_GRID_MIN_CELL_DP = 96
