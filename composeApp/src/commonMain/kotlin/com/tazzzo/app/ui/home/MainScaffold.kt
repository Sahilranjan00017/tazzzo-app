package com.tazzzo.app.ui.home

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.tazzzo.app.HomeTab
import com.tazzzo.app.LocalAppState
import com.tazzzo.app.data.repository.ServiceLocator
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import com.tazzzo.app.ui.interaction.TazPress
import com.tazzzo.app.ui.interaction.tazPressable
import androidx.compose.animation.core.tween
import com.tazzzo.app.theme.TazMotion
import com.tazzzo.app.theme.TazColors
import com.tazzzo.app.theme.TazIcons
import com.tazzzo.app.theme.TazRadius
import com.tazzzo.app.theme.TazSize
import com.tazzzo.app.theme.TazSpace
import com.tazzzo.app.theme.TazType
import com.tazzzo.app.ui.common.CartBar
import com.tazzzo.app.ui.guided.GuidedJourneyOverlay
import com.tazzzo.app.ui.voice.VoiceComingSoonSheet
import kotlinx.coroutines.delay

/**
 * The main shell of the app: 5 bottom tabs + floating cart bar + one-time
 * guided-journey overlay + voice "coming soon" sheet.
 */
@Composable
fun MainScaffold() {
    val app = LocalAppState.current
    Box(Modifier.fillMaxSize().background(TazColors.Cream)) {
        // The navigation FLOATS over the content (UI Page `Home.jpeg`); each tab's content leaves TazSize.floatingNavClearance
        // of room at the bottom so nothing scrolls under the capsule.
        Box(Modifier.fillMaxSize()) {
            val remote = ServiceLocator.catalogMode == com.tazzzo.app.data.catalog.CatalogMode.REMOTE
            // A tab the active catalogue cannot support (e.g. a remembered DEALS in REMOTE mode)
            // falls back to Home rather than rendering mock content.
            val tab = if (app.homeTab in visibleHomeTabs(ServiceLocator.catalogCapabilities)) app.homeTab else HomeTab.HOME
            when (tab) {
                HomeTab.HOME -> if (remote) RemoteHomeContent() else HomeTabContent()
                HomeTab.SHOP -> if (remote) com.tazzzo.app.ui.catalog.RemoteCategoriesContent() else CategoriesTabContent()
                HomeTab.DEALS -> DealsTabContent()
                HomeTab.ORDERS -> if (remote) com.tazzzo.app.ui.order.RemoteOrdersContent(inTab = true) else OrdersScreen()
                HomeTab.ORDER_AGAIN -> OrderAgainTabContent()
                HomeTab.PROFILE -> AccountTabContent()
            }
        }
        FloatingNavBar(Modifier.align(Alignment.BottomCenter))

        CartBar(aboveNav = true)
        TransientMessageToast()

        if (app.guidedJourneyPending) {
            GuidedJourneyOverlay(onDone = { app.markTourSeen() })
        }
        if (app.showVoiceSheet) {
            VoiceComingSoonSheet(onDismiss = { app.showVoiceSheet = false })
        }
    }
}

/**
 * Small centered bottom toast for [com.tazzzo.app.TazzzoAppState.transientMessage]
 * (e.g. "Only 3 left in stock"). Sits above the floating cart bar and
 * auto-dismisses after 2.2 seconds.
 */
@Composable
private fun BoxScope.TransientMessageToast() {
    val app = LocalAppState.current
    val message = app.transientMessage
    // Held so the copy does not blank out mid-exit-animation.
    var lastMessage by remember { mutableStateOf("") }
    LaunchedEffect(message) { if (message != null) lastMessage = message }
    LaunchedEffect(message) {
        if (message != null) {
            delay(2200)
            app.transientMessage = null
        }
    }
    // Anchored above the cart bar when there is one, and to the nav bar when
    // there is not — the old fixed 120dp offset left the toast floating in
    // mid-air on an empty cart, and was a magic number outside the token scale.
    // Sits above whatever owns the bottom edge: the nav bar, plus the cart
    // bar when there is one. Derived from the same tokens those use, so the
    // three never drift apart.
    val app2 = LocalAppState.current
    // The SERVER cart's count in REMOTE (observed, so the toast moves with the bar), the local demo cart in MOCK.
    val cartBarUp = if (com.tazzzo.app.data.repository.ServiceLocator.catalogMode == com.tazzzo.app.data.catalog.CatalogMode.REMOTE) {
        val st by com.tazzzo.app.data.repository.ServiceLocator.cart.state.collectAsState()
        ((st as? com.tazzzo.app.data.cart.CartState.Loaded)?.cart?.itemCount ?: 0) > 0
    } else app2.cartItemCount > 0
    val cartBarBand = 64.dp + TazSpace.md * 2          // bar height + its vertical padding
    val bottomInset = TazSize.navBarHeight + TazSpace.lg +
        (if (cartBarUp) cartBarBand else 0.dp)
    AnimatedVisibility(
        visible = message != null,
        modifier = Modifier.align(Alignment.BottomCenter),
        // Rises into place and fades out. A refusal that hard-cuts on screen
        // reads as a glitch; the customer needs to see it arrive.
        enter = slideInVertically(tween(TazMotion.fast)) { it / 2 } + fadeIn(tween(TazMotion.fast)),
        exit = fadeOut(tween(TazMotion.fast))
    ) {
        Box(
            Modifier
                .padding(bottom = bottomInset)
                .clip(TazRadius.pill)
                .background(TazColors.TextPrimary)
                .padding(horizontal = TazSpace.lg, vertical = TazSpace.sm)
                // Announced to screen readers. Previously a customer using
                // TalkBack or VoiceOver was never told the app had refused
                // their tap — the only signal was a visual one they could not
                // see. This is the accessibility half of the limit feedback.
                .semantics { liveRegion = LiveRegionMode.Polite },
        ) {
            Text(
                lastMessage,
                color = TazColors.White,
                fontSize = TazType.captionSize,
                lineHeight = TazType.captionLine
            )
        }
    }
}



