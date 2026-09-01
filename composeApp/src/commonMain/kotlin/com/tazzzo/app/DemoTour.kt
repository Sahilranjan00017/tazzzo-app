package com.tazzzo.app

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import com.tazzzo.app.data.model.PaymentMethodKind
import com.tazzzo.app.data.repository.ServiceLocator
import com.tazzzo.app.order.OrderPlacement
import kotlinx.coroutines.delay

/**
 * Demo autopilot: walks the whole app screen-by-screen for demos, videos and
 * screenshot verification. Only active when the process is launched with the
 * TAZZZO_DEMO_TOUR environment variable, e.g.:
 *   SIMCTL_CHILD_TAZZZO_DEMO_TOUR=1 xcrun simctl launch <udid> com.tazzzo.app
 * Completely inert in normal launches.
 */
expect fun isDemoTourEnabled(): Boolean

/** TAZZZO_DEMO_HOME=1 → jump straight to Home (for screenshots/demos). */
expect fun isDemoHomeEnabled(): Boolean

/** TAZZZO_DEMO_FAIL_LOAD=1 → first catalogue load throws once, to verify the
 *  error/retry state on a real device. Dev tool; inert in production. */
expect fun isDemoFailLoadEnabled(): Boolean

@Composable
fun DemoTourRunner() {
    val app = LocalAppState.current
    LaunchedEffect(Unit) {
        if (isDemoHomeEnabled()) { app.resetTo(Screen.Home); return@LaunchedEffect }
        if (!isDemoTourEnabled()) return@LaunchedEffect

        // Dev-only search smoke test — evidence in the device log, ships nowhere.
        for (q in listOf("aata", "doodh", "magi", "sabun", "atta 5kg", "xyzzy")) {
            val hits = ServiceLocator.catalog.search(q)
            println("TAZZZO-SEARCHCHECK q='$q' -> ${hits.size} hits: ${hits.take(3).joinToString { it.name }}")
        }

        // Splash plays on its own (auto-advances at 1.8s) → onboarding.
        delay(4_500)                                    // t≈4.5s onboarding w/ marquee

        delay(2_000)                                    // t≈6.5s login screen
        app.navigate(Screen.Login)
        delay(2_500)                                    // t≈9s

        // "Skip for now" → home with the spotlight guided tour. In demo mode the
        // overlay auto-advances through its 7 steps (~2s each) and calls onDone
        // itself, which clears guidedJourneyPending.
        app.guidedJourneyPending = true
        app.resetTo(Screen.Home)
        delay(16_000)                                   // t≈9s..25s spotlight tour steps

        app.guidedJourneyPending = false                // safety net if tour still open
        delay(2_500)                                    // clean home, rails loaded

        app.showVoiceSheet = true
        delay(2_500)                                    // t≈16.5s voice coming-soon sheet

        app.showVoiceSheet = false
        app.navigate(Screen.CategoryDetail("meat"))
        delay(2_500)                                    // t≈19s category two-pane

        // Add items → cart bar + steppers.
        ServiceLocator.catalog.getProducts("meat").take(2).forEach { p ->
            app.addToCart(p); app.addToCart(p)
        }
        delay(2_000)                                    // t≈21s ADD steppers + cart bar

        // Product detail page (Chicken Curry Cut — purchasable, same aisle)
        app.navigate(Screen.ProductDetail("p22"))
        delay(2_800)
        app.back()

        app.navigate(Screen.Cart)
        delay(3_000)                                    // cart + bill details

        // ---- checkout: drive the REAL state machine the UI observes --------
        app.checkout = null
        app.navigate(Screen.Checkout)
        delay(2_800)                                    // address step + revalidation
        val session = app.checkout ?: return@LaunchedEffect
        val addresses = ServiceLocator.addresses.getAddresses()
        session.selectAddress(addresses.first())
        delay(1_500)
        session.next()                                  // -> slot
        delay(2_200)
        val slots = ServiceLocator.checkout.getSlots(addresses.first().id)
        session.slot = slots.first { it.available }
        delay(1_200)
        session.next()                                  // -> payment
        delay(2_200)
        session.payment = PaymentMethodKind.COD
        delay(1_200)
        session.next()                                  // -> review
        delay(2_600)

        // Render the failure state (UI verification — never shown as success).
        session.placement = CheckoutSession.Placement.Failed(
            "We couldn't reach the order service.", retryable = true
        )
        delay(2_600)

        // Place through the SAME shared path the checkout screen uses, so the
        // demo can never diverge from real placement semantics — including the
        // replay guard that keeps a repeated placement from crediting coins
        // twice. Do not hand-roll placement here again.
        OrderPlacement.place(
            app = app,
            session = session,
            address = addresses.first(),
            slot = session.slot!!,
            payment = PaymentMethodKind.COD
        )
        delay(2_500)                                    // order success

        app.resetTo(Screen.Home)
        app.navigate(Screen.Orders)
        delay(2_500)                                    // t≈29s orders + status steps

        app.navigate(Screen.Coins)
        delay(2_500)                                    // t≈31.5s coins hero + ledger

        app.navigate(Screen.Help)
        delay(2_500)                                    // t≈34s help + FAQs

        app.resetTo(Screen.Home)
        app.navigate(Screen.Search)
        delay(2_500)                                    // t≈36.5s search screen

        app.resetTo(Screen.Home)
        app.homeTab = HomeTab.CATEGORIES
        delay(2_500)                                    // t≈39s all categories

        app.homeTab = HomeTab.ACCOUNT
        delay(2_500)                                    // t≈41.5s account menu

        app.homeTab = HomeTab.HOME                      // t≈44s rest on home
    }
}
