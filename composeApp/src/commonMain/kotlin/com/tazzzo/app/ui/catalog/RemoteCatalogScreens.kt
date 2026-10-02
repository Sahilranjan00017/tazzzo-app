package com.tazzzo.app.ui.catalog

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.disabled
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.tazzzo.app.LocalAppState
import com.tazzzo.app.Screen
import com.tazzzo.app.data.catalog.AppendState
import com.tazzzo.app.data.catalog.CatalogNode
import com.tazzzo.app.data.catalog.CatalogProduct
import com.tazzzo.app.data.catalog.NodesState
import com.tazzzo.app.data.catalog.PagedState
import com.tazzzo.app.data.catalog.PdpState
import com.tazzzo.app.data.catalog.PurchaseAction
import com.tazzzo.app.data.catalog.StockTone
import com.tazzzo.app.data.catalog.TaxonomyBrowser
import com.tazzzo.app.data.catalog.discountPercentLabel
import com.tazzzo.app.data.catalog.displayValue
import com.tazzzo.app.data.catalog.mrpLabel
import com.tazzzo.app.data.catalog.priceLabel
import com.tazzzo.app.data.catalog.productDetailHolder
import com.tazzzo.app.data.catalog.productListHolder
import com.tazzzo.app.data.catalog.purchaseAction
import com.tazzzo.app.data.catalog.savingLabel
import com.tazzzo.app.data.catalog.stockLabel
import com.tazzzo.app.data.repository.ServiceLocator
import com.tazzzo.app.theme.TazColors
import com.tazzzo.app.theme.TazIcons
import com.tazzzo.app.theme.TazRadius
import com.tazzzo.app.theme.TazSize
import com.tazzzo.app.theme.TazSpace
import com.tazzzo.app.theme.TazType
import com.tazzzo.app.ui.common.ChipTone
import com.tazzzo.app.ui.common.EmptyState
import com.tazzzo.app.ui.common.LogoImage
import com.tazzzo.app.ui.common.PillButton
import com.tazzzo.app.ui.common.SkeletonBlock
import com.tazzzo.app.ui.common.TazChip
import com.tazzzo.app.ui.common.TazIcon
import com.tazzzo.app.ui.common.TazTopBar
import com.tazzzo.app.ui.interaction.TazPress
import com.tazzzo.app.ui.interaction.tazPressable

/*
 * REAL-catalogue screens (PR-04C): Home entry, Categories, product list (PLP) and product page
 * (PDP). They observe state holders — never a data source — and render only what the backend sent:
 * no counts, no ratings, no ETA, no invented copy. Layout reuses existing tokens/components; the
 * approved visual design is a separate PR.
 */

// ---------------------------------------------------------------------------------------------------
// Taxonomy: sections (TZS) -> categories (TZC), children loaded lazily
// ---------------------------------------------------------------------------------------------------

@Composable
private fun rememberTaxonomyBrowser(): TaxonomyBrowser {
    val scope = rememberCoroutineScope()
    return remember { TaxonomyBrowser(scope, ServiceLocator.remoteCatalog) }
}

@Composable
private fun RemoteTaxonomyList(browser: TaxonomyBrowser, header: @Composable () -> Unit, onOpen: (CatalogNode) -> Unit) {
    val root by browser.root.collectAsState()
    val children by browser.children.collectAsState()
    var expanded by rememberSaveable { mutableStateOf("") }          // comma-separated section ids
    var rootFailures by remember { mutableStateOf(0) }
    LaunchedEffect(Unit) { browser.ensureRoot() }
    val open = expanded.split(',').filter { it.isNotEmpty() }.toSet()

    LazyColumn(Modifier.fillMaxSize().testTag("remoteTaxonomy"), contentPadding = PaddingValues(bottom = TazSpace.xxxl)) {
        item { header() }
        when (val r = root) {
            NodesState.Idle, NodesState.Loading -> items(5) { SkeletonRow() }
            is NodesState.Failed -> item {
                FailurePanel(r.failure, rootFailures + 1, onRetry = { rootFailures++; browser.retryRoot() })
            }
            is NodesState.Loaded ->
                if (r.items.isEmpty()) item {
                    EmptyState("📦", "No categories yet", "Check back soon.")
                } else r.items.forEach { section ->
                    val isOpen = section.id in open
                    item(key = section.id) {
                        SectionHeader(section.name, isOpen) {
                            expanded = (if (isOpen) open - section.id else open + section.id).joinToString(",")
                            if (!isOpen) browser.ensureChildren(section.id)   // lazy: only when asked for
                        }
                    }
                    if (isOpen) when (val c = children[section.id]) {
                        null, NodesState.Idle, NodesState.Loading -> items(3, key = { "${section.id}-sk$it" }) { SkeletonRow() }
                        is NodesState.Failed -> item(key = "${section.id}-err") {
                            FailurePanel(c.failure, 1, onRetry = { browser.retryChildren(section.id) }, compact = true)
                        }
                        is NodesState.Loaded ->
                            if (c.items.isEmpty()) item(key = "${section.id}-empty") {
                                Text(
                                    "Nothing here yet.", fontSize = TazType.captionSize, color = TazColors.TextTertiary,
                                    modifier = Modifier.padding(horizontal = TazSpace.xxl, vertical = TazSpace.md)
                                )
                            } else items(c.items, key = { it.id }) { CategoryRow(it) { onOpen(it) } }
                    }
                }
        }
    }
}

@Composable
private fun SectionHeader(name: String, open: Boolean, onToggle: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().defaultMinSize(minHeight = TazSize.touchTarget)
            .tazPressable(onClick = onToggle, pressScale = TazPress.compact)
            .padding(horizontal = TazSpace.lg, vertical = TazSpace.md),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(name, fontSize = TazType.titleSize, fontWeight = TazType.titleWeight, color = TazColors.TextPrimary, modifier = Modifier.weight(1f))
        Text(if (open) "Hide" else "Show", fontSize = TazType.captionSize, fontWeight = FontWeight.SemiBold, color = TazColors.Green)
    }
}

@Composable
private fun CategoryRow(node: CatalogNode, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().defaultMinSize(minHeight = TazSize.touchTarget)
            .background(TazColors.Surface)
            .tazPressable(onClick = onClick, pressScale = TazPress.compact)
            .padding(horizontal = TazSpace.lg, vertical = TazSpace.sm),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Neutral tile: the backend has no category imagery and none is invented here.
        NeutralPlaceholder(Modifier.size(44.dp).clip(TazRadius.chip), iconSize = 22.dp)
        Spacer(Modifier.width(TazSpace.md))
        Text(node.name, fontSize = TazType.bodySize, color = TazColors.TextPrimary, modifier = Modifier.weight(1f))
    }
}

@Composable
private fun SkeletonRow() {
    Row(Modifier.fillMaxWidth().padding(horizontal = TazSpace.lg, vertical = TazSpace.sm), verticalAlignment = Alignment.CenterVertically) {
        SkeletonBlock(width = 44.dp, height = 44.dp, corner = 8.dp)
        Spacer(Modifier.width(TazSpace.md))
        SkeletonBlock(width = 160.dp, height = 14.dp)
    }
}

/** Home in REMOTE mode: delivery status + real category navigation. Unsupported rails are not shown. */
@Composable
fun RemoteHomeContent() {
    val app = LocalAppState.current
    val browser = rememberTaxonomyBrowser()
    Column(Modifier.fillMaxSize().background(TazColors.Cream)) {
        Row(Modifier.fillMaxWidth().background(TazColors.Surface).padding(horizontal = TazSpace.lg, vertical = TazSpace.md), verticalAlignment = Alignment.CenterVertically) {
            LogoImage(32.dp)
        }
        ServiceabilityBannerView()
        com.tazzzo.app.ui.address.DeliverySuggestionBanner()
        RemoteTaxonomyList(
            browser,
            header = {
                Text(
                    "Shop by category", fontSize = TazType.h2Size, fontWeight = TazType.h2Weight, color = TazColors.TextPrimary,
                    modifier = Modifier.padding(start = TazSpace.lg, top = TazSpace.lg, end = TazSpace.lg, bottom = TazSpace.xs)
                )
            },
            onOpen = { app.navigate(Screen.CategoryDetail(it.id)) }
        )
    }
}

/** The Categories tab in REMOTE mode. */
@Composable
fun RemoteCategoriesContent() {
    val app = LocalAppState.current
    val browser = rememberTaxonomyBrowser()
    Column(Modifier.fillMaxSize().background(TazColors.Cream)) {
        TazTopBar("Categories")
        ServiceabilityBannerView()
        RemoteTaxonomyList(browser, header = {}, onOpen = { app.navigate(Screen.CategoryDetail(it.id)) })
    }
}

// ---------------------------------------------------------------------------------------------------
// PLP
// ---------------------------------------------------------------------------------------------------

@Composable
fun RemoteCategoryScreen(categoryId: String, initialSubcategoryId: String?) {
    val app = LocalAppState.current
    val scope = rememberCoroutineScope()
    val reader = ServiceLocator.remoteCatalog
    val pin by ServiceLocator.launchContext.pin.collectAsState()
    val holder = remember(categoryId) { productListHolder(scope, reader, ServiceLocator.launchContext.pin) }
    val subs = remember(categoryId) { TaxonomyBrowser(scope, reader) }
    var selected by rememberSaveable(categoryId) { mutableStateOf(initialSubcategoryId) }   // null = the category itself ("All")
    val node = selected ?: categoryId
    LaunchedEffect(categoryId) { subs.ensureChildren(categoryId) }
    LaunchedEffect(node) { holder.open(node) }

    val state by holder.state.collectAsState()
    val subState by subs.children.collectAsState()
    var failures by remember(categoryId, node, pin) { mutableStateOf(0) }
    val gridState = rememberSaveable(node, pin.value, saver = LazyGridState.Saver) { LazyGridState() }

    // Pagination: ask for more when the last visible item is within a few of the end.
    val total = (state as? PagedState.Content<*>)?.items?.size ?: 0
    LaunchedEffect(gridState, total) {
        snapshotFlow { gridState.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: -1 }
            .collect { last -> if (total > 0 && last >= total - PREFETCH_DISTANCE) holder.loadMore() }
    }

    Column(Modifier.fillMaxSize().background(TazColors.Cream)) {
        TazTopBar(title = reader.taxonomy.nameOf(categoryId) ?: "Products", onBack = { app.back() })
        ServiceabilityBannerView()
        (subState[categoryId] as? NodesState.Loaded)?.items?.takeIf { it.isNotEmpty() }?.let { subList ->
            LazyRow(
                Modifier.fillMaxWidth().background(TazColors.Surface).testTag("subcategories"),
                contentPadding = PaddingValues(horizontal = TazSpace.lg, vertical = TazSpace.sm),
                horizontalArrangement = Arrangement.spacedBy(TazSpace.sm)
            ) {
                item { SubChip("All", selected == null) { selected = null } }
                items(subList, key = { it.id }) { SubChip(it.name, selected == it.id) { selected = it.id } }
            }
        }
        Box(Modifier.weight(1f).fillMaxWidth()) {
            when (val s = state) {
                PagedState.Idle, PagedState.LoadingFirst -> ProductGridSkeleton()
                is PagedState.FirstPageFailed ->
                    FailurePanel(s.failure, failures + 1, onRetry = { failures++; holder.refresh() })
                PagedState.Empty ->
                    EmptyState("📦", "Nothing here yet", "There are no products in this section right now.")
                is PagedState.Content -> {
                    LaunchedEffect(Unit) { failures = 0 }
                    LazyVerticalGrid(
                        modifier = Modifier.fillMaxSize().testTag("remoteProductGrid"),
                        state = gridState,
                        columns = GridCells.Fixed(2),
                        contentPadding = PaddingValues(TazSpace.md),
                        horizontalArrangement = Arrangement.spacedBy(TazSpace.md),
                        verticalArrangement = Arrangement.spacedBy(TazSpace.md)
                    ) {
                        // skuId is the list identity (a row is a SKU); productId is only for navigation.
                        items(s.items, key = { it.skuId }) { p ->
                            RemoteProductCard(p) { app.navigate(Screen.ProductDetail(p.productId)) }
                        }
                        item(span = { GridItemSpan(maxLineSpan) }, key = "footer") {
                            ListFooter(s, onRetry = { failures++; holder.retryAppend() }, failures = failures)
                        }
                    }
                }
            }
        }
    }
}

private const val PREFETCH_DISTANCE = 6

@Composable
private fun SubChip(label: String, selected: Boolean, onClick: () -> Unit) {
    Text(
        label, fontSize = TazType.captionSize, fontWeight = FontWeight.SemiBold,
        color = if (selected) TazColors.Surface else TazColors.TextPrimary,
        modifier = Modifier.clip(TazRadius.chip)
            .background(if (selected) TazColors.Green else TazColors.SurfaceSunken)
            .tazPressable(onClick = onClick, pressScale = TazPress.compact, selected = selected)
            .padding(horizontal = TazSpace.md, vertical = TazSpace.sm)
    )
}

@Composable
private fun ListFooter(s: PagedState.Content<CatalogProduct>, onRetry: () -> Unit, failures: Int) {
    when (val a = s.append) {
        AppendState.Loading -> Box(Modifier.fillMaxWidth().padding(TazSpace.lg), contentAlignment = Alignment.Center) {
            androidx.compose.material3.CircularProgressIndicator(color = TazColors.Green, modifier = Modifier.size(24.dp))
        }
        is AppendState.Failed -> FailurePanel(a.failure, failures.coerceAtLeast(1), onRetry, compact = true)
        AppendState.Idle ->
            if (!s.hasMore) Text(
                "You've seen everything here", fontSize = TazType.captionSize, color = TazColors.TextTertiary,
                textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth().padding(TazSpace.lg)
            )
    }
}

@Composable
private fun ProductGridSkeleton() {
    LazyVerticalGrid(
        modifier = Modifier.fillMaxSize(), columns = GridCells.Fixed(2), userScrollEnabled = false,
        contentPadding = PaddingValues(TazSpace.md),
        horizontalArrangement = Arrangement.spacedBy(TazSpace.md), verticalArrangement = Arrangement.spacedBy(TazSpace.md)
    ) {
        items(6) {
            Column(Modifier.clip(TazRadius.card).background(TazColors.Surface)) {
                Box(Modifier.fillMaxWidth().aspectRatio(1f).background(TazColors.SurfaceSunken))
                Column(Modifier.padding(TazSpace.md), verticalArrangement = Arrangement.spacedBy(TazSpace.sm)) {
                    SkeletonBlock(height = 13.dp); SkeletonBlock(width = 62.dp, height = 11.dp); SkeletonBlock(width = 44.dp, height = 15.dp)
                }
            }
        }
    }
}

@Composable
fun RemoteProductCard(product: CatalogProduct, onClick: () -> Unit) {
    val action = purchaseAction(product, ServiceLocator.catalogCapabilities)
    Column(
        Modifier.fillMaxWidth().clip(TazRadius.card).background(TazColors.Surface)
            .tazPressable(onClick = onClick, pressScale = TazPress.compact)
            .semantics { contentDescription = product.name }
    ) {
        NeutralPlaceholder(Modifier.fillMaxWidth().aspectRatio(1f))
        Column(Modifier.padding(TazSpace.md), verticalArrangement = Arrangement.spacedBy(TazSpace.xs)) {
            Text(product.name, fontSize = TazType.productNameSize, fontWeight = FontWeight.Medium, color = TazColors.TextPrimary, maxLines = 2, overflow = TextOverflow.Ellipsis)
            PriceLine(product, compact = true)
            StockText(product)
            if (action is PurchaseAction.Disabled) DisabledPurchase(action.label, compact = true)
        }
    }
}

// ---------------------------------------------------------------------------------------------------
// PDP
// ---------------------------------------------------------------------------------------------------

@Composable
fun RemoteProductDetailScreen(productId: String) {
    val app = LocalAppState.current
    val scope = rememberCoroutineScope()
    val holder = remember(productId) { productDetailHolder(scope, ServiceLocator.remoteCatalog, ServiceLocator.launchContext.pin) }
    LaunchedEffect(productId) { holder.open(productId) }
    val state by holder.state.collectAsState()
    var failures by remember(productId) { mutableStateOf(0) }

    Column(Modifier.fillMaxSize().background(TazColors.Cream)) {
        TazTopBar(title = "", onBack = { app.back() })
        ServiceabilityBannerView()
        Box(Modifier.weight(1f).fillMaxWidth()) {
            when (val s = state) {
                PdpState.Idle, PdpState.Loading -> Column(Modifier.padding(TazSpace.lg), verticalArrangement = Arrangement.spacedBy(TazSpace.md)) {
                    SkeletonBlock(height = 220.dp, corner = 14.dp); SkeletonBlock(height = 18.dp); SkeletonBlock(width = 120.dp, height = 18.dp)
                }
                PdpState.NotFound -> EmptyState("📦", "This product isn't available", "It may have been removed or isn't sold in your area.", "Go back", onAction = { app.back() })
                is PdpState.Failed -> FailurePanel(s.failure, failures + 1, onRetry = { failures++; holder.retry() })
                is PdpState.Content -> {
                    LaunchedEffect(Unit) { failures = 0 }
                    val p = s.detail.product
                    val action = purchaseAction(p, ServiceLocator.catalogCapabilities)
                    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(TazSpace.lg), verticalArrangement = Arrangement.spacedBy(TazSpace.md)) {
                        // Neutral placeholder: the gallery URLs are kept in state; image loading is a later PR.
                        NeutralPlaceholder(Modifier.fillMaxWidth().aspectRatio(1f).clip(TazRadius.card), iconSize = 48.dp)
                        Text(p.name, fontSize = TazType.h2Size, fontWeight = TazType.h2Weight, color = TazColors.TextPrimary)
                        PriceLine(p, compact = false)
                        StockText(p)
                        if (action is PurchaseAction.Disabled) DisabledPurchase(action.label, compact = false)
                        val attrs = s.detail.attributes.mapNotNull { a -> a.displayValue()?.let { a.label to it } }
                        if (attrs.isNotEmpty()) {
                            Column(Modifier.fillMaxWidth().clip(TazRadius.card).background(TazColors.Surface).padding(TazSpace.lg), verticalArrangement = Arrangement.spacedBy(TazSpace.sm)) {
                                Text("Details", fontSize = TazType.titleSize, fontWeight = TazType.titleWeight, color = TazColors.TextPrimary)
                                attrs.forEach { (label, value) ->
                                    Row(Modifier.fillMaxWidth()) {
                                        Text(label, fontSize = TazType.bodySize, color = TazColors.TextSecondary, modifier = Modifier.weight(1f))
                                        Text(value, fontSize = TazType.bodySize, color = TazColors.TextPrimary, textAlign = TextAlign.End)
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

// ---------------------------------------------------------------------------------------------------
// Shared pieces
// ---------------------------------------------------------------------------------------------------

/** Price exactly as the server gave it. No price -> "Price unavailable", never `₹0`; discounts are the server's, never recomputed. */
@Composable
private fun PriceLine(p: CatalogProduct, compact: Boolean) {
    val price = p.priceLabel()
    Column(verticalArrangement = Arrangement.spacedBy(TazSpace.xxs)) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(TazSpace.sm)) {
            if (price != null) {
                Text(price, fontSize = if (compact) TazType.priceSize else TazType.priceHeroSize, fontWeight = TazType.priceWeight, color = TazColors.TextPrimary)
                p.mrpLabel()?.let { Text(it, fontSize = TazType.mrpSize, color = TazColors.TextTertiary, textDecoration = TextDecoration.LineThrough) }
            } else {
                Text("Price unavailable", fontSize = if (compact) TazType.captionSize else TazType.bodySize, color = TazColors.TextTertiary)
            }
        }
        p.discountPercentLabel()?.let { TazChip(it, ChipTone.Savings) }
        if (!compact) p.savingLabel()?.let { Text(it, fontSize = TazType.savingsSize, color = TazColors.Success) }
    }
}

/** UNKNOWN is worded and coloured differently from OUT_OF_STOCK. */
@Composable
private fun StockText(p: CatalogProduct) {
    val l = p.stockLabel()
    val ink = when (l.tone) {
        StockTone.Available -> TazColors.Success
        StockTone.Scarce -> TazColors.Warning
        StockTone.Unavailable -> TazColors.Danger
        StockTone.Unknown -> TazColors.TextTertiary
    }
    Text(l.text, fontSize = TazType.captionSize, fontWeight = FontWeight.SemiBold, color = ink)
}

/** Neutral, non-interactive: a real product never reaches the local/mock cart. */
@Composable
private fun DisabledPurchase(label: String, compact: Boolean) {
    Box(
        Modifier.fillMaxWidth().clip(TazRadius.chip).background(TazColors.SurfaceSunken)
            .padding(vertical = if (compact) TazSpace.xs else TazSpace.md, horizontal = TazSpace.sm)
            .semantics { disabled(); contentDescription = label },
        contentAlignment = Alignment.Center
    ) {
        Text(label, fontSize = TazType.captionSize, fontWeight = FontWeight.SemiBold, color = TazColors.TextTertiary, textAlign = TextAlign.Center)
    }
}

/** A surface that has no backend source in REMOTE mode (search, master list). Never fed from mock data. */
@Composable
fun UnavailableSurface(name: String) {
    val app = LocalAppState.current
    Column(Modifier.fillMaxSize().background(TazColors.Cream)) {
        TazTopBar(title = name, onBack = { app.back() })
        EmptyState("📦", "$name isn't available yet", "We're working on it.", "Go back", onAction = { app.back() })
    }
}
