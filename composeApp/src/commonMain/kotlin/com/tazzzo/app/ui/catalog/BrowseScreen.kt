package com.tazzzo.app.ui.catalog

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.Saver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.tazzzo.app.LocalAppState
import com.tazzzo.app.Screen
import com.tazzzo.app.data.catalog.AppendState
import com.tazzzo.app.data.catalog.CatalogProduct
import com.tazzzo.app.data.catalog.NodesState
import com.tazzzo.app.data.catalog.PagedState
import com.tazzzo.app.data.catalog.TaxonomyBrowser
import com.tazzzo.app.data.catalog.productListHolder
import com.tazzzo.app.data.repository.ServiceLocator
import com.tazzzo.app.theme.TazColors
import com.tazzzo.app.theme.TazIcons
import com.tazzzo.app.theme.TazRadius
import com.tazzzo.app.theme.TazSize
import com.tazzzo.app.theme.TazSpace
import com.tazzzo.app.theme.TazType
import com.tazzzo.app.ui.common.CatalogCardSkeleton
import com.tazzzo.app.ui.common.EditorialEmptyState
import com.tazzzo.app.ui.common.EditorialFailureState
import com.tazzzo.app.ui.common.EditorialText
import com.tazzzo.app.ui.common.TazIcon
import com.tazzzo.app.ui.common.plain
import com.tazzzo.app.ui.interaction.TazPress
import com.tazzzo.app.ui.interaction.tazPressable

/**
 * Category browsing + product listing (UI-03). One screen serves every depth of the backend's hierarchy: it opens on a
 * node, lists that node's products, and shows a chip row for each level whose children exist (section → category →
 * subcategory → vertical, or whatever the taxonomy really has — see [BrowsePath]). Choosing a chip narrows the list
 * to that child and reveals its own children; "All" steps back up. The header carries the opened node's name and the
 * chosen path as a breadcrumb. No product count (none is authoritative), no filter/sort (the backend has none).
 */
@Composable
fun RemoteCategoryScreen(categoryId: String, initialSubcategoryId: String?) {
    val app = LocalAppState.current
    val scope = rememberCoroutineScope()
    val reader = ServiceLocator.remoteCatalog
    val pin by ServiceLocator.launchContext.pin.collectAsState()
    val holder = remember(categoryId) { productListHolder(scope, reader, ServiceLocator.launchContext.pin) }
    val browser = remember(categoryId) { TaxonomyBrowser(scope, reader) }
    var path by rememberSaveable(categoryId, saver = BrowsePathSaver) { mutableStateOf(BrowsePath(categoryId, listOfNotNull(initialSubcategoryId))) }

    LaunchedEffect(path.levels) { path.levels.forEach { browser.ensureChildren(it) } }
    LaunchedEffect(path.current) { holder.open(path.current) }

    val state by holder.state.collectAsState()
    val children by browser.children.collectAsState()
    val levels = path.levels.mapIndexedNotNull { i, id -> (children[id] as? NodesState.Loaded)?.items?.takeIf { it.isNotEmpty() }?.let { ChipLevel(i, id, it) } }
    var failures by remember(categoryId, path.current, pin) { mutableStateOf(0) }
    val gridState = rememberSaveable(path.current, pin.value, saver = LazyGridState.Saver) { LazyGridState() }

    BrowseScreenLayout(
        title = reader.taxonomy.nameOf(categoryId) ?: "Products",
        breadcrumb = path.selected.mapNotNull { reader.taxonomy.nameOf(it) },
        levels = levels, path = path, state = state, gridState = gridState, consecutiveFailures = failures + 1,
        actions = BrowseActions(
            back = { app.back() },
            select = { level, id -> path = if (id == null) path.selectAll(level) else path.select(level, id) },
            openProduct = { app.navigate(Screen.ProductDetail(it.productId)) },
            retryFirst = { failures++; holder.refresh() },
            retryAppend = { failures++; holder.retryAppend() },
            loadMore = { holder.loadMore() },
            loaded = { failures = 0 }
        ),
        banners = { ServiceabilityBannerView() }
    )
}

private val BrowsePathSaver = Saver<androidx.compose.runtime.MutableState<BrowsePath>, String>(
    save = { (listOf(it.value.rootId) + it.value.selected).joinToString(",") },
    restore = { s -> val parts = s.split(',').filter { it.isNotEmpty() }; mutableStateOf(BrowsePath(parts.first(), parts.drop(1))) }
)

class BrowseActions(
    val back: () -> Unit,
    /** [id] null = "All" at that level. */
    val select: (level: Int, id: String?) -> Unit,
    val openProduct: (CatalogProduct) -> Unit,
    val retryFirst: () -> Unit,
    val retryAppend: () -> Unit,
    val loadMore: () -> Unit,
    val loaded: () -> Unit = {}
)

/** The browse/PLP layout, independent of its data source (evidence and tests render it with sample state). */
@Composable
fun BrowseScreenLayout(
    title: String,
    breadcrumb: List<String>,
    levels: List<ChipLevel>,
    path: BrowsePath,
    state: PagedState<CatalogProduct>,
    actions: BrowseActions,
    gridState: LazyGridState = rememberLazyGridState(),
    consecutiveFailures: Int = 1,
    banners: @Composable () -> Unit = {}
) {
    // Pagination: ask for more when the last visible card is within a few of the end.
    val total = (state as? PagedState.Content<*>)?.items?.size ?: 0
    LaunchedEffect(gridState, total) {
        snapshotFlow { gridState.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: -1 }
            .collect { last -> if (total > 0 && last >= total - PREFETCH_DISTANCE) actions.loadMore() }
    }

    Column(Modifier.fillMaxSize().background(TazColors.Cream)) {
        BrowseHeader(title, breadcrumb, onBack = actions.back)
        banners()
        levels.forEach { level -> ChipRow(level, path, onSelect = { id -> actions.select(level.level, id) }) }
        Box(Modifier.weight(1f).fillMaxWidth()) {
            when (state) {
                PagedState.Idle, PagedState.LoadingFirst -> ProductGridSkeleton()
                is PagedState.FirstPageFailed -> EditorialFailureState(state.failure, consecutiveFailures, onRetry = actions.retryFirst)
                PagedState.Empty -> EditorialEmptyState(TazIcons.Bag, ShopCopy.EMPTY_SECTION_TITLE, ShopCopy.EMPTY_SECTION_BODY)
                is PagedState.Content -> {
                    LaunchedEffect(Unit) { actions.loaded() }
                    LazyVerticalGrid(
                        modifier = Modifier.fillMaxSize().testTag("remoteProductGrid"),
                        state = gridState,
                        columns = GridCells.Adaptive(PRODUCT_GRID_MIN_CELL_DP.dp),
                        contentPadding = PaddingValues(start = TazSpace.screenEdge, end = TazSpace.screenEdge, top = TazSpace.sm, bottom = TazSpace.cartBarClearance),
                        horizontalArrangement = Arrangement.spacedBy(TazSpace.sm),
                        verticalArrangement = Arrangement.spacedBy(TazSpace.md)
                    ) {
                        // skuId is the list identity (a row is a SKU); productId is only for navigation.
                        items(state.items, key = { it.skuId }) { p -> TazProductCard(p, onClick = { actions.openProduct(p) }) }
                        item(span = { GridItemSpan(maxLineSpan) }, key = "footer") { GridFooter(state, consecutiveFailures, onRetry = actions.retryAppend) }
                    }
                }
            }
        }
    }
}

private const val PREFETCH_DISTANCE = 6

@Composable
private fun BrowseHeader(title: String, breadcrumb: List<String>, onBack: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().statusBarsPadding().padding(start = TazSpace.md, end = TazSpace.lg, top = TazSpace.sm, bottom = TazSpace.sm),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            Modifier.size(TazSize.touchTarget).clip(CircleShape).background(TazColors.Surface)
                .tazPressable(onClick = onBack, pressScale = TazPress.compact, role = Role.Button)
                .semantics { contentDescription = "Back" },
            contentAlignment = Alignment.Center
        ) { TazIcon(TazIcons.Back, null, size = TazSize.iconSm, tint = TazColors.BrandEditorial) }
        Spacer(Modifier.width(TazSpace.md))
        Column(Modifier.weight(1f)) {
            EditorialText(
                listOf(plain(title)), size = TazType.editorialScreenTitleSize, lineHeight = TazType.editorialScreenTitleLine, color = TazColors.BrandEditorial, textAlign = TextAlign.Start,
                modifier = Modifier.semantics { contentDescription = title }
            )
            if (breadcrumb.isNotEmpty()) Text(
                breadcrumb.joinToString(" › "), fontSize = TazType.captionSize, color = TazColors.TextSecondary,
                maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.testTag("breadcrumb")
            )
        }
    }
}

/** One level of the hierarchy as editorial chips: "All" (the owner node) then its real children, in backend order. */
@Composable
private fun ChipRow(level: ChipLevel, path: BrowsePath, onSelect: (String?) -> Unit) {
    val noneSelected = path.selected.getOrNull(level.level) == null
    LazyRow(
        Modifier.fillMaxWidth().testTag("chips${level.level}"),
        contentPadding = PaddingValues(horizontal = TazSpace.lg, vertical = TazSpace.xs),
        horizontalArrangement = Arrangement.spacedBy(TazSpace.sm)
    ) {
        item(key = "all") { BrowseChip(ShopCopy.ALL_IN, noneSelected) { onSelect(null) } }
        items(level.children, key = { it.id }) { c -> BrowseChip(c.name, path.isSelected(level.level, c.id)) { onSelect(c.id) } }
    }
}

@Composable
private fun BrowseChip(label: String, selected: Boolean, onClick: () -> Unit) {
    Box(
        Modifier.height(TazSize.chipHeight).clip(TazRadius.pill)
            .background(if (selected) TazColors.BrandEditorial else TazColors.Surface)
            .border(BorderStroke(1.dp, if (selected) TazColors.BrandEditorial else TazColors.CardBorder), TazRadius.pill)
            .tazPressable(onClick = onClick, pressScale = TazPress.compact, selected = selected, role = Role.Tab)
            .padding(horizontal = TazSpace.md),
        contentAlignment = Alignment.Center
    ) {
        Text(
            label, fontSize = TazType.captionSize, fontWeight = FontWeight.SemiBold,
            color = if (selected) TazColors.EditorialOnDark else TazColors.TextPrimary, maxLines = 1
        )
    }
}

@Composable
private fun GridFooter(s: PagedState.Content<CatalogProduct>, consecutiveFailures: Int, onRetry: () -> Unit) {
    when (val a = s.append) {
        AppendState.Loading -> Box(Modifier.fillMaxWidth().padding(TazSpace.lg), contentAlignment = Alignment.Center) {
            CircularProgressIndicator(color = TazColors.BrandEditorial, modifier = Modifier.size(24.dp), strokeWidth = 2.dp)
        }
        is AppendState.Failed -> EditorialFailureState(a.failure, consecutiveFailures, onRetry, compact = true)
        AppendState.Idle -> if (!s.hasMore) Text(
            ShopCopy.END_OF_LIST, fontSize = TazType.captionSize, color = TazColors.TextTertiary,
            textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth().padding(TazSpace.lg)
        )
    }
}

@Composable
private fun ProductGridSkeleton() {
    LazyVerticalGrid(
        modifier = Modifier.fillMaxSize().testTag("productGridSkeleton"), columns = GridCells.Adaptive(PRODUCT_GRID_MIN_CELL_DP.dp), userScrollEnabled = false,
        contentPadding = PaddingValues(horizontal = TazSpace.screenEdge, vertical = TazSpace.sm),
        horizontalArrangement = Arrangement.spacedBy(TazSpace.sm), verticalArrangement = Arrangement.spacedBy(TazSpace.md)
    ) { items(6) { CatalogCardSkeleton() } }
}
