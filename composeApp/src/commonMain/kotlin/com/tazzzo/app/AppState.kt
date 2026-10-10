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
    /** Add ([addressId] null) or edit one saved address. REMOTE mode only. */
    data class AddressForm(val addressId: String? = null) : Screen
    /** Your regulars, built from real order history, with one tap to add them all. */
    data object MasterList : Screen
    data object About : Screen
    /** Tazzzo Club landing — the value proposition, before any payment. */
    data object Club : Screen
    /** Confirm + pay. Separate destination so back returns to the landing page. */
    data object ClubCheckout : Screen
    /** Permanent receipt + service centre for one order. */
    data class OrderDetail(val orderId: String) : Screen
    /** Tazzzo Genie: the voice-ordering surface (truthful "coming soon" until a real capability exists). MOCK-only entry. */
    data object Voice : Screen
    /** A legal document (`terms` | `privacy`), rendered as plain text from `GET /v1/content/legal/{slug}`. Works signed out. */
    data class Legal(val slug: String) : Screen
    /** One of the customer's support requests: its message thread and the reply box. */
    data class SupportCase(val caseId: String) : Screen
    /** "Contact us": a new support request, optionally about one order. */
    data class SupportNew(val orderId: String? = null) : Screen
}

/**
 * Stable identity for a destination, used to key retained UI state (scroll
 * position, query, filters) across navigation.
 *
 * Deliberately NOT the back-stack index: an index shifts when the stack is
 * popped, which would throw away exactly the state we are trying to keep. Two
 * identical destinations in one stack share a key, and therefore share scroll
 * position — the correct behaviour for this app, where that means the same
 * product or the same aisle.
 */
val Screen.stateKey: String
    get() = when (this) {
        is Screen.Splash -> "splash"
        is Screen.Onboarding -> "onboarding"
        is Screen.Login -> "login"
        is Screen.Home -> "home"
        is Screen.CategoryDetail -> "category:$categoryId:${subcategoryId ?: ""}"
        is Screen.ProductDetail -> "product:$productId"
        is Screen.Search -> "search"
        is Screen.Cart -> "cart"
        is Screen.Checkout -> "checkout"
        is Screen.OrderSuccess -> "orderSuccess:$orderId"
        is Screen.Orders -> "orders"
        is Screen.Coins -> "coins"
        is Screen.Help -> "help"
        is Screen.Addresses -> "addresses"
        is Screen.AddressForm -> "addressForm:${addressId ?: "new"}"
        is Screen.MasterList -> "masterList"
        is Screen.About -> "about"
        is Screen.Club -> "club"
        is Screen.ClubCheckout -> "clubCheckout"
        is Screen.OrderDetail -> "order:$orderId"
        is Screen.Voice -> "voice"
        is Screen.Legal -> "legal:$slug"
        is Screen.SupportCase -> "support:$caseId"
        is Screen.SupportNew -> "supportNew:${orderId ?: ""}"
    }

/** Which way the customer is travelling. Drives the transition, nothing else. */
enum class NavDirection { Forward, Backward, Replace }

/** The primary tabs (UI Page `Home.jpeg`): Home · Shop · Orders · Profile. DEALS and ORDER_AGAIN exist only for the MOCK catalogue. */
enum class HomeTab(val label: String) {
    HOME("Home"),
    SHOP("Shop"),
    DEALS("Deals"),
    /** Real surface: the customer's order history (`GET /v1/customer/orders`). Never mock history. */
    ORDERS("Orders"),
    ORDER_AGAIN("Order Again"),
    PROFILE("Profile")
}

/**
 * The mock demo address line shown by the MOCK Home header. A real (REMOTE) session has no such default:
 * customer addresses come from the backend, never from a hard-coded string.
 */
internal fun defaultAddressText(): String =
    if (ServiceLocator.catalogMode == com.tazzzo.app.data.catalog.CatalogMode.MOCK) "HSR Layout, Bengaluru" else ""

class TazzzoAppState(
    private val store: com.tazzzo.app.data.local.PersistentStore? =
        com.tazzzo.app.data.local.PersistentStore()
) {
    // --- navigation ---
    val backStack = mutableStateListOf<Screen>(Screen.Splash)
    val current: Screen get() = backStack.last()
    var homeTab by mutableStateOf(HomeTab.HOME)

    /** Set by every stack mutation so the host can pick the right transition. */
    var navDirection by mutableStateOf(NavDirection.Forward)
        private set

    /**
     * Pushes a destination.
     *
     * Ignores a push of the destination already on top. That single guard is
     * what stops a double-tapped product card from stacking two identical PDPs
     * and forcing the customer to press back twice — the classic "broken back
     * stack" bug. Genuine re-entry (Home → PDP → Home → same PDP) still works,
     * because the top of the stack differs by then.
     */
    fun navigate(screen: Screen) {
        // A banner's query is only for the Search it opened: any other navigation means it was not consumed — drop it.
        if (screen != Screen.Search) searchPrefill = null
        if (backStack.lastOrNull() == screen) return
        navDirection = NavDirection.Forward
        backStack.add(screen)
    }

    /**
     * One back behaviour for every trigger (top-bar arrow, Android system
     * back/gesture): checkout steps unwind first, then the nav stack, then
     * the bottom tabs return to Home before the app is allowed to exit.
     */
    fun handleSystemBack() {
        // Hierarchy, outermost first. An overlay must always swallow the first
        // back press: dismissing what is on top of the screen is what the
        // customer means, and popping the screen underneath it instead is the
        // single most disorienting thing back navigation can do.
        if (showVoiceSheet) { showVoiceSheet = false; return }
        if (guidedJourneyPending) { markTourSeen(); return }

        val active = checkout
        if (current is Screen.Checkout && active != null) {
            if (!active.backStep()) { checkout = null; back() }
            return
        }
        if (backStack.size > 1) { back(); return }
        if (homeTab != HomeTab.HOME) homeTab = HomeTab.HOME
    }

    val canHandleSystemBack: Boolean
        get() = showVoiceSheet || guidedJourneyPending ||
            backStack.size > 1 || current is Screen.Checkout || homeTab != HomeTab.HOME
    fun back() {
        searchPrefill = null
        if (backStack.size > 1) {
            navDirection = NavDirection.Backward
            backStack.removeAt(backStack.lastIndex)
        }
    }

    fun resetTo(screen: Screen) {
        searchPrefill = null
        navDirection = NavDirection.Replace
        backStack.clear()
        backStack.add(screen)
    }

    /**
     * "Start shopping" / "Continue shopping": the Home FEED, every time.
     *
     * Found by the on-device journey test (F6): a customer who reached Tazzzo
     * Club via the Account tab, paid, and tapped "Start shopping" was dropped
     * back on the Account tab — `resetTo(Screen.Home)` resets the back stack
     * but the shell's selected tab is separate state, and it was still
     * ACCOUNT. A button that says "shopping" must land on shopping.
     */
    fun goHome() {
        homeTab = HomeTab.HOME
        resetTo(Screen.Home)
    }

    // --- session ---
    private val _user = mutableStateOf(
        UserProfile(name = "Guest", phone = "", isGuest = true, coinBalance = 40, address = defaultAddressText())
    )
    /**
     * Whether a secure auth session exists. THIS, not `UserProfile.isGuest`, is
     * the session authority; `user.isGuest` is kept in step with it.
     */
    var isAuthenticated by mutableStateOf(false)
        private set

    /** Called with the secure session's state at start-up and whenever it changes. */
    fun applyAuthState(authenticated: Boolean) {
        if (isAuthenticated && !authenticated) { clearRecentSearches(); dropSearch() }   // session ended: next person must not see them
        isAuthenticated = authenticated
        _user.value = _user.value.copy(isGuest = !authenticated)
    }

    /**
     * A sign-in just completed. No profile is fetched in this slice, so the
     * profile stays neutral: no invented name, and the phone is NOT stored.
     */
    fun onSignedIn() {
        applyAuthState(true)
        user = user.copy(name = "", phone = "", isGuest = false)
    }

    /** Server revoke (best effort) + unconditional local sign-out. */
    suspend fun logout(auth: com.tazzzo.app.data.auth.AuthRepository = ServiceLocator.auth) {
        try {
            auth.logout()
        } finally {
            markLoggedOut()
        }
    }

    var user: UserProfile
        get() = _user.value
        set(value) {
            _user.value = value
            store?.saveSession(
                com.tazzzo.app.data.local.PersistentStore.SavedSession(
                    value.name, value.phone, value.isGuest, value.coinBalance
                )
            )
        }

    // --- search ----------------------------------------------------------
    /**
     * A query the Search screen should run when it is shown (a `search:` banner). Consumed (set back to null) by the screen.
     * Only a query that passes [com.tazzzo.app.data.catalog.SearchQueryRules] is ever set.
     */
    var searchPrefill by mutableStateOf<String?>(null)

    /**
     * The Search screen's holder, kept ABOVE the screen so the query, results and paging survive opening a result and coming
     * back. Created by the screen; closed and dropped when Search leaves the back stack or the session ends.
     */
    var search: com.tazzzo.app.data.catalog.ProductSearch? = null
        internal set

    internal fun attachSearch(holder: com.tazzzo.app.data.catalog.ProductSearch) { search?.close(); search = holder }

    /** Search is no longer on the back stack (or the session ended): forget its query and results. */
    fun dropSearch() { search?.close(); search = null }

    /** Opens Search; with [query], prefilled and run at once. An unsendable query opens an empty Search. */
    fun openSearch(query: String? = null) {
        searchPrefill = query?.let { com.tazzzo.app.data.catalog.SearchQueryRules.check(it) as? com.tazzzo.app.data.catalog.SearchQueryCheck.Valid }?.text
        navigate(Screen.Search)
    }

    /** Most-recent-first, deduplicated, capped; persisted per device and cleared on logout. */
    val recentSearches = mutableStateListOf<String>()
    fun clearRecentSearches() {
        recentSearches.clear()
        store?.clearRecentSearches()
    }
    fun recordSearch(query: String) {
        val q = query.trim()
        if (q.length < 2) return
        recentSearches.removeAll { it.equals(q, ignoreCase = true) }   // one entry per query, whatever the casing; the latest wins
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
        // The tour spotlights widgets of the (mock) Home feed; the REMOTE Home does not have them.
        guidedJourneyPending = store?.tourSeen != true && ServiceLocator.catalogMode == com.tazzzo.app.data.catalog.CatalogMode.MOCK
        store?.onboarded = true
    }

    /**
     * Notification preference. Reading is cheap and observable; writing also
     * persists, so the switch survives a relaunch.
     *
     * One property rather than a val plus a setter function: the pair compiled
     * to two JVM methods with the same signature and broke the Android build.
     */
    private var notificationsState by mutableStateOf(store?.notificationsEnabled ?: true)
    var notificationsEnabled: Boolean
        get() = notificationsState
        set(value) {
            notificationsState = value
            store?.notificationsEnabled = value
        }

    /**
     * "No carry bag needed", chosen in the cart.
     *
     * Lives here rather than on [CheckoutSession] because the cart is open long
     * before a session exists; the session seeds itself from this value so the
     * choice is not silently dropped between the two screens.
     *
     * It is a packing preference and nothing more. The mockup pairs it with
     * "earn 5 Eco Karma points"; no such ledger exists, and inventing a reward
     * to sell a choice the customer was already making is the kind of claim
     * D5 exists to stop.
     */
    var noCarryBag by mutableStateOf(false)

    /** Returning customers skip the login wall; logout resets this. */
    val isOnboarded: Boolean get() = store?.onboarded == true

    /** Dev-only entry behind the `taz_start_home` launch flag: mark the device
     *  onboarded AND route to Home in one step, so no later routing effect
     *  (Splash → Onboarding for a new device) can bounce a demo launch back onto
     *  the login wall. Ships nowhere; test classes clear the store first. */
    fun enterDemoHome() { store?.onboarded = true; resetTo(Screen.Home) }

    fun markLoggedOut() {
        isAuthenticated = false
        clearRecentSearches()
        dropSearch()
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
        st.purgeLegacyAddressData()      // earlier builds persisted addresses in plain settings
        st.loadSession()?.let {
            // The secure session decides guest vs signed in, not what was saved.
            _user.value = UserProfile(it.name, it.phone, !isAuthenticated, it.coinBalance, defaultAddressText())
        }
        st.loadMembership()?.let { membership = it }
        val searches = st.loadRecentSearches()
        if (recentSearches.isEmpty() && searches.isNotEmpty()) {
            recentSearches.addAll(searches)
        }
        // A pre-paise cart (rupees, mock product ids) is dropped, never reinterpreted — tell the customer once.
        val obsoleteCart = st.discardObsoleteCart()
        val saved = st.loadCart()
        if (obsoleteCart && saved.isEmpty() && cartEntries.isEmpty()) {
            restoreNotice = "Your saved basket was from an earlier version of the app and couldn't be restored."
        }
        // The local cart is a MOCK-catalogue concept. In REMOTE mode the cart lives on the server, and restoring
        // saved lines would look them up in the MOCK catalogue — the real/mock mixture this mode forbids.
        val cartRestorable = ServiceLocator.catalogMode == com.tazzzo.app.data.catalog.CatalogMode.MOCK
        // A local cart saved by an older build must not survive into REMOTE mode: purge it, never migrate it.
        if (!cartRestorable && saved.isNotEmpty()) { st.clearCart(); restoreNotice = null }
        if (cartRestorable && saved.isNotEmpty() && cartEntries.isEmpty()) {
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

    // --- Tazzzo Club --------------------------------------------------------

    /**
     * Local mirror of the customer's club standing. Authoritative only until
     * a backend exists — see BLOCKERS.md, same caveat as coin balances.
     */
    var membership by mutableStateOf(com.tazzzo.app.data.model.MembershipState())

    val isClubMember: Boolean get() = membership.isActive

    /** Club eligibility for the CURRENT cart. One source, used by cart, checkout and CTAs. */
    fun clubEligibility(lines: List<CartLine> = cartLines()) =
        com.tazzzo.app.config.MembershipCalculator.evaluate(
            itemTotal = lines.sumOfMoney { it.lineTotal },
            isMember = isClubMember,
            plan = com.tazzzo.app.config.MembershipConfig.plan,
            cumulativeSpend = membership.cumulativeSpend
        )

    /**
     * Coupon the customer typed. Cart context, not transient UI, so it lives
     * here rather than in a screen's `remember` — leaving the cart and coming
     * back must not silently drop a code the customer entered.
     */
    var couponCode by mutableStateOf<String?>(null)

    /** Set when Help is opened FROM an order, so support is order-scoped. */
    var helpOrderId by mutableStateOf<String?>(null)

    /** Delegates to [com.tazzzo.app.config.BillCalculator] — the one place money maths lives. */
    fun bill(lines: List<CartLine>): BillSummary =
        com.tazzzo.app.config.BillCalculator.bill(
            lines = lines,
            isClubMember = isClubMember,
            clubCumulativeSpend = membership.cumulativeSpend,
            couponCode = couponCode,
            slot = checkout?.slot,
            // Zero until a session exists, so the cart's bill and the checkout
            // bill agree until the customer actually chooses a tip.
            tip = checkout?.tip ?: Money.ZERO
        )

    fun clearCart() { cartEntries.clear(); store?.clearCart() }

    private fun persistCart() {
        store?.saveCart(cartEntries.values.map {
            com.tazzzo.app.data.local.PersistentStore.SavedCartLine(
                it.product.id, it.quantity, it.product.price.paise
            )
        })
    }
}

val LocalAppState = staticCompositionLocalOf<TazzzoAppState> { error("AppState not provided") }
