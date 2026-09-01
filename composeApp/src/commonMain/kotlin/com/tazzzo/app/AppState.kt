package com.tazzzo.app

import androidx.compose.runtime.*
import androidx.compose.ui.geometry.Rect
import com.tazzzo.app.data.model.*
import com.tazzzo.app.config.AppConfig
import com.tazzzo.app.data.repository.ServiceLocator

/** All destinations in the app. Simple back-stack navigation, no libraries. */
sealed interface Screen {
    data object Splash : Screen
    data object Onboarding : Screen
    data object Login : Screen
    data object Home : Screen                       // main scaffold w/ bottom tabs
    data class CategoryDetail(val categoryId: String, val subcategoryId: String? = null) : Screen
    data class ProductDetail(val productId: String) : Screen
    data object Search : Screen
    data object Cart : Screen
    data object Checkout : Screen
    data class OrderSuccess(val orderId: String) : Screen
    data object Orders : Screen
    data object Coins : Screen
    data object Help : Screen
    data object Addresses : Screen
    data object About : Screen
}

enum class HomeTab(val label: String, val emoji: String) {
    HOME("Home", "🏠"),
    CATEGORIES("Categories", "🗂️"),
    ORDER_AGAIN("Order Again", "🔄"),
    ACCOUNT("Account", "👤")
}

class TazzzoAppState(
    private val store: com.tazzzo.app.data.local.PersistentStore? =
        com.tazzzo.app.data.local.PersistentStore()
) {
    // --- navigation ---
    val backStack = mutableStateListOf<Screen>(Screen.Splash)
    val current: Screen get() = backStack.last()
    var homeTab by mutableStateOf(HomeTab.HOME)

    fun navigate(screen: Screen) { backStack.add(screen) }

    /**
     * One back behaviour for every trigger (top-bar arrow, Android system
     * back/gesture): checkout steps unwind first, then the nav stack, then
     * the bottom tabs return to Home before the app is allowed to exit.
     */
    fun handleSystemBack() {
        val active = checkout
        if (current is Screen.Checkout && active != null) {
            if (!active.backStep()) { checkout = null; back() }
            return
        }
        if (backStack.size > 1) { back(); return }
        if (homeTab != HomeTab.HOME) homeTab = HomeTab.HOME
    }

    val canHandleSystemBack: Boolean
        get() = backStack.size > 1 || current is Screen.Checkout || homeTab != HomeTab.HOME
    fun back() { if (backStack.size > 1) backStack.removeAt(backStack.lastIndex) }
    fun resetTo(screen: Screen) { backStack.clear(); backStack.add(screen) }

    // --- session ---
    private val _user = mutableStateOf(
        UserProfile(name = "Guest", phone = "", isGuest = true, coinBalance = 40,
            address = "HSR Layout, Bengaluru")
    )
    var user: UserProfile
        get() = _user.value
        set(value) {
            _user.value = value
            store?.saveSession(
                com.tazzzo.app.data.local.PersistentStore.SavedSession(
                    value.name, value.phone, value.isGuest, value.coinBalance, value.address
                )
            )
        }

    // --- search ----------------------------------------------------------
    /** Most-recent-first, deduplicated, capped. Session-only until persistence lands. */
    val recentSearches = mutableStateListOf<String>()
    fun recordSearch(query: String) {
        val q = query.trim()
        if (q.length < 2) return
        recentSearches.remove(q)
        recentSearches.add(0, q)
        while (recentSearches.size > 8) recentSearches.removeAt(recentSearches.lastIndex)
        store?.saveRecentSearches(recentSearches.toList())
        com.tazzzo.app.analytics.Analytics.track(
            com.tazzzo.app.analytics.AnalyticsEvents.SEARCH, mapOf("query" to q)
        )
    }

    /** Transient feedback line (e.g. "Only 3 left") shown briefly by MainScaffold. */
    var transientMessage by mutableStateOf<String?>(null)

    /** One-time restore notice shown on Home after a reconciled cart restore. */
    var restoreNotice by mutableStateOf<String?>(null)

    /** Guided tour requested only on the first ever skip/login. Also marks
     *  the user as onboarded so later launches route straight to Home. */
    fun requestGuidedTourIfFirstTime() {
        guidedJourneyPending = store?.tourSeen != true
        store?.onboarded = true
    }

    /** Returning customers skip the login wall; logout resets this. */
    val isOnboarded: Boolean get() = store?.onboarded == true

    fun markLoggedOut() {
        store?.onboarded = false
        user = UserProfile(name = "Guest", phone = "", isGuest = true,
            coinBalance = user.coinBalance, address = user.address)
    }

    fun markTourSeen() {
        guidedJourneyPending = false
        store?.tourSeen = true
    }

    /**
     * Restores persisted state. Cart lines are reconciled against the CURRENT
     * catalogue — current prices always win, and every deviation from what the
     * customer left behind is disclosed via [restoreNotice].
     */
    suspend fun restoreFromDisk() {
        val st = store ?: return
        st.loadSession()?.let {
            _user.value = UserProfile(it.name, it.phone, it.isGuest, it.coinBalance, it.address)
        }
        val searches = st.loadRecentSearches()
        if (recentSearches.isEmpty() && searches.isNotEmpty()) {
            recentSearches.addAll(searches)
        }
        val saved = st.loadCart()
        if (saved.isNotEmpty() && cartEntries.isEmpty()) {
            val products = saved.map { it.id }.distinct().associateWith {
                com.tazzzo.app.data.repository.ServiceLocator.catalog.getProduct(it)
            }
            val outcome = com.tazzzo.app.data.local.CartRestore.reconcile(saved) { products[it] }
            outcome.restored.forEach { (product, qty) ->
                cartEntries[product.id] = CartLine(product, qty)
            }
            persistCart()   // write back the reconciled truth
            restoreNotice = outcome.notice()
        }
    }

    // --- guided journey: screen-coords of elements the tour spotlights ---
    val guidedTargets = mutableStateMapOf<String, Rect>()

    // --- guided journey & voice ---
    var guidedJourneyPending by mutableStateOf(false)   // set when user skips login
    var showVoiceSheet by mutableStateOf(false)

    // --- cart -------------------------------------------------------------
    // The cart stores a full product SNAPSHOT taken at add time, not just an id.
    // Two reasons: (1) the UI must never reach into MockCatalog to resolve ids —
    // that couples screens to the mock data layer; (2) in production the price
    // shown in the cart must be the price the customer accepted, revalidated
    // explicitly at checkout rather than silently drifting.
    private val cartEntries = mutableStateMapOf<String, CartLine>()
    var lastOrder by mutableStateOf<Order?>(null)

    /** Active checkout, if any. Created on checkout entry, cleared on success/exit. */
    var checkout by mutableStateOf<CheckoutSession?>(null)

    fun quantityOf(product: Product): Int = cartEntries[product.id]?.quantity ?: 0

    /**
     * Adds one unit, refusing to exceed what stock and policy allow.
     * Returns false when the ceiling was hit so the UI can explain why.
     */
    fun addToCart(product: Product): Boolean {
        if (!product.isPurchasable) return false
        val current = cartEntries[product.id]?.quantity ?: 0
        if (current >= product.purchasableLimit) return false
        cartEntries[product.id] = CartLine(product, current + 1)
        persistCart()
        com.tazzzo.app.analytics.Analytics.track(
            com.tazzzo.app.analytics.AnalyticsEvents.ADD_TO_CART,
            mapOf("product_id" to product.id)
        )
        return true
    }

    fun removeFromCart(product: Product) {
        val q = (cartEntries[product.id]?.quantity ?: 0) - 1
        if (q <= 0) cartEntries.remove(product.id)
        else cartEntries[product.id] = CartLine(product, q)
        persistCart()
        com.tazzzo.app.analytics.Analytics.track(
            com.tazzzo.app.analytics.AnalyticsEvents.REMOVE_FROM_CART,
            mapOf("product_id" to product.id)
        )
    }

    /** Cart contents, newest-stable order. No catalogue needed. */
    fun cartLines(): List<CartLine> = cartEntries.values.sortedBy { it.product.name }

    val cartItemCount: Int get() = cartEntries.values.sumOf { it.quantity }

    /** Delegates to [com.tazzzo.app.config.BillCalculator] — the one place money maths lives. */
    fun bill(lines: List<CartLine>): BillSummary =
        com.tazzzo.app.config.BillCalculator.bill(lines)

    fun clearCart() { cartEntries.clear(); store?.clearCart() }

    private fun persistCart() {
        store?.saveCart(cartEntries.values.map {
            com.tazzzo.app.data.local.PersistentStore.SavedCartLine(
                it.product.id, it.quantity, it.product.price
            )
        })
    }
}

val LocalAppState = staticCompositionLocalOf<TazzzoAppState> { error("AppState not provided") }
