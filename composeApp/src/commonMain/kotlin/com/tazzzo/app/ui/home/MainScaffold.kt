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
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Surface
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
import com.tazzzo.app.theme.TazColors
import com.tazzzo.app.theme.TazIcons
import com.tazzzo.app.theme.TazRadius
import com.tazzzo.app.theme.TazSize
import com.tazzzo.app.theme.TazSpace
import com.tazzzo.app.theme.TazType
import com.tazzzo.app.ui.common.CartBar
import com.tazzzo.app.ui.common.TazIcon
import com.tazzzo.app.ui.common.guidedTarget
import com.tazzzo.app.ui.guided.GuidedJourneyOverlay
import com.tazzzo.app.ui.voice.VoiceComingSoonSheet
import kotlinx.coroutines.delay

/**
 * The main shell of the app: 4 bottom tabs + floating cart bar + one-time
 * guided-journey overlay + voice "coming soon" sheet.
 */
@Composable
fun MainScaffold() {
    val app = LocalAppState.current
    Box(Modifier.fillMaxSize().background(TazColors.Cream)) {
        Column(Modifier.fillMaxSize()) {
            Box(Modifier.weight(1f).fillMaxWidth()) {
                when (app.homeTab) {
                    HomeTab.HOME -> HomeTabContent()
                    HomeTab.CATEGORIES -> CategoriesTabContent()
                    HomeTab.ORDER_AGAIN -> OrderAgainTabContent()
                    HomeTab.ACCOUNT -> AccountTabContent()
                }
            }
            BottomNavBar()
        }

        CartBar()
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
    LaunchedEffect(message) {
        if (message != null) {
            delay(2200)
            app.transientMessage = null
        }
    }
    if (message != null) {
        Box(
            Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = 120.dp)
                .clip(TazRadius.pill)
                .background(TazColors.TextPrimary)
                .padding(horizontal = TazSpace.lg, vertical = TazSpace.sm),
        ) {
            Text(
                message,
                color = TazColors.White,
                fontSize = TazType.captionSize,
                lineHeight = TazType.captionLine
            )
        }
    }
}

/** One icon family for the shell — no emoji reaches the navigation bar. */
private fun navIconFor(tab: HomeTab): ImageVector = when (tab) {
    HomeTab.HOME -> TazIcons.Home
    HomeTab.CATEGORIES -> TazIcons.Categories
    HomeTab.ORDER_AGAIN -> TazIcons.OrderAgain
    HomeTab.ACCOUNT -> TazIcons.Account
}

/** Geometry of the selected-tab pill sitting behind the icon. */
private val NavPillHeight = 28.dp
private val NavPillWidth = 52.dp

@Composable
private fun BottomNavBar() {
    val app = LocalAppState.current
    Surface(color = TazColors.Surface, shadowElevation = 12.dp) {
        Column(Modifier.fillMaxWidth()) {
            // hairline top border — separates the bar from cream content
            Box(Modifier.fillMaxWidth().height(1.dp).background(TazColors.CardBorder))
            Row(
                Modifier
                    .fillMaxWidth()
                    .guidedTarget("bottomnav")
                    .navigationBarsPadding()
                    .height(TazSize.navBarHeight),
                horizontalArrangement = Arrangement.SpaceEvenly,
                verticalAlignment = Alignment.CenterVertically
            ) {
                HomeTab.entries.forEach { tab ->
                    val selected = app.homeTab == tab
                    val tint = if (selected) TazColors.Green else TazColors.TextTertiary
                    Column(
                        Modifier
                            .weight(1f)
                            .fillMaxHeight()
                            .clickable { app.homeTab = tab }
                            .semantics(mergeDescendants = true) {
                                contentDescription = tab.label
                            },
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
                    ) {
                        Box(
                            Modifier
                                .height(NavPillHeight)
                                .width(NavPillWidth)
                                .clip(TazRadius.pill)
                                .background(
                                    if (selected) TazColors.GreenSoft else Color.Transparent
                                ),
                            contentAlignment = Alignment.Center
                        ) {
                            TazIcon(navIconFor(tab), null, size = TazSize.iconMd, tint = tint)
                        }
                        Spacer(Modifier.height(TazSpace.xxs))
                        Text(
                            tab.label,
                            fontSize = TazType.navLabelSize,
                            lineHeight = TazType.microLine,
                            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Medium,
                            color = tint,
                            maxLines = 1
                        )
                    }
                }
            }
        }
    }
}
