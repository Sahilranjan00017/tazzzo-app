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
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import com.tazzzo.app.ui.interaction.TazPress
import com.tazzzo.app.ui.interaction.tazPressable
import com.tazzzo.app.theme.TazColors
import com.tazzzo.app.theme.TazIcons
import com.tazzzo.app.theme.TazRadius
import com.tazzzo.app.theme.TazSize
import com.tazzzo.app.theme.TazSpace
import com.tazzzo.app.theme.TazType
import com.tazzzo.app.ui.common.CartBar
import com.tazzzo.app.ui.guided.GuidedJourneyOverlay
import com.tazzzo.app.ui.voice.VoiceComingSoonSheet

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
                HomeTab.SHOP -> if (remote) com.tazzzo.app.ui.catalog.RemoteShopContent() else CategoriesTabContent()
                HomeTab.DEALS -> DealsTabContent()
                HomeTab.ORDERS -> if (remote) com.tazzzo.app.ui.order.RemoteOrdersContent(inTab = true) else OrdersScreen()
                HomeTab.ORDER_AGAIN -> OrderAgainTabContent()
                HomeTab.PROFILE -> if (remote) com.tazzzo.app.ui.profile.RemoteProfileContent() else AccountTabContent()
            }
        }
        FloatingNavBar(Modifier.align(Alignment.BottomCenter))

        CartBar(aboveNav = true)
        // The transient notice is hosted at the App root (above every screen), not here.

        if (app.guidedJourneyPending) {
            GuidedJourneyOverlay(onDone = { app.markTourSeen() })
        }
        if (app.showVoiceSheet) {
            VoiceComingSoonSheet(onDismiss = { app.showVoiceSheet = false })
        }
    }
}
