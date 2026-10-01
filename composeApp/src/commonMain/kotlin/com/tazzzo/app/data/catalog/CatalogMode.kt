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
     * Whether a product from this catalogue may enter a cart. False for REMOTE
     * until the real server cart lands: a real product must never reach the
     * local/mock cart and fake checkout.
     */
    val cartIntegration: Boolean
) {
    companion object {
        /** What the running backend provides today: taxonomy, product lists, PDP, serviceability — and nothing else. */
        val REMOTE = CatalogCapabilities(
            search = false, bestsellers = false, deals = false, banners = false,
            counts = false, sorting = false, cartIntegration = false
        )

        /** The in-memory demo/test catalogue. */
        val MOCK = CatalogCapabilities(
            search = true, bestsellers = true, deals = true, banners = true,
            counts = true, sorting = true, cartIntegration = true
        )

        fun forMode(mode: CatalogMode) = when (mode) { CatalogMode.REMOTE -> REMOTE; CatalogMode.MOCK -> MOCK }
    }
}
