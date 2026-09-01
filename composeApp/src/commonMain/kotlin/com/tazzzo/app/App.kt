package com.tazzzo.app

import androidx.compose.animation.Crossfade
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
import com.tazzzo.app.ui.home.AboutScreen
import com.tazzzo.app.ui.onboarding.LoginScreen
import com.tazzzo.app.ui.onboarding.OnboardingScreen
import com.tazzzo.app.ui.splash.SplashScreen

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
                Crossfade(targetState = appState.current) { screen ->
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
                        is Screen.About -> AboutScreen()
                    }
                }
            }
        }
    }
}
