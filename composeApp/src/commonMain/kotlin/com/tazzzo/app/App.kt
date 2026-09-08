package com.tazzzo.app

import androidx.compose.animation.AnimatedContent
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import com.tazzzo.app.ui.interaction.tazNavTransition
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
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
import com.tazzzo.app.ui.onboarding.OnboardingScreen
import com.tazzzo.app.ui.splash.SplashScreen
import com.tazzzo.app.ui.home.MasterListScreen

@Composable
fun App() {
    val appState = remember { TazzzoAppState() }
    CompositionLocalProvider(LocalAppState provides appState) {
        PlatformBackHandler(
            enabled = appState.canHandleSystemBack,
            onBack = { appState.handleSystemBack() }
        )
        PersistenceRunner()
        DemoTourRunner()
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
                    knownKeys = live
                }

                AnimatedContent(
                    targetState = appState.current,
                    transitionSpec = { tazNavTransition(appState.navDirection) },
                    label = "navigation"
                ) { screen ->
                    stateHolder.SaveableStateProvider(screen.stateKey) {
                    when (screen) {
                        is Screen.Splash -> SplashScreen()
                        is Screen.Onboarding -> OnboardingScreen()
                        is Screen.Login -> LoginScreen()
                        is Screen.Home -> MainScaffold()
                        is Screen.CategoryDetail -> CategoryDetailScreen(screen.categoryId, screen.subcategoryId)
                        is Screen.ProductDetail -> ProductDetailScreen(screen.productId)
                        is Screen.Search -> SearchScreen()
                        is Screen.Cart -> CartScreen()
                        is Screen.Checkout -> CheckoutScreen()
                        is Screen.OrderSuccess -> OrderSuccessScreen(screen.orderId)
                        is Screen.Orders -> OrdersScreen()
                        is Screen.Coins -> CoinsScreen()
                        is Screen.Help -> HelpScreen()
                        is Screen.Addresses -> AddressesScreen()
                        is Screen.MasterList -> MasterListScreen()
                        is Screen.About -> AboutScreen()
                        is Screen.Club -> ClubScreen()
                        is Screen.ClubCheckout -> ClubCheckoutScreen()
                        is Screen.OrderDetail -> OrderDetailScreen(screen.orderId)
                    }
                    }
                }
            }
        }
    }
}
