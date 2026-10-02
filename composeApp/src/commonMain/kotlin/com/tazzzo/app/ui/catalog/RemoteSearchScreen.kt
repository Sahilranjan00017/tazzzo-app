package com.tazzzo.app.ui.catalog

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import com.tazzzo.app.HomeTab
import com.tazzzo.app.LocalAppState
import com.tazzzo.app.data.repository.ServiceLocator
import com.tazzzo.app.theme.TazColors
import com.tazzzo.app.theme.TazIcons
import com.tazzzo.app.theme.TazSize
import com.tazzzo.app.theme.TazSpace
import com.tazzzo.app.theme.TazType
import com.tazzzo.app.ui.common.EditorialEmptyState
import com.tazzzo.app.ui.common.EditorialText
import com.tazzzo.app.ui.common.TazIcon
import com.tazzzo.app.ui.common.TazSearchShell
import com.tazzzo.app.ui.common.plain
import com.tazzzo.app.ui.interaction.TazPress
import com.tazzzo.app.ui.interaction.tazPressable

/**
 * Search in REMOTE mode (UI-03). The running backend has no search endpoint and [CatalogCapabilities.search] is false,
 * so this screen is the TRUTHFUL one: the approved editorial heading and the shared search shell (static — typing
 * would have nothing real to query), then a clear "not yet" state with the way back into the Shop. No local index, no
 * mock results, no field that pretends. When a real search contract ships, results render through [TazProductCard].
 */
@Composable
fun RemoteSearchScreen() {
    val app = LocalAppState.current
    SearchScreenLayout(
        surface = searchSurface(ServiceLocator.catalogCapabilities),
        onBack = { app.back() },
        onShop = { app.homeTab = HomeTab.SHOP; app.back() }
    )
}

@Composable
fun SearchScreenLayout(surface: SearchSurface, onBack: () -> Unit, onShop: () -> Unit) {
    Column(Modifier.fillMaxSize().background(TazColors.Cream).verticalScroll(rememberScrollState()).testTag("searchScreen")) {
        Row(
            Modifier.fillMaxWidth().statusBarsPadding().padding(start = TazSpace.md, end = TazSpace.lg, top = TazSpace.sm),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                Modifier.size(TazSize.touchTarget).clip(CircleShape).background(TazColors.Surface)
                    .tazPressable(onClick = onBack, pressScale = TazPress.compact, role = Role.Button)
                    .semantics { contentDescription = "Back" },
                contentAlignment = Alignment.Center
            ) { TazIcon(TazIcons.Back, null, size = TazSize.iconSm, tint = TazColors.BrandEditorial) }
            Spacer(Modifier.width(TazSpace.md))
            EditorialText(listOf(plain(ShopCopy.SEARCH_TITLE)), size = TazType.editorialHeadlineSize, lineHeight = TazType.editorialHeadlineLine, color = TazColors.BrandEditorial, textAlign = TextAlign.Start)
        }
        Spacer(Modifier.height(TazSpace.lg))
        TazSearchShell(onClick = null, modifier = Modifier.padding(horizontal = TazSpace.lg))
        Spacer(Modifier.height(TazSpace.xl))
        when (surface) {
            // There is no real results path yet: Available is never true for the REMOTE catalogue, and when a contract
            // exists the results list is added here. Until then both branches say the same honest thing.
            SearchSurface.Unavailable, SearchSurface.Available -> EditorialEmptyState(
                TazIcons.Search, ShopCopy.SEARCH_UNAVAILABLE_TITLE, ShopCopy.SEARCH_UNAVAILABLE_BODY,
                actionLabel = ShopCopy.BACK_TO_SHOP, onAction = onShop
            )
        }
    }
}
