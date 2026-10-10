package com.tazzzo.app.data.catalog

/**
 * Which catalogue a build talks to. Selection is explicit and the two NEVER mix:
 * there is no runtime fallback from one to the other — a remote 404, 429, 503,
 * empty result or network failure is shown as such, not papered over with mock products.
 */
enum class CatalogMode { REMOTE, MOCK }

object CatalogModeSelector {
    /**
     * Release builds are always [CatalogMode.REMOTE]. Debug builds are REMOTE
     * unless a developer/test explicitly asks for [CatalogMode.MOCK]; the override
     * is ignored outright when [isDebug] is false (same rule as `AppEnvironment`).
     */
    fun resolve(isDebug: Boolean, debugOverride: CatalogMode?): CatalogMode =
        if (isDebug) debugOverride ?: CatalogMode.REMOTE else CatalogMode.REMOTE

    /** Builds exactly ONE backend — never both, never one as a fallback for the other. */
    fun <T> choose(mode: CatalogMode, remote: () -> T, mock: () -> T): T =
        when (mode) { CatalogMode.REMOTE -> remote(); CatalogMode.MOCK -> mock() }
}

/** Debug-only explicit selection (hard no-op in release). Not consulted by any screen until PR-04C. */
object CatalogSource {
    var debugOverride: CatalogMode? = null
        set(value) {
            if (!com.tazzzo.app.config.AppEnvironment.isDebug) return
            field = value
        }

    val current: CatalogMode
        get() = CatalogModeSelector.resolve(com.tazzzo.app.config.AppEnvironment.isDebug, debugOverride)
}

/**
 * What the active catalogue can actually do. A screen hides or disables whatever
 * is false instead of faking it (PR-04C consumes this).
 */
data class CatalogCapabilities(
    val search: Boolean,
    val bestsellers: Boolean,
    val deals: Boolean,
    val banners: Boolean,
    val counts: Boolean,
    val sorting: Boolean,
    /**
     * Whether a product from this catalogue may enter a cart. REMOTE products go to the SERVER cart
     * only (never the local/mock cart); MOCK products go to the local demo cart.
     */
    val cartIntegration: Boolean,
    /**
     * Whether this catalogue's cart may proceed to CHECKOUT review. REMOTE: the real checkout quote (PR-07).
     * A server cart must never reach the mock checkout, whatever this says.
     */
    val checkoutIntegration: Boolean,
    /**
     * Whether REAL COD order placement is enabled (`POST /v1/customer/orders`). True for REMOTE since the product-closure
     * release. The quote's money preview is ADVISORY (the order computes its own authoritative money, which may differ): the
     * checkout shows it as the total for review ("Final amount is confirmed when you place your order") and the confirmation /
     * order detail show "Your total changed from ₹X to ₹Y" when the order differs. Quote replay, pending-order recovery and the
     * 409 mapping are unchanged. A real quote still has no path into the mock OrderPlacement, local orders, coin
     * credits or Club progress. Read through [com.tazzzo.app.data.order.OrderLaunchGate].
     */
    val orderIntegration: Boolean,
    /**
     * Whether REAL order history exists: `GET /v1/customer/orders` (cursor-paged) + `GET /v1/customer/orders/{id}`. True for
     * REMOTE: the Orders tab lists the customer's real orders. It does NOT expose "Order again" (see [reorder]).
     */
    val orderHistoryIntegration: Boolean,
    /**
     * Whether the "Order again" tab exists. It is built from the MOCK order history and there is no reorder contract, so it
     * stays MOCK-only even though REMOTE now has real history.
     */
    val reorder: Boolean
) {
    companion object {
        /** What the running backend provides: taxonomy, product lists, PDP, serviceability, cart, checkout, COD orders and history. */
        val REMOTE = CatalogCapabilities(
            search = false, bestsellers = false, deals = false, banners = false,
            counts = false, sorting = false, cartIntegration = true, checkoutIntegration = true, orderIntegration = true, orderHistoryIntegration = true,
            reorder = false
        )

        /** The in-memory demo/test catalogue. */
        val MOCK = CatalogCapabilities(
            search = true, bestsellers = true, deals = true, banners = true,
            counts = true, sorting = true, cartIntegration = true, checkoutIntegration = true, orderIntegration = true, orderHistoryIntegration = true,
            reorder = true
        )

        fun forMode(mode: CatalogMode) = when (mode) { CatalogMode.REMOTE -> REMOTE; CatalogMode.MOCK -> MOCK }
    }
}
