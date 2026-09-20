package com.tazzzo.app.ui.home

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.grid.*
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.tazzzo.app.LocalAppState
import com.tazzzo.app.Screen
import com.tazzzo.app.data.repository.ServiceLocator
import com.tazzzo.app.data.repository.Taxonomy
import com.tazzzo.app.ui.interaction.TazPress
import com.tazzzo.app.ui.interaction.tazPressable
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.foundation.lazy.grid.LazyGridState
import com.tazzzo.app.ui.common.ProductFiltersSaver
import androidx.compose.ui.platform.testTag
import com.tazzzo.app.theme.TazColors
import com.tazzzo.app.ui.common.TazIcon
import com.tazzzo.app.theme.TazType
import com.tazzzo.app.theme.TazSize
import com.tazzzo.app.theme.TazIcons
import com.tazzzo.app.theme.TazRadius
import com.tazzzo.app.theme.TazSpace
import com.tazzzo.app.ui.common.CartBar
import com.tazzzo.app.ui.common.EmojiBox
import com.tazzzo.app.ui.common.EmptyState
import com.tazzzo.app.ui.common.FilterBar
import com.tazzzo.app.ui.common.MicButton
import com.tazzzo.app.ui.common.ProductCard
import com.tazzzo.app.ui.common.ProductFilters
import com.tazzzo.app.ui.common.SortSheet
import com.tazzzo.app.ui.common.applyFilters
import com.tazzzo.app.ui.common.ProductCardSkeleton
import com.tazzzo.app.ui.common.StateHost
import com.tazzzo.app.ui.common.TazTopBar
import com.tazzzo.app.ui.state.UiState
import com.tazzzo.app.ui.state.rememberLoad

// --- two-pane geometry -------------------------------------------------------
private val SidebarWidth = 88.dp
private val SidebarWellSize = 52.dp
private val SidebarIndicatorWidth = 3.dp
private val SidebarIndicatorHeight = 28.dp
private val Hairline = 1.dp

/**
 * Blinkit-style two-pane category browser: subcategory rail on the left,
 * product grid on the right.
 */
@Composable
fun CategoryDetailScreen(categoryId: String, initialSubcategoryId: String?) {
    val app = LocalAppState.current
    // Taxonomy comes from the repository, not from the fixtures. Every route
    // into this screen (home tab, categories tab, search) has already warmed
    // the taxonomy cache, so the title and rail normally paint on first frame;
    // the load below is the cold-start path.
    val taxonomy = rememberLoad(categoryId) {
        Taxonomy.categories().find { it.id == categoryId }
    }
    val category = (taxonomy.state as? UiState.Success)?.data
        ?: Taxonomy.cachedCategory(categoryId)

    // rememberSaveable, not remember: these are the customer's context. Opening
    // a product and coming back must not silently reset the aisle they chose or
    // the filters they set.
    var selectedSub by rememberSaveable { mutableStateOf(initialSubcategoryId) } // null = All
    var filters by rememberSaveable(stateSaver = ProductFiltersSaver) {
        mutableStateOf(ProductFilters())
    }
    // The sort sheet is transient UI, not context — it should NOT come back.
    var showSort by remember { mutableStateOf(false) }
    // Grid scroll position, keyed per aisle so switching subcategory starts at
    // the top but returning from a product does not.
    val gridState = rememberSaveable(selectedSub, saver = LazyGridState.Saver) { LazyGridState() }
    val hairlinePx = with(LocalDensity.current) { Hairline.toPx() }

    // Keyed on the selected subcategory: switching aisles re-runs the load and
    // gives loading / empty / error / retry for free.
    val products = rememberLoad(categoryId, selectedSub) {
        ServiceLocator.catalog.getProducts(categoryId, selectedSub)
    }

    Box(Modifier.fillMaxSize().background(TazColors.Cream)) {
        Column(Modifier.fillMaxSize()) {
            TazTopBar(
                title = category?.name ?: "",
                onBack = { app.back() },
                trailing = {
                    // 44dp minimum touch target + a label for screen readers.
                    Box(
                        Modifier.defaultMinSize(minWidth = 44.dp, minHeight = 44.dp)
                            .tazPressable(onClick = { app.navigate(Screen.Search) }, pressScale = TazPress.compact)
                            .semantics { contentDescription = "Search" },
                        contentAlignment = Alignment.Center
                    ) {
                        TazIcon(TazIcons.Search, null, size = TazSize.iconSm, tint = TazColors.TextPrimary)
                    }
                    Spacer(Modifier.width(8.dp))
                    MicButton(30.dp) { app.showVoiceSheet = true }
                }
            )

            Row(Modifier.weight(1f)) {
                // ----- LEFT: subcategory sidebar -----
                // Its own surface with a hairline edge, so the rail reads as a
                // deliberate pane rather than as unstyled space beside the grid.
                LazyColumn(
                    Modifier.width(SidebarWidth).fillMaxHeight()
                        .background(TazColors.Surface)
                        .drawBehind {
                            val x = size.width - hairlinePx / 2f
                            drawLine(
                                color = TazColors.CardBorder,
                                start = Offset(x, 0f),
                                end = Offset(x, size.height),
                                strokeWidth = hairlinePx
                            )
                        }
                ) {
                    if (category != null) {
                        item {
                            SidebarEntry(
                                emoji = category.emoji,
                                name = "All",
                                tint = Color(category.tint),
                                selected = selectedSub == null,
                                onClick = { selectedSub = null }
                            )
                        }
                        items(
                            category.subcategories,
                            key = { it.id },
                            contentType = { "sidebarEntry" }
                        ) { sub ->
                            SidebarEntry(
                                emoji = sub.emoji,
                                name = sub.name,
                                tint = Color(category.tint),
                                selected = selectedSub == sub.id,
                                onClick = { selectedSub = sub.id }
                            )
                        }
                    }
                }

                // ----- RIGHT: product grid -----
                StateHost(
                    handle = products,
                    modifier = Modifier.weight(1f).fillMaxHeight(),
                    loading = { ProductGridSkeleton() },
                    empty = {
                        EmptyState(
                            emoji = "🧺",
                            title = "Nothing here yet",
                            body = "Try another aisle from the list on the left."
                        )
                    }
                ) { list ->
                    val brands = remember(list) { list.map { it.brand }.distinct().sorted() }
                    val filtered = list.applyFilters(filters)
                    Column(Modifier.fillMaxSize()) {
                        // Context strip: the count is derived from the loaded list,
                        // so it only ever renders once there is real data behind it.
                        Row(
                            Modifier.fillMaxWidth()
                                .background(TazColors.Surface)
                                .drawBehind {
                                    val y = size.height - hairlinePx / 2f
                                    drawLine(
                                        color = TazColors.CardBorder,
                                        start = Offset(0f, y),
                                        end = Offset(size.width, y),
                                        strokeWidth = hairlinePx
                                    )
                                },
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                if (filtered.size == 1) "1 item" else "${filtered.size} items",
                                fontSize = TazType.captionSize,
                                lineHeight = TazType.captionLine,
                                color = TazColors.TextTertiary,
                                maxLines = 1,
                                modifier = Modifier.padding(start = TazSpace.md)
                            )
                            Spacer(Modifier.width(TazSpace.sm))
                            FilterBar(
                                filters = filters,
                                brands = brands,
                                onChange = { filters = it },
                                onOpenSort = { showSort = true },
                                modifier = Modifier.weight(1f)
                            )
                        }
                        if (filtered.isEmpty() && list.isNotEmpty()) {
                            EmptyState(
                                emoji = "🔍",
                                title = "No products match",
                                body = "Try removing some filters.",
                                actionLabel = "Clear filters",
                                onAction = { filters = ProductFilters() }
                            )
                        } else {
                            LazyVerticalGrid(
                                // Test tag so the journey suite can drive this
                                // grid unambiguously — the screen has two
                                // scrollable regions (aisle rail + product grid)
                                // and "the scrollable one" is not a selector.
                                modifier = Modifier.fillMaxSize().testTag("categoryGrid"),
                                state = gridState,
                                columns = GridCells.Fixed(2),
                                contentPadding = PaddingValues(
                                    start = TazSpace.md, top = TazSpace.md, end = TazSpace.md,
                                    bottom = TazSpace.cartBarClearance
                                ),
                                horizontalArrangement = Arrangement.spacedBy(TazSpace.md),
                                verticalArrangement = Arrangement.spacedBy(TazSpace.md)
                            ) {
                                items(
                                    filtered,
                                    key = { it.id },
                                    // Uniform item type lets Compose reuse the
                                    // card's composition and layout node instead
                                    // of rebuilding one per row while scrolling.
                                    contentType = { "productCard" }
                                ) { p ->
                                    ProductCard(p, Modifier.fillMaxWidth())
                                }
                            }
                        }
                    }
                }
            }
        }
        CartBar()
    }

    if (showSort) {
        SortSheet(
            current = filters.sort,
            onSelect = { filters = filters.copy(sort = it) },
            onDismiss = { showSort = false }
        )
    }
}

/** Two-column card silhouette, matching the grid it is standing in for. */
@Composable
private fun ProductGridSkeleton() {
    Column(
        Modifier.fillMaxSize().padding(TazSpace.md),
        verticalArrangement = Arrangement.spacedBy(TazSpace.md)
    ) {
        repeat(3) {
            Row(horizontalArrangement = Arrangement.spacedBy(TazSpace.md)) {
                repeat(2) { ProductCardSkeleton() }
            }
        }
    }
}

@Composable
private fun SidebarEntry(
    emoji: String,
    name: String,
    tint: Color,
    selected: Boolean,
    onClick: () -> Unit
) {
    Box(
        Modifier.fillMaxWidth().tazPressable(onClick = { onClick() }, pressScale = TazPress.compact),
        contentAlignment = Alignment.CenterStart
    ) {
        Column(
            Modifier.fillMaxWidth().padding(vertical = TazSpace.sm, horizontal = TazSpace.sm),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            EmojiBox(
                emoji = emoji,
                fontSize = 22.sp,
                bg = if (selected) TazColors.GreenSoft else TazColors.SurfaceSunken,
                modifier = Modifier.size(SidebarWellSize),
                corner = TazRadius.tileDp
            )
            Spacer(Modifier.height(TazSpace.xs))
            Text(
                name,
                fontSize = TazType.microSize,
                lineHeight = TazType.microLine,
                fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Medium,
                color = if (selected) TazColors.TextPrimary else TazColors.TextTertiary,
                textAlign = TextAlign.Center,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
        }
        // Selection marker sits on the pane edge, vertically centred on the entry.
        if (selected) {
            Box(
                Modifier.width(SidebarIndicatorWidth).height(SidebarIndicatorHeight)
                    .clip(TazRadius.pill)
                    .background(TazColors.Green)
            )
        }
    }
}
