package com.tazzzo.app.data.repository

import com.tazzzo.app.data.model.Category

/**
 * Process-lifetime cache for the category taxonomy.
 *
 * Four surfaces need the full category list — the home tab, the categories
 * tab, the product detail page (for its breadcrumb) and the category listing —
 * and each of them was fetching it independently. Against the in-memory mock
 * that only cost a fake delay; against `GET /catalog/v1/categories` it is four
 * round trips for a list that changes on the order of days.
 *
 * It also lets the category listing render its title and subcategory rail
 * immediately when the customer arrives from a screen that already loaded the
 * taxonomy, which is every path into it today. Without that, moving the
 * taxonomy off the in-memory mock would have introduced a blank-title flash on
 * a frozen screen.
 *
 * Deliberately NOT persisted: this is a request cache, not storage.
 * [invalidate] exists for the session boundary — call it on logout or when a
 * future push tells the app the catalogue changed.
 */
object Taxonomy {

    private var cache: List<Category>? = null

    /** Last loaded taxonomy, or null if it has never been loaded this process. */
    fun cached(): List<Category>? = cache

    /** Cached category, or null if the taxonomy is cold or the id is unknown. */
    fun cachedCategory(id: String): Category? = cache?.find { it.id == id }

    /**
     * Returns the taxonomy, loading it through the repository on first use.
     * Exceptions propagate: a failed load must reach the caller's error state,
     * never be swallowed into an empty list.
     */
    suspend fun categories(catalog: CatalogRepository = ServiceLocator.catalog): List<Category> =
        cache ?: catalog.getCategories().also { cache = it }

    /** Drops the cache. Safe to call at any time; the next read reloads. */
    fun invalidate() { cache = null }
}
