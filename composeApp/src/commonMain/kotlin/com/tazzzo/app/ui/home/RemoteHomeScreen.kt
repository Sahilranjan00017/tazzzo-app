package com.tazzzo.app.ui.home

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowForward
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.tazzzo.app.HomeTab
import com.tazzzo.app.LocalAppState
import com.tazzzo.app.Screen
import com.tazzzo.app.data.address.BookState
import com.tazzzo.app.data.catalog.CatalogNode
import com.tazzzo.app.data.catalog.CatalogProduct
import com.tazzzo.app.data.catalog.NodesState
import com.tazzzo.app.data.catalog.PagedState
import com.tazzzo.app.data.catalog.discountPercentLabel
import com.tazzzo.app.data.catalog.mrpLabel
import com.tazzzo.app.data.catalog.priceLabel
import com.tazzzo.app.data.catalog.productListHolder
import com.tazzzo.app.data.repository.ServiceLocator
import com.tazzzo.app.theme.TazColors
import com.tazzzo.app.theme.TazIcons
import com.tazzzo.app.theme.TazRadius
import com.tazzzo.app.theme.TazSize
import com.tazzzo.app.theme.TazSpace
import com.tazzzo.app.theme.TazType
import com.tazzzo.app.theme.tazEditorialFamily
import com.tazzzo.app.ui.address.DeliverySuggestionBanner
import com.tazzzo.app.ui.catalog.TazProductCard
import com.tazzzo.app.ui.common.TazSearchShell
import com.tazzzo.app.ui.catalog.ServiceabilityBannerView
import com.tazzzo.app.ui.catalog.rememberTaxonomyBrowser
import com.tazzzo.app.ui.common.EditorialText
import com.tazzzo.app.ui.common.SkeletonBlock
import com.tazzzo.app.ui.common.italic
import com.tazzzo.app.ui.common.plain
import com.tazzzo.app.ui.interaction.TazPress
import com.tazzzo.app.ui.interaction.tazPressable
import org.jetbrains.compose.resources.DrawableResource
import org.jetbrains.compose.resources.painterResource
import tazzzo.resources.Res
import tazzzo.resources.bg_home_bulk
import tazzzo.resources.bg_home_hero
import tazzzo.resources.bg_home_quality

/**
 * REMOTE Home, built to the UI Page reference `Home.jpeg`: location header with the server-cart badge, rounded search
 * shell, editorial hero, circular categories from the REAL taxonomy, the quality banner, a product rail from the REAL
 * catalogue (never called a recommendation), and the dark-green bulk band. Every photo layer is a plate derived from the
 * reference; every control is native. Nothing here fakes slots, discounts, pack sizes or personalisation.
 */
@Composable
fun RemoteHomeContent() {
    val app = LocalAppState.current
    val scope = rememberCoroutineScope()
    val browser = rememberTaxonomyBrowser()
    val root by browser.root.collectAsState()
    LaunchedEffect(Unit) { browser.ensureRoot() }

    // The rail: the first root category's first page — deterministic catalogue data, surfaced under a neutral title.
    val reader = ServiceLocator.remoteCatalog
    val rail = remember { productListHolder(scope, reader, ServiceLocator.launchContext.pin) }
    val firstRoot = (root as? NodesState.Loaded)?.items?.firstOrNull()
    LaunchedEffect(firstRoot?.id) { firstRoot?.let { rail.open(it.id) } }
    val railState by rail.state.collectAsState()

    val selectedId by ServiceLocator.deliveryLocation.selectedAddressId.collectAsState()
    val book by ServiceLocator.addressBook.state.collectAsState()
    val pin by ServiceLocator.launchContext.pin.collectAsState()
    val serviceability by ServiceLocator.launchContext.state.collectAsState()
    val cartState by ServiceLocator.cart.state.collectAsState()
    val selected = (book as? BookState.Loaded)?.addresses?.firstOrNull { it.addressId == selectedId }

    HomeScreenLayout(
        header = HomeHeaderData(homeLocationLabel(selected, pin), deliveryStatusLine(serviceability, pin), cartBadgeCount(cartState)),
        root = root, rail = railState,
        banners = { ServiceabilityBannerView(); DeliverySuggestionBanner() },
        actions = HomeActions(
            openLocation = { app.navigate(Screen.Addresses) }, openCart = { app.navigate(Screen.Cart) }, openSearch = { app.navigate(Screen.Search) },
            openShop = { app.homeTab = HomeTab.SHOP }, openCategory = { app.navigate(Screen.CategoryDetail(it.id)) },
            openProduct = { app.navigate(Screen.ProductDetail(it.productId)) }, retryCategories = { browser.retryRoot() }
        )
    )
}

/** What the header shows: all three values come from real state (see HomeModel.kt). */
data class HomeHeaderData(val locationLabel: String, val statusLine: String?, val cartBadge: Int)

class HomeActions(
    val openLocation: () -> Unit, val openCart: () -> Unit, val openSearch: () -> Unit, val openShop: () -> Unit,
    val openCategory: (CatalogNode) -> Unit, val openProduct: (CatalogProduct) -> Unit, val retryCategories: () -> Unit
)

/** The reference layout, independent of where its data comes from (so evidence/tests can render it with sample state). */
@Composable
fun HomeScreenLayout(header: HomeHeaderData, root: NodesState, rail: PagedState<CatalogProduct>, actions: HomeActions, banners: @Composable () -> Unit = {}) {
    LazyColumn(Modifier.fillMaxSize().background(TazColors.Cream)) {
        item { HomeHeader(header, actions.openLocation, actions.openCart) }
        item { banners() }
        item { HomeSearchShell(onClick = actions.openSearch) }
        item { HomeHero(onShop = actions.openShop) }
        item { HomeCategoryRow(root, onOpen = actions.openCategory, onRetry = actions.retryCategories) }
        item { HomeQualityBanner(onShop = actions.openShop) }
        item { HomeProductRail(rail, onSeeAll = actions.openShop, onOpen = actions.openProduct) }
        item { HomeBulkBand(onShop = actions.openShop) }
        item { Spacer(Modifier.height(TazSize.floatingNavClearance)) }
    }
}

// ---- header ---------------------------------------------------------------------------------------------------------

@Composable
private fun HomeHeader(data: HomeHeaderData, onLocation: () -> Unit, onCart: () -> Unit) {
    val status = data.statusLine
    val badge = data.cartBadge

    Row(
        Modifier.fillMaxWidth().statusBarsPadding().padding(start = TazSpace.lg, end = TazSpace.lg, top = TazSpace.sm, bottom = TazSpace.xs),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(
            Modifier.weight(1f).clip(TazRadius.chip)
                .tazPressable(onClick = onLocation, pressScale = TazPress.compact)
                .semantics { contentDescription = "Delivery location" }
                .padding(vertical = TazSpace.xs)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(TazIcons.Location, contentDescription = null, tint = TazColors.BrandEditorial, modifier = Modifier.size(22.dp))
                Spacer(Modifier.width(TazSpace.sm))
                Text(data.locationLabel, fontFamily = tazEditorialFamily(), fontSize = 22.sp, color = TazColors.BrandEditorial, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Spacer(Modifier.width(TazSpace.xs))
                Icon(TazIcons.ChevronDown, contentDescription = null, tint = TazColors.BrandEditorial, modifier = Modifier.size(18.dp))
            }
            // The reference's second line is a delivery slot; we have none. The line keeps its height and shows what IS known.
            Box(Modifier.height(18.dp).padding(start = 30.dp), contentAlignment = Alignment.CenterStart) {
                status?.let { Text(it, fontSize = TazType.captionSize, color = TazColors.BrandEditorial.copy(alpha = 0.85f), maxLines = 1) }
            }
        }
        Box(
            Modifier.size(TazSize.touchTarget).clip(CircleShape)
                .tazPressable(onClick = onCart, pressScale = TazPress.compact)
                .semantics { contentDescription = if (badge > 0) "Cart, $badge items" else "Cart" },
            contentAlignment = Alignment.Center
        ) {
            Icon(TazIcons.Bag, contentDescription = null, tint = TazColors.BrandEditorial, modifier = Modifier.size(26.dp))
            if (badge > 0) {
                Box(
                    Modifier.align(Alignment.TopEnd).offset(x = 2.dp, y = (-2).dp).size(20.dp).clip(CircleShape).background(TazColors.BrandEditorial),
                    contentAlignment = Alignment.Center
                ) { Text(badge.coerceAtMost(99).toString(), fontSize = 11.sp, fontWeight = FontWeight.SemiBold, color = TazColors.White) }
            }
        }
    }
}

// ---- search ---------------------------------------------------------------------------------------------------------

@Composable
private fun HomeSearchShell(onClick: () -> Unit) {
    TazSearchShell(onClick = onClick, modifier = Modifier.padding(horizontal = TazSpace.lg, vertical = TazSpace.sm))
}

// ---- hero -----------------------------------------------------------------------------------------------------------

@Composable
private fun HomeHero(onShop: () -> Unit) {
    Box(Modifier.fillMaxWidth().aspectRatio(588f / 330f)) {
        Plate(Res.drawable.bg_home_hero, Modifier.matchParentSize(), alignment = Alignment.CenterEnd)
        Column(Modifier.fillMaxHeight().fillMaxWidth(0.56f).padding(start = TazSpace.lg, top = TazSpace.lg), verticalArrangement = Arrangement.Center) {
            Eyebrow(HomeCopy.HERO_EYEBROW, TazColors.BrandEditorial)
            Spacer(Modifier.height(TazSpace.sm))
            EditorialText(listOf(plain("Fresh\nEssentials\n"), italic("for a Better You.")), size = TazType.editorialTitleSize, lineHeight = TazType.editorialTitleLine, color = TazColors.TextPrimary, textAlign = TextAlign.Start)
            Spacer(Modifier.height(TazSpace.md))
            CompactPill(HomeCopy.HERO_CTA, onClick = onShop)
        }
    }
}

// ---- categories -----------------------------------------------------------------------------------------------------

@Composable
private fun HomeCategoryRow(root: NodesState, onOpen: (CatalogNode) -> Unit, onRetry: () -> Unit) {
    when (root) {
        NodesState.Idle, NodesState.Loading -> Row(Modifier.padding(horizontal = TazSpace.lg, vertical = TazSpace.md), horizontalArrangement = Arrangement.spacedBy(TazSpace.lg)) {
            repeat(HOME_CATEGORY_COUNT) { Column(horizontalAlignment = Alignment.CenterHorizontally) { SkeletonBlock(width = CAT_SIZE, height = CAT_SIZE, corner = CAT_SIZE / 2); Spacer(Modifier.height(TazSpace.xs)); SkeletonBlock(width = 48.dp, height = 10.dp, corner = 5.dp) } }
        }
        is NodesState.Failed -> Row(Modifier.fillMaxWidth().padding(TazSpace.lg), verticalAlignment = Alignment.CenterVertically) {
            Text("Couldn't load categories", fontSize = TazType.captionSize, color = TazColors.TextSecondary, modifier = Modifier.weight(1f))
            Text("Retry", fontSize = TazType.captionSize, fontWeight = FontWeight.SemiBold, color = TazColors.BrandEditorial,
                modifier = Modifier.clip(TazRadius.chip).tazPressable(onClick = onRetry, pressScale = TazPress.compact).padding(TazSpace.sm))
        }
        is NodesState.Loaded -> if (root.items.isNotEmpty()) LazyRow(
            contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = TazSpace.lg, vertical = TazSpace.md),
            horizontalArrangement = Arrangement.spacedBy(TazSpace.lg)
        ) {
            items(root.items.take(HOME_CATEGORY_COUNT), key = { it.id }) { node -> CategoryCircle(node, onClick = { onOpen(node) }) }
        }
    }
}

@Composable
private fun CategoryCircle(node: CatalogNode, onClick: () -> Unit) {
    val art = homeCategoryArt(node)
    Column(
        Modifier.width(CAT_SIZE + 8.dp).clip(TazRadius.card).tazPressable(onClick = onClick, pressScale = TazPress.compact).semantics { contentDescription = node.name },
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Box(Modifier.size(CAT_SIZE).clip(CircleShape).background(TazColors.SurfaceSunken), contentAlignment = Alignment.Center) {
            if (art != null) Image(painterResource(art), contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.matchParentSize())
            else Text(node.name.take(1).uppercase(), fontFamily = tazEditorialFamily(), fontSize = 26.sp, color = TazColors.BrandEditorial)   // neutral: no mismatched photo
        }
        Spacer(Modifier.height(TazSpace.xs))
        Text(node.name, fontSize = TazType.captionSize, fontWeight = FontWeight.Medium, color = TazColors.TextPrimary, maxLines = 1, overflow = TextOverflow.Ellipsis, textAlign = TextAlign.Center)
    }
}

// ---- quality banner -------------------------------------------------------------------------------------------------

@Composable
private fun HomeQualityBanner(onShop: () -> Unit) {
    Box(Modifier.padding(horizontal = TazSpace.lg, vertical = TazSpace.sm).fillMaxWidth().aspectRatio(528f / 178f).clip(TazRadius.tile)) {
        Plate(Res.drawable.bg_home_quality, Modifier.matchParentSize(), alignment = Alignment.CenterEnd)
        Column(Modifier.fillMaxHeight().fillMaxWidth(0.58f).padding(start = TazSpace.lg), verticalArrangement = Arrangement.Center) {
            Eyebrow(HomeCopy.QUALITY_EYEBROW, TazColors.BrandEditorial)
            Spacer(Modifier.height(TazSpace.xs))
            EditorialText(listOf(plain("Quality you\ncan count on.")), size = 22.sp, lineHeight = 25.sp, color = TazColors.TextPrimary, textAlign = TextAlign.Start)
            Spacer(Modifier.height(TazSpace.sm))
            CompactPill(HomeCopy.QUALITY_CTA, onClick = onShop)
        }
    }
}

// ---- product rail ---------------------------------------------------------------------------------------------------

@Composable
private fun HomeProductRail(state: PagedState<CatalogProduct>, onSeeAll: () -> Unit, onOpen: (CatalogProduct) -> Unit) {
    val items = (state as? PagedState.Content)?.items.orEmpty()
    if (state !is PagedState.LoadingFirst && items.isEmpty()) return      // nothing truthful to show: no rail, no filler
    Column(Modifier.fillMaxWidth().padding(top = TazSpace.md)) {
        Row(Modifier.fillMaxWidth().padding(horizontal = TazSpace.lg), verticalAlignment = Alignment.CenterVertically) {
            EditorialText(listOf(plain(HomeCopy.RAIL_TITLE)), size = TazType.editorialSectionSize, lineHeight = TazType.editorialSectionLine, color = TazColors.TextPrimary, textAlign = TextAlign.Start, modifier = Modifier.weight(1f))
            Row(Modifier.clip(TazRadius.chip).tazPressable(onClick = onSeeAll, pressScale = TazPress.compact).padding(TazSpace.xs), verticalAlignment = Alignment.CenterVertically) {
                Text(HomeCopy.RAIL_SEE_ALL, fontFamily = tazEditorialFamily(), fontSize = 14.sp, color = TazColors.TextPrimary)
                Spacer(Modifier.width(TazSpace.xs))
                Icon(TazIcons.Forward, contentDescription = null, tint = TazColors.TextPrimary, modifier = Modifier.size(12.dp))
            }
        }
        Spacer(Modifier.height(TazSpace.sm))
        if (items.isEmpty()) {
            Row(Modifier.padding(horizontal = TazSpace.lg), horizontalArrangement = Arrangement.spacedBy(TazSpace.md)) { repeat(3) { SkeletonBlock(width = CARD_W, height = 170.dp, corner = 16.dp) } }
        } else {
            LazyRow(contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = TazSpace.lg), horizontalArrangement = Arrangement.spacedBy(TazSpace.md)) {
                // The canonical card (ui/catalog/ProductCard.kt) with the reference's round well: photos arrive through the shared pipeline.
                items(items, key = { it.skuId }) { p -> TazProductCard(p, onClick = { onOpen(p) }, width = CARD_W, wellShape = CircleShape) }
            }
        }
    }
}

// ---- bulk band ------------------------------------------------------------------------------------------------------

@Composable
private fun HomeBulkBand(onShop: () -> Unit) {
    Box(Modifier.fillMaxWidth().padding(top = TazSpace.lg).aspectRatio(588f / 250f)) {
        // The band's wave top edge, then the plate clipped under it.
        Canvas(Modifier.matchParentSize()) {
            val w = size.width; val h = size.height
            val p = Path().apply {
                moveTo(0f, h * 0.22f); cubicTo(w * 0.18f, h * 0.02f, w * 0.42f, h * 0.30f, w * 0.62f, h * 0.10f)
                cubicTo(w * 0.78f, -h * 0.05f, w * 0.92f, h * 0.02f, w, h * 0.06f); lineTo(w, h); lineTo(0f, h); close()
            }
            drawPath(p, TazColors.BrandEditorialDeep)
        }
        Box(Modifier.matchParentSize().padding(top = 18.dp).clip(RoundedCornerShape(topStart = 120.dp, topEnd = 40.dp))) {
            Plate(Res.drawable.bg_home_bulk, Modifier.matchParentSize(), alignment = Alignment.CenterEnd)
            Box(Modifier.matchParentSize().background(Brush.horizontalGradient(0f to TazColors.BrandEditorialDeep, 0.42f to TazColors.BrandEditorialDeep.copy(alpha = 0.55f), 0.6f to Color.Transparent)))
        }
        Column(Modifier.fillMaxHeight().fillMaxWidth(0.55f).padding(start = TazSpace.lg, top = TazSpace.xl), verticalArrangement = Arrangement.Center) {
            Eyebrow(HomeCopy.BULK_EYEBROW, TazColors.EditorialOnDark)
            Spacer(Modifier.height(TazSpace.xs))
            EditorialText(listOf(plain("Stock more.\n"), italic("Save more.")), size = 24.sp, lineHeight = 27.sp, color = TazColors.EditorialOnDark, textAlign = TextAlign.Start)
            Spacer(Modifier.height(TazSpace.md))
            CompactPill(HomeCopy.BULK_CTA, onClick = onShop, dark = false)
        }
    }
}

// ---- shared bits ----------------------------------------------------------------------------------------------------

@Composable
private fun Plate(res: DrawableResource, modifier: Modifier, alignment: Alignment) {
    Image(painterResource(res), contentDescription = null, contentScale = ContentScale.Crop, alignment = alignment, modifier = modifier.clearAndSetSemantics { })
}

@Composable
private fun Eyebrow(text: String, color: Color) {
    Text(text, fontSize = TazType.eyebrowSize, fontWeight = FontWeight.SemiBold, letterSpacing = TazType.eyebrowTracking, color = color)
}

/** The in-banner pill CTA: 40dp, serif label, trailing arrow. Dark = green on light photography; light = cream on green. */
@Composable
private fun CompactPill(text: String, onClick: () -> Unit, dark: Boolean = true) {
    val bg = if (dark) TazColors.BrandEditorial else TazColors.CreamStrong
    val fg = if (dark) TazColors.EditorialOnDark else TazColors.BrandEditorial
    Row(
        Modifier.height(40.dp).clip(TazRadius.pill).background(bg).border(BorderStroke(0.dp, Color.Transparent), TazRadius.pill)
            .tazPressable(onClick = onClick, pressScale = TazPress.control).semantics { contentDescription = text }.padding(horizontal = TazSpace.lg),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(text, fontFamily = tazEditorialFamily(), fontSize = 15.sp, color = fg)
        Spacer(Modifier.width(TazSpace.sm))
        Icon(Icons.AutoMirrored.Rounded.ArrowForward, contentDescription = null, tint = fg, modifier = Modifier.size(16.dp))
    }
}

private val CAT_SIZE = 72.dp
private val CARD_W = 140.dp
