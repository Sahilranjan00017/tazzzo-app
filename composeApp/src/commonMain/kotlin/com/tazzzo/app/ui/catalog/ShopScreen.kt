package com.tazzzo.app.ui.catalog

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.tazzzo.app.LocalAppState
import com.tazzzo.app.Screen
import com.tazzzo.app.data.catalog.CatalogFailure
import com.tazzzo.app.data.catalog.CatalogNode
import com.tazzzo.app.data.catalog.NodesState
import com.tazzzo.app.data.repository.ServiceLocator
import com.tazzzo.app.theme.TazColors
import com.tazzzo.app.theme.TazIcons
import com.tazzzo.app.theme.TazRadius
import com.tazzzo.app.theme.TazSize
import com.tazzzo.app.theme.TazSpace
import com.tazzzo.app.theme.TazType
import com.tazzzo.app.theme.tazEditorialFamily
import com.tazzzo.app.ui.common.CategoryTileSkeleton
import com.tazzzo.app.ui.common.EditorialEmptyState
import com.tazzzo.app.ui.common.EditorialFailureState
import com.tazzzo.app.ui.common.EditorialText
import com.tazzzo.app.ui.common.TazSearchShell
import com.tazzzo.app.ui.common.plain
import com.tazzzo.app.ui.home.deliveryStatusLine
import com.tazzzo.app.ui.home.homeCategoryArt
import com.tazzzo.app.ui.interaction.TazPress
import com.tazzzo.app.ui.interaction.tazPressable
import org.jetbrains.compose.resources.painterResource

/**
 * The Shop tab (UI-03): category discovery in the approved Tazzzo language, derived from Home's category row and the
 * UI-01 screens (there is no standalone Shop reference). An editorial "Shop" heading, the shared search shell, then
 * the REAL taxonomy root as rounded category tiles — reference artwork where an approved plate matches the backend's
 * name, a quiet serif initial otherwise. No fake taxonomy, no counts, no raw ids. Deeper levels open the browse screen.
 */
@Composable
fun RemoteShopContent() {
    val app = LocalAppState.current
    val browser = rememberTaxonomyBrowser()
    val root by browser.root.collectAsState()
    LaunchedEffect(Unit) { browser.ensureRoot() }
    val pin by ServiceLocator.launchContext.pin.collectAsState()
    val serviceability by ServiceLocator.launchContext.state.collectAsState()
    var failures by remember { mutableStateOf(0) }
    LaunchedEffect(root) { if (root is NodesState.Loaded) failures = 0 }

    ShopScreenLayout(
        root = root,
        statusLine = deliveryStatusLine(serviceability, pin),
        consecutiveFailures = failures + 1,
        actions = ShopActions(
            openSearch = { app.navigate(Screen.Search) },
            openCategory = { app.navigate(Screen.CategoryDetail(it.id)) },
            retry = { failures++; browser.retryRoot() }
        ),
        banners = { ServiceabilityBannerView() }
    )
}

class ShopActions(val openSearch: () -> Unit, val openCategory: (CatalogNode) -> Unit, val retry: () -> Unit)

/** The Shop layout, independent of where its state comes from (evidence and tests render it with sample state). */
@Composable
fun ShopScreenLayout(
    root: NodesState,
    statusLine: String?,
    actions: ShopActions,
    consecutiveFailures: Int = 1,
    banners: @Composable () -> Unit = {}
) {
    LazyVerticalGrid(
        columns = GridCells.Adaptive(CATEGORY_GRID_MIN_CELL_DP.dp),
        modifier = Modifier.fillMaxSize().background(TazColors.Cream).testTag("shopRoot"),
        contentPadding = PaddingValues(start = TazSpace.lg, end = TazSpace.lg, bottom = TazSize.floatingNavClearance),
        horizontalArrangement = Arrangement.spacedBy(TazSpace.md),
        verticalArrangement = Arrangement.spacedBy(TazSpace.lg)
    ) {
        item(span = { GridItemSpan(maxLineSpan) }, key = "header") { ShopHeader(statusLine) }
        item(span = { GridItemSpan(maxLineSpan) }, key = "banners") { banners() }
        item(span = { GridItemSpan(maxLineSpan) }, key = "search") { TazSearchShell(onClick = actions.openSearch) }
        when (root) {
            NodesState.Idle, NodesState.Loading -> items(6, key = { "sk$it" }) { CategoryTileSkeleton() }
            is NodesState.Failed -> item(span = { GridItemSpan(maxLineSpan) }, key = "failed") {
                // A 404 on the ROOT taxonomy is not "this category is gone" — it is the catalogue service not answering (not
                // deployed / wrong host). Say that, and let the customer try again; every other failure keeps its own copy.
                if (root.failure == CatalogFailure.NotFound) EditorialEmptyState(
                    TazIcons.Store, ShopCopy.CATALOGUE_UNAVAILABLE_TITLE, ShopCopy.CATALOGUE_UNAVAILABLE_BODY, "Try again", onAction = actions.retry
                ) else EditorialFailureState(root.failure, consecutiveFailures, onRetry = actions.retry)
            }
            is NodesState.Loaded ->
                if (root.items.isEmpty()) item(span = { GridItemSpan(maxLineSpan) }, key = "empty") {
                    EditorialEmptyState(TazIcons.Store, ShopCopy.NO_CATEGORIES_TITLE, ShopCopy.NO_CATEGORIES_BODY)
                } else items(root.items, key = { it.id }) { node -> CategoryTile(node, onClick = { actions.openCategory(node) }) }
        }
    }
}

@Composable
private fun ShopHeader(statusLine: String?) {
    Column(Modifier.fillMaxWidth().statusBarsPadding().padding(top = TazSpace.lg)) {
        EditorialText(
            listOf(plain(ShopCopy.TITLE)), size = TazType.editorialHeadlineSize, lineHeight = TazType.editorialHeadlineLine,
            color = TazColors.BrandEditorial, textAlign = TextAlign.Start
        )
        // Only a REAL support line: serviceability for the current PIN, or nothing.
        statusLine?.let {
            Spacer(Modifier.height(TazSpace.xs))
            Text(it, fontSize = TazType.bodySize, color = TazColors.TextSecondary, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
    }
}

/**
 * The scalable category tile: a square sage/cream well with the approved artwork or a serif initial, the backend's
 * name beneath. One accessibility node per tile, labelled with the name. 44dp+ by construction.
 */
@Composable
fun CategoryTile(node: CatalogNode, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val art = homeCategoryArt(node)
    Column(
        modifier.clip(TazRadius.card).tazPressable(onClick = onClick, pressScale = TazPress.card).semantics { contentDescription = node.name }.padding(TazSpace.xxs),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // A circular well on the sage ground: the approved category plates are circular crops (Home.jpeg), so the tile keeps that framing.
        Box(Modifier.fillMaxWidth().aspectRatio(1f).clip(TazRadius.tile).background(TazColors.GreenSoft).padding(TazSpace.sm), contentAlignment = Alignment.Center) {
          Box(Modifier.fillMaxSize().clip(CircleShape).background(TazColors.SurfaceSunken), contentAlignment = Alignment.Center) {
            if (art != null) Image(painterResource(art), contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
            else Text(node.name.take(1).uppercase(), fontFamily = tazEditorialFamily(), fontSize = 34.sp, color = TazColors.BrandEditorial)
          }
        }
        Spacer(Modifier.height(TazSpace.sm))
        Text(
            node.name, fontSize = TazType.captionSize, lineHeight = TazType.captionLine, fontWeight = FontWeight.Medium, color = TazColors.TextPrimary,
            textAlign = TextAlign.Center, maxLines = 2, minLines = 2, overflow = TextOverflow.Ellipsis
        )
    }
}
