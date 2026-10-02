package com.tazzzo.app.ui.home

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.tazzzo.app.HomeTab
import com.tazzzo.app.LocalAppState
import com.tazzzo.app.data.repository.ServiceLocator
import com.tazzzo.app.theme.TazColors
import com.tazzzo.app.theme.TazIcons
import com.tazzzo.app.theme.TazMotion
import com.tazzzo.app.theme.TazRadius
import com.tazzzo.app.theme.TazSize
import com.tazzzo.app.theme.TazSpace
import com.tazzzo.app.theme.TazType
import com.tazzzo.app.ui.interaction.TazHaptic
import com.tazzzo.app.ui.interaction.TazPress
import com.tazzzo.app.ui.interaction.rememberHaptics
import com.tazzzo.app.ui.interaction.tazPressable

/**
 * The floating navigation capsule (UI Page `Home.jpeg`): a rounded cream pill floating above the bottom edge with a soft
 * shadow; the active tab sits in a mint circular well with a green icon and label. Tabs come from
 * [visibleHomeTabs] (Home · Shop · Orders · Profile in production). Every tab is one accessibility node carrying label,
 * role and selected state.
 */
@Composable
fun FloatingNavBar(modifier: Modifier = Modifier) {
    val app = LocalAppState.current
    val haptics = rememberHaptics()
    Box(modifier.fillMaxWidth().navigationBarsPadding().padding(horizontal = NAV_SIDE_MARGIN, vertical = NAV_BOTTOM_MARGIN), contentAlignment = Alignment.Center) {
        Row(
            Modifier
                .fillMaxWidth()
                .height(TazSize.navBarHeight)
                .shadow(14.dp, TazRadius.pill, ambientColor = Color.Black.copy(alpha = 0.18f), spotColor = Color.Black.copy(alpha = 0.22f))
                .clip(TazRadius.pill)
                .background(TazColors.Surface.copy(alpha = 0.97f))
                .padding(horizontal = TazSpace.sm),
            horizontalArrangement = Arrangement.SpaceEvenly,
            verticalAlignment = Alignment.CenterVertically
        ) {
            visibleHomeTabs(ServiceLocator.catalogCapabilities).forEach { tab ->
                val selected = app.homeTab == tab
                val tint by animateColorAsState(if (selected) TazColors.BrandEditorial else TazColors.TextSecondary, tween(TazMotion.fast), label = "navTint")
                val well by animateColorAsState(if (selected) TazColors.GreenSoft else Color.Transparent, tween(TazMotion.fast), label = "navWell")
                Column(
                    Modifier
                        .weight(1f)
                        .fillMaxHeight()
                        .semantics(mergeDescendants = true) { contentDescription = tab.label }
                        .tazPressable(
                            onClick = { if (!selected) { haptics.perform(TazHaptic.Select); app.homeTab = tab } },
                            pressScale = TazPress.compact, haptic = null, role = Role.Tab, selected = selected
                        ),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    Box(Modifier.size(NAV_WELL).clip(CircleShape).background(well), contentAlignment = Alignment.Center) {
                        Icon(navIcon(tab, selected), contentDescription = null, tint = tint, modifier = Modifier.size(TazSize.iconMd))
                    }
                    Spacer(Modifier.height(2.dp))
                    Text(tab.label, fontSize = TazType.navLabelSize, fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Medium, color = tint, maxLines = 1)
                }
            }
        }
    }
}

private fun navIcon(tab: HomeTab, selected: Boolean): ImageVector = when (tab) {
    HomeTab.HOME -> if (selected) TazIcons.Home else TazIcons.HomeOutlined
    HomeTab.SHOP -> TazIcons.Shop
    HomeTab.ORDERS -> TazIcons.Orders
    HomeTab.PROFILE -> TazIcons.Profile
    HomeTab.DEALS -> TazIcons.Offer
    HomeTab.ORDER_AGAIN -> TazIcons.OrderAgain
}

private val NAV_SIDE_MARGIN = 20.dp
private val NAV_BOTTOM_MARGIN = 12.dp
private val NAV_WELL = 34.dp

