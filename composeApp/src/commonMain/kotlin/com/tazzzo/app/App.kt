package com.tazzzo.app

import androidx.compose.animation.AnimatedContent
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import com.tazzzo.app.ui.interaction.tazNavTransition
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import com.tazzzo.app.data.repository.ServiceLocator
import com.tazzzo.app.config.AppEnvironment
import com.tazzzo.app.data.catalog.CatalogMode
import com.tazzzo.app.data.catalog.CatalogSource
import com.tazzzo.app.ui.catalog.RemoteCategoryScreen
import com.tazzzo.app.ui.address.RemoteAddressFormScreen
import com.tazzzo.app.ui.address.RemoteAddressesScreen
import com.tazzzo.app.ui.catalog.RemoteProductDetailScreen
import com.tazzzo.app.ui.catalog.UnavailableSurface
import com.tazzzo.app.theme.TazzzoTheme
import com.tazzzo.app.theme.TazColors
import com.tazzzo.app.ui.home.MainScaffold
import com.tazzzo.app.ui.home.CategoryDetailScreen
import com.tazzzo.app.ui.home.ProductDetailScreen
import com.tazzzo.app.ui.home.SearchScreen
import com.tazzzo.app.ui.home.CartScreen
import com.tazzzo.app.ui.checkout.CheckoutScreen
import com.tazzzo.app.ui.home.OrderSuccessScreen
import com.tazzzo.app.ui.home.OrdersScreen
import com.tazzzo.app.ui.home.CoinsScreen
import com.tazzzo.app.ui.home.HelpScreen
import com.tazzzo.app.ui.home.AddressesScreen
import com.tazzzo.app.ui.home.OrderDetailScreen
import com.tazzzo.app.ui.home.AboutScreen
import com.tazzzo.app.ui.club.ClubScreen
import com.tazzzo.app.ui.club.ClubCheckoutScreen
import com.tazzzo.app.ui.onboarding.LoginScreen
import com.tazzzo.app.ui.onboarding.ShowcaseScreen
import com.tazzzo.app.ui.splash.SplashScreen
import com.tazzzo.app.ui.home.MasterListScreen
import com.tazzzo.app.ui.common.TransientMessageToast
import com.tazzzo.app.ui.order.opensConfirmation

@Composable
fun App() {
    // Catalogue mode is decided ONCE, before any screen reads it. Release is always REMOTE; a debug
    // build is REMOTE unless a developer/demo/test EXPLICITLY asks for the mock catalogue.
    remember { applyDevCatalogMode() }
    val appState = remember { TazzzoAppState() }
    val appScope = rememberCoroutineScope()
    CompositionLocalProvider(LocalAppState provides appState, LocalAppScope provides appScope) {
        PlatformBackHandler(
            enabled = appState.canHandleSystemBack,
            onBack = { appState.handleSystemBack() }
        )
        com.tazzzo.app.theme.SystemReduceMotionEffect()
        PersistenceRunner()
        AuthSessionRunner(appState)
        CatalogRunner()
        if (ServiceLocator.catalogMode == CatalogMode.REMOTE) { com.tazzzo.app.ui.cart.CartNoticeHost(); OrderStateRunner(appState) }
        DemoTourRunner()
        // The ONE product-image pipeline (UI-03): every product surface reads it through LocalProductImageLoader.
        com.tazzzo.app.ui.common.ProvideProductImageLoader(com.tazzzo.app.image.ProductImagePipeline.loader) {
        TazzzoTheme {
            Surface(Modifier.fillMaxSize().background(TazColors.Cream), color = TazColors.Cream) {
                // One host for direction AND continuity.
                //
                // `SaveableStateHolder` is the piece that was missing: an
                // AnimatedContent (like the Crossfade before it) DISPOSES the
                // outgoing screen, so every `remember` in it — scroll position,
                // search query, selected filters — was destroyed on the way
                // out and rebuilt from scratch on the way back. Wrapping each
                // destination in a provider keyed by its identity means
                // `rememberSaveable` state (and `rememberLazyListState`, which
                // is saveable) survives the round trip. That is what makes
                // "scroll halfway, open a product, come back" land where the
                // customer left off.
                val stateHolder = rememberSaveableStateHolder()
                val liveKeys = appState.backStack.map { it.stateKey }

                // Drop retained state for destinations that are no longer
                // reachable, so a long session cannot accumulate them.
                var knownKeys by remember { mutableStateOf(emptySet<String>()) }
                LaunchedEffect(liveKeys.joinToString("|")) {
                    val live = liveKeys.toSet()
                    (knownKeys - live).forEach { stateHolder.removeState(it) }
                    if (Screen.Search.stateKey !in live) appState.dropSearch()   // Search left the stack: its query/results go too
                    if (Screen.Login.stateKey !in live) appState.dropAuthFlow()  // Login left the stack: drop the phone/OTP flow
                    knownKeys = live
                }

                Box(Modifier.fillMaxSize()) {
                AnimatedContent(
                    targetState = appState.current,
                    transitionSpec = { tazNavTransition(appState.navDirection) },
                    label = "navigation"
                ) { screen ->
                    stateHolder.SaveableStateProvider(screen.stateKey) {
                    // One routing decision for every destination (RouteTable.kt): REMOTE never renders a MOCK screen.
                    val mock = routeImpl(screen, ServiceLocator.catalogMode) == RouteImpl.MOCK
                    val unavailable = routeImpl(screen, ServiceLocator.catalogMode) == RouteImpl.UNAVAILABLE
                    when (screen) {
                        is Screen.Splash -> SplashScreen()
                        is Screen.Onboarding -> ShowcaseScreen()   // the 3-page brand carousel; Login follows
                        is Screen.Login -> LoginScreen()
                        is Screen.Home -> MainScaffold()
                        is Screen.CategoryDetail ->
                            if (mock) CategoryDetailScreen(screen.categoryId, screen.subcategoryId)
                            else RemoteCategoryScreen(screen.categoryId, screen.subcategoryId)
                        is Screen.ProductDetail -> if (mock) ProductDetailScreen(screen.productId) else RemoteProductDetailScreen(screen.productId)
                        is Screen.Search -> if (mock) SearchScreen() else com.tazzzo.app.ui.catalog.RemoteSearchScreen()
                        is Screen.Cart -> if (mock) CartScreen() else com.tazzzo.app.ui.cart.RemoteCartScreen()
                        // REMOTE: the real quote review only; the 4-step mock checkout is MOCK-mode only.
                        is Screen.Checkout -> if (mock) CheckoutScreen() else com.tazzzo.app.ui.checkout.RemoteCheckoutScreen()
                        // REMOTE: the confirmation is built ONLY from the real backend order (the mock screen reads `lastOrder`).
                        is Screen.OrderSuccess -> if (mock) OrderSuccessScreen(screen.orderId) else com.tazzzo.app.ui.order.RemoteOrderSuccessScreen(screen.orderId)
                        is Screen.Orders -> if (mock) OrdersScreen() else com.tazzzo.app.ui.order.RemoteOrdersScreen()
                        is Screen.Help -> if (mock) HelpScreen() else com.tazzzo.app.ui.support.RemoteHelpScreen()
                        is Screen.Addresses -> if (mock) AddressesScreen() else RemoteAddressesScreen()
                        is Screen.AddressForm -> RemoteAddressFormScreen(screen.addressId)
                        is Screen.About -> if (mock) AboutScreen() else com.tazzzo.app.ui.profile.RemoteAboutScreen()
                        is Screen.OrderDetail -> if (mock) OrderDetailScreen(screen.orderId) else com.tazzzo.app.ui.order.RemoteOrderDetailScreen(screen.orderId)
                        is Screen.Legal -> com.tazzzo.app.ui.support.LegalScreen(screen.slug)
                        is Screen.SupportCase -> com.tazzzo.app.ui.support.SupportCaseScreen(screen.caseId)
                        is Screen.SupportNew -> com.tazzzo.app.ui.support.NewSupportRequestScreen(screen.orderId)
                        // MOCK-only features. REMOTE has no caller; if one is ever reached it is a truthful "not available" page.
                        is Screen.Coins -> if (unavailable) UnavailableSurface("Tazzzo Coins") else CoinsScreen()
                        is Screen.MasterList -> if (unavailable) UnavailableSurface("Shopping list") else MasterListScreen()
                        is Screen.Club -> if (unavailable) UnavailableSurface("Tazzzo Club") else ClubScreen()
                        is Screen.ClubCheckout -> if (unavailable) UnavailableSurface("Tazzzo Club") else ClubCheckoutScreen()
                        is Screen.Voice -> if (unavailable) UnavailableSurface("Voice ordering") else com.tazzzo.app.ui.voice.GenieScreen()
                    }
                    }
                }
                // The transient notice ("Only 3 left", cart refusals) is hosted HERE, above every screen, so a notice raised on
                // the PDP, the cart or the address book shows at once (it used to render only while Home was on top).
                TransientMessageToast(aboveNav = appState.current is Screen.Home)
                }
            }
        }
        }
    }
}

/** A scope that lives as long as the app's composition: holders that must outlive one screen (Search) run on it. */
val LocalAppScope = staticCompositionLocalOf<kotlinx.coroutines.CoroutineScope> { error("App scope not provided") }

/**
 * Restores the secure session at start-up and keeps [TazzzoAppState] in step
 * with it: a definitive refresh rejection elsewhere flips the app to guest.
 */
@Composable
private fun AuthSessionRunner(app: TazzzoAppState) {
    LaunchedEffect(Unit) {
        val session = ServiceLocator.authSession
        if (ServiceLocator.catalogMode == CatalogMode.REMOTE) {
            // Restore the secure session, then start the bindings knowing whether one exists, then recover a pending order:
            // one automatic reconciliation if restored, deletion only if the credential was definitively refused. A session that
            // is merely absent/not-yet-restored is never treated as a logout.
            com.tazzzo.app.data.order.restoreSessionAndRecoverOrders(session, ServiceLocator.orderStore) { initiallyAuthenticated ->
                // Signed in -> load addresses; signed out / rejected -> wipe them and reset the location to 560047.
                com.tazzzo.app.data.address.SessionLocationBinding(
                    ServiceLocator.authScope, session.active, ServiceLocator.addressBook, ServiceLocator.deliveryLocation
                ).start(initiallyAuthenticated = initiallyAuthenticated)
                ServiceLocator.startCartBinding(initiallyAuthenticated)
            }
        } else {
            session.restore()
        }
        session.active.collect { app.applyAuthState(it) }
    }
}

/**
 * Debug-only explicit selection of the MOCK catalogue: the launch flag, or the demo/autopilot flags
 * (which are built around mock data). In a release build [AppEnvironment.allowsDevTooling] is false,
 * so nothing here can run and the catalogue is REMOTE.
 */
internal fun applyDevCatalogMode() {
    if (AppEnvironment.allowsDevTooling && (isMockCatalogRequested() || isDemoTourEnabled() || isDemoHomeEnabled())) {
        CatalogSource.debugOverride = CatalogMode.MOCK
    }
}

/** REMOTE mode: check delivery for the launch PIN once at start-up. */
@Composable
private fun CatalogRunner() {
    LaunchedEffect(Unit) {
        if (ServiceLocator.catalogMode == CatalogMode.REMOTE) ServiceLocator.launchContext.refresh()
    }
}

/**
 * REMOTE: turns order state changes into navigation. A placed order opens the real confirmation (home first, so Back never
 * lands on a spent checkout); an unresolved attempt (e.g. after a restart) brings the customer to "Check order".
 */
@Composable
private fun OrderStateRunner(app: TazzzoAppState) {
    val state by ServiceLocator.orderStore.state.collectAsState()
    LaunchedEffect(state) {
        when (val s = state) {
            is com.tazzzo.app.data.order.OrderState.Placed -> {
                // An already-cancelled order (found by "Check order") opens its detail, not the "Order placed" confirmation.
                val target = if (s.order.opensConfirmation()) Screen.OrderSuccess(s.order.orderId) else Screen.OrderDetail(s.order.orderId)
                if (app.current != target) { app.goHome(); app.navigate(target) }
                if (target is Screen.OrderDetail) ServiceLocator.orderStore.acknowledge()
            }
            is com.tazzzo.app.data.order.OrderState.Ambiguous -> if (app.current !is Screen.Checkout) app.navigate(Screen.Checkout)
            else -> Unit
        }
    }
}
