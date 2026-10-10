package com.tazzzo.app.ui.catalog

import com.tazzzo.app.data.catalog.CatalogCapabilities
import com.tazzzo.app.data.catalog.CatalogProduct
import com.tazzzo.app.data.catalog.PagedState
import com.tazzzo.app.data.catalog.SearchQueryCheck
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
    /** `GET /v1/search` returns PRODUCTS only, matched on product name and brand: the copy says exactly that. */
    const val SEARCH_PLACEHOLDER = "Search products by name or brand"
    const val SEARCH_FIELD_DESCRIPTION = "Search products"
    const val SEARCH_START_TITLE = "Find a product"
    const val SEARCH_START_BODY = "Type a product name or brand. Results show products only."
    const val RECENT_SEARCHES = "Recent searches"
    const val CLEAR_RECENT = "Clear"
    const val NO_RESULTS_TITLE = "No products found"
    const val NO_RESULTS_BODY = "Try a different word, or browse the Shop."
    const val QUERY_TOO_SHORT = "Type at least 2 letters or numbers."
    const val QUERY_TOO_LONG = "That's too long. Use up to 64 characters and shorter words."
    const val QUERY_TOO_MANY_WORDS = "Use up to 5 words."
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

/** The inline hint for a query that cannot be sent (null when it can, or when nothing is typed). */
fun SearchQueryCheck.hint(): String? = when (this) {
    is SearchQueryCheck.Valid, SearchQueryCheck.Blank -> null
    SearchQueryCheck.TooShort -> ShopCopy.QUERY_TOO_SHORT
    SearchQueryCheck.TooLong -> ShopCopy.QUERY_TOO_LONG
    SearchQueryCheck.TooManyWords -> ShopCopy.QUERY_TOO_MANY_WORDS
}

/** What the Search body shows, decided once from the query check and the results. */
sealed interface SearchBody {
    /** Nothing typed: recent searches (chips) when there are any, otherwise the start prompt. */
    data class Start(val recent: List<String>) : SearchBody
    /** A query that cannot be sent: only the inline hint. */
    data class Invalid(val hint: String) : SearchBody
    data object Loading : SearchBody
    data class Failed(val failure: com.tazzzo.app.data.catalog.CatalogFailure) : SearchBody
    data object NoResults : SearchBody
    data class Results(val state: PagedState.Content<CatalogProduct>) : SearchBody
}

fun searchBody(check: SearchQueryCheck, results: PagedState<CatalogProduct>, recent: List<String>): SearchBody = when (check) {
    SearchQueryCheck.Blank -> SearchBody.Start(recent)
    is SearchQueryCheck.Valid -> when (results) {
        PagedState.Idle, PagedState.LoadingFirst -> SearchBody.Loading      // Idle = the debounce has not fired yet
        is PagedState.FirstPageFailed -> SearchBody.Failed(results.failure)
        PagedState.Empty -> SearchBody.NoResults
        is PagedState.Content -> SearchBody.Results(results)
    }
    else -> SearchBody.Invalid(check.hint()!!)
}

/** Grid cards adapt to the width: two columns from 320dp up, more on wide screens. */
const val PRODUCT_GRID_MIN_CELL_DP = 140
const val CATEGORY_GRID_MIN_CELL_DP = 96
