package com.tazzzo.app.ui.home

import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import com.tazzzo.app.HomeTab
import com.tazzzo.app.LocalAppState
import com.tazzzo.app.Screen
import com.tazzzo.app.config.AppConfig
import com.tazzzo.app.config.BrandCopy
import com.tazzzo.app.config.DeliveryCopy
import com.tazzzo.app.data.model.Category
import com.tazzzo.app.data.model.Product
import com.tazzzo.app.data.model.PromoBanner
import com.tazzzo.app.data.repository.ServiceLocator
import com.tazzzo.app.data.repository.Taxonomy
import com.tazzzo.app.ui.interaction.tazPressableCard
import com.tazzzo.app.ui.interaction.TazPress
import com.tazzzo.app.ui.interaction.tazPressable
import com.tazzzo.app.theme.MotionSettings
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.platform.testTag
import com.tazzzo.app.theme.TazColors
import com.tazzzo.app.theme.TazIcons
import com.tazzzo.app.theme.TazRadius
import com.tazzzo.app.theme.TazSize
import com.tazzzo.app.theme.TazSpace
import com.tazzzo.app.theme.TazType
import com.tazzzo.app.ui.common.CategoryTile
import com.tazzzo.app.ui.common.CoinChip
import com.tazzzo.app.ui.common.CoinsPromoBanner
import com.tazzzo.app.ui.common.DeliveryPromoBanner
import com.tazzzo.app.ui.common.HeroBasketBanner
import com.tazzzo.app.ui.common.LogoImage
import com.tazzzo.app.ui.common.MicButton
import com.tazzzo.app.ui.common.ProductRail
import com.tazzzo.app.ui.common.ProductRailSkeleton
import com.tazzzo.app.ui.common.SectionHeader
import com.tazzzo.app.ui.common.SkeletonBlock
import com.tazzzo.app.ui.common.StateHost
import com.tazzzo.app.ui.common.TazIcon
import com.tazzzo.app.ui.common.VoiceCommerceBannerV3
import com.tazzzo.app.ui.common.guidedTarget
import com.tazzzo.app.ui.state.rememberLoad
import kotlinx.coroutines.delay

private val RAIL_ORDER = listOf(
    "Bestsellers",
    "Snacks & Munchies",
    "Dairy, Bread & Eggs",
    "Personal Care",
    "Sweet Tooth",
    "Cleaning Essentials"
)

// ---------------------------------------------------------------------------
// Page rhythm.
//
// The old feed was a stack of identical full-width white rectangles at one
// margin, which is why it read as a wireframe. Two gaps fix that: majors
// breathe apart, members of the same section sit close together — and the
// blocks themselves alternate between cream ground (category grid), gutter
// cards (banners) and edge-bleeding rails.
// ---------------------------------------------------------------------------

private val SectionGap: Dp = 28.dp
private val InnerGap: Dp = 12.dp
private val CategoryTileSize: Dp = 74.dp

/** Matches the banner height inside ui/common so the carousel never resizes. */
private val BannerHeight: Dp = 150.dp

/** Everything the home feed needs, loaded as one unit. */
private data class HomeFeed(
    val banners: List<PromoBanner>,
    val categories: List<Category>,
    val rails: Map<String, List<Product>>,
    /** Distinct products from real past orders — empty when no order history. */
    val orderAgain: List<Product>
)

/**
 * The home feed: brand bar, search, category grid, offers, rails, voice.
 *
 * Reference implementation of the load/state contract: one [rememberLoad] for the
 * whole feed, rendered through [StateHost] so loading, failure and retry are
 * handled without any hand-rolled `loading` flag.
 *
 * The header + search sit OUTSIDE the scrolling list: they are the brand bar,
 * they stay put, and the cream feed scrolls under their shadow.
 */
@Composable
fun HomeTabContent() {
    val feed = rememberLoad(isEmpty = { false }) {
        val catalog = ServiceLocator.catalog
        val pastOrders = ServiceLocator.orders.getOrders()
        HomeFeed(
            banners = catalog.getBanners(),
            categories = Taxonomy.categories(catalog),
            rails = mapOf(
                "Bestsellers" to catalog.getBestsellers(),
                "Snacks & Munchies" to catalog.getProducts("munchies"),
                "Dairy, Bread & Eggs" to catalog.getProducts("dairy"),
                "Personal Care" to (catalog.getProducts("personal") + catalog.getProducts("skincare")),
                "Sweet Tooth" to catalog.getProducts("sweet"),
                "Cleaning Essentials" to catalog.getProducts("cleaning")
            ),
            orderAgain = pastOrders
                .flatMap { it.lines }
                .map { it.product }
                .distinctBy { it.id }
                .take(8)
        )
    }

    Column(Modifier.fillMaxSize().background(TazColors.Cream)) {
        HomeHeader()
        StateHost(
            handle = feed,
            modifier = Modifier.weight(1f).fillMaxWidth(),
            loading = { HomeFeedSkeleton() }
        ) { data -> HomeFeedList(data) }
    }
}

/** The success path — the mandated feed order, re-spaced. */
@Composable
private fun HomeFeedList(data: HomeFeed) {
    val app = LocalAppState.current
    // Home scroll survives a trip into a product and back.
    val homeScroll = rememberSaveable(saver = LazyListState.Saver) { LazyListState() }
    LazyColumn(
        // Tagged so the journey suite can scroll rails into composition —
        // ADD controls below the fold do not exist in semantics until then.
        modifier = Modifier.fillMaxSize().testTag("homeFeed"),
        state = homeScroll,
        contentPadding = PaddingValues(bottom = TazSpace.cartBarClearance)
    ) {
        // ------------------------------------------------- 1. restore notice
        item { RestoreNoticeBanner() }
        item { Spacer(Modifier.height(TazSpace.lg)) }

        // ----------------------------------------------- 2. shop by category
        // No card wrapper: tiles sit straight on the cream ground so the grid
        // reads as a different KIND of block from the rails below it.
        item {
            SectionHeader(
                title = "Shop by category",
                actionLabel = "See all",
                onAction = { app.homeTab = HomeTab.CATEGORIES }
            )
        }
        item { CategoryGrid(data.categories.take(8)) }

        // ----------------------------------------------- 3. offers carousel
        item { Spacer(Modifier.height(SectionGap)) }
        item { BannerCarousel(data.banners) }

        // ------------------------------------ 4. bestsellers + order again
        item { Spacer(Modifier.height(SectionGap)) }
        item { ProductRail("Bestsellers", data.rails["Bestsellers"] ?: emptyList()) }
        if (data.orderAgain.isNotEmpty()) {
            item { Spacer(Modifier.height(SectionGap)) }
            item {
                ProductRail(
                    "Order again", data.orderAgain,
                    actionLabel = "All orders",
                    onAction = { app.homeTab = HomeTab.ORDER_AGAIN }
                )
            }
        }

        // ----------------------------------------------- 5. remaining rails
        RAIL_ORDER.filter { it != "Bestsellers" }.forEach { title ->
            item { Spacer(Modifier.height(SectionGap)) }
            item { ProductRail(title, data.rails[title] ?: emptyList()) }
        }

        // ------------------------------------ 6. voice commerce (coming soon)
        item { Spacer(Modifier.height(SectionGap)) }
        item {
            Box(Modifier.fillMaxWidth().padding(horizontal = TazSpace.gutter)) {
                VoiceCommerceBannerV3()
            }
        }

        // ----------------------------------------------------- 7. footer
        item { HomeFooter() }
    }
}

/**
 * Loading keeps the real brand bar (it lives outside this composable) and
 * shows the silhouette of the two blocks that arrive first — the category grid
 * and the rails — so nothing jumps when the data lands.
 */
@Composable
private fun HomeFeedSkeleton() {
    Column(
        Modifier.fillMaxSize().padding(top = TazSpace.xl),
        verticalArrangement = Arrangement.spacedBy(SectionGap)
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(InnerGap)) {
            SkeletonBlock(
                width = 170.dp, height = 20.dp,
                modifier = Modifier.padding(start = TazSpace.gutter)
            )
            repeat(2) {
                Row(
                    Modifier.fillMaxWidth().padding(horizontal = TazSpace.gutter),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    repeat(4) {
                        Box(Modifier.weight(1f), contentAlignment = Alignment.Center) {
                            SkeletonBlock(
                                width = CategoryTileSize, height = CategoryTileSize,
                                corner = TazRadius.tileDp
                            )
                        }
                    }
                }
            }
        }
        repeat(2) { ProductRailSkeleton() }
    }
}

// ---------------------------------------------------------------------------
// Header — the fixed brand bar: identity, coins, voice, account, location,
// and the single most inviting control on the screen (search).
// ---------------------------------------------------------------------------

@Composable
private fun HomeHeader() {
    val app = LocalAppState.current
    Column(
        Modifier
            .fillMaxWidth()
            // drawn last so the soft edge falls ON the cream feed below
            .zIndex(1f)
            .shadow(4.dp, spotColor = Color.Black.copy(alpha = 0.16f))
            .background(TazColors.Surface)
            .statusBarsPadding()
            .padding(horizontal = TazSpace.gutter)
            .padding(top = TazSpace.sm, bottom = TazSpace.md)
    ) {
        // --- Row 1: identity left, the three standing actions right ---------
        Row(
            Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(TazSpace.sm)
        ) {
            LogoImage(height = 26.dp)
            Spacer(Modifier.weight(1f))
            Box(Modifier.guidedTarget("coins")) {
                CoinChip(app.user.coinBalance) { app.navigate(Screen.Coins) }
            }
            Box(Modifier.guidedTarget("mic")) {
                MicButton { app.showVoiceSheet = true }
            }
            Box(
                Modifier
                    .size(TazSize.avatar)
                    .clip(CircleShape)
                    .background(TazColors.GreenSoft)
                    .tazPressable(onClick = { app.homeTab = HomeTab.ACCOUNT }, pressScale = TazPress.compact),
                contentAlignment = Alignment.Center
            ) {
                TazIcon(
                    TazIcons.Account, "Your account",
                    size = TazSize.iconSm, tint = TazColors.Green
                )
            }
        }

        // --- Row 2: one tappable line — promise, then where it goes ---------
        Spacer(Modifier.height(TazSpace.sm))
        Row(
            Modifier
                .fillMaxWidth()
                .guidedTarget("location")
                .defaultMinSize(minHeight = TazSize.touchTarget)
                .tazPressable(onClick = { /* address picker — demo only */ }, pressScale = TazPress.compact),
            verticalAlignment = Alignment.CenterVertically
        ) {
            TazIcon(TazIcons.Location, null, size = TazSize.iconXs, tint = TazColors.Green)
            Spacer(Modifier.width(TazSpace.xs + TazSpace.xxs))
            Text(
                DeliveryCopy.headline(AppConfig.deliveryPromise),
                fontSize = TazType.titleSize,
                fontWeight = TazType.titleWeight,
                color = TazColors.TextPrimary,
                maxLines = 1
            )
            Text(
                " · ",
                fontSize = TazType.bodySize,
                color = TazColors.TextTertiary,
                maxLines = 1
            )
            Text(
                app.user.address,
                fontSize = TazType.bodySize,
                color = TazColors.TextSecondary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f, fill = false)
            )
            TazIcon(
                TazIcons.Dropdown, null,
                size = TazSize.iconXs, tint = TazColors.TextSecondary
            )
        }

        // --- The search bar: tall, lifted, unmissable ------------------------
        Spacer(Modifier.height(TazSpace.md))
        HomeSearchBar()
    }
}

@Composable
private fun HomeSearchBar() {
    val app = LocalAppState.current
    Row(
        Modifier
            .fillMaxWidth()
            .height(TazSize.inputHeight)
            .guidedTarget("search")
            .shadow(3.dp, TazRadius.card, spotColor = Color.Black.copy(alpha = 0.14f))
            .clip(TazRadius.card)
            .background(TazColors.Surface)
            .border(BorderStroke(1.dp, TazColors.CardBorder), TazRadius.card)
            .semantics(mergeDescendants = true) { contentDescription = "Search products" }
            .tazPressable(onClick = { app.navigate(Screen.Search) }, pressScale = TazPress.compact)
            .padding(start = TazSpace.md, end = TazSpace.xs),
        verticalAlignment = Alignment.CenterVertically
    ) {
        TazIcon(TazIcons.Search, null, size = TazSize.iconSm, tint = TazColors.TextTertiary)
        Spacer(Modifier.width(TazSpace.sm))
        Text(
            "Search \"milk\", \"atta\", \"soap\"…",
            fontSize = TazType.bodySize,
            color = TazColors.TextTertiary,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f)
        )
        MicButton(TazSize.avatar) { app.showVoiceSheet = true }
    }
}

// ---------------------------------------------------------------------------
// Banner carousel — full width inside the gutter, one height, one radius.
// ---------------------------------------------------------------------------

@Composable
private fun BannerCarousel(banners: List<PromoBanner>) {
    if (banners.isEmpty()) return
    val app = LocalAppState.current

    // A pager, not a Crossfade: cross-fading two OPAQUE banners meant both
    // headlines were legible on top of each other mid-transition, which read as
    // a rendering bug. Paging also makes the carousel swipeable, which is what
    // customers try first.
    val pagerState = rememberPagerState(pageCount = { banners.size })

    // Auto-advance is ambient motion: off under reduce-motion and under test.
    // It also yields to the customer — a banner that is yanked away mid-drag,
    // or advances while a finger is resting on it, is a classic annoyance.
    LaunchedEffect(banners, MotionSettings.ambientEnabled) {
        if (!MotionSettings.ambientEnabled) return@LaunchedEffect
        while (true) {
            delay(4000)
            if (pagerState.isScrollInProgress) continue
            val next = (pagerState.currentPage + 1) % banners.size
            pagerState.animateScrollToPage(next, animationSpec = tween(450))
        }
    }

    Column(Modifier.fillMaxWidth()) {
        HorizontalPager(
            state = pagerState,
            contentPadding = PaddingValues(horizontal = TazSpace.gutter),
            pageSpacing = TazSpace.md,
            modifier = Modifier.fillMaxWidth()
        ) { page ->
            val onClick: () -> Unit = when (page) {
                2 -> { { app.navigate(Screen.Coins) } }
                else -> { { app.homeTab = HomeTab.CATEGORIES } }
            }
            Box(
                // Banners are large surfaces, so the scale is gentle and a
                // shape-clipped tint carries most of the response. Previously
                // this had indication = null and gave no feedback at all.
                Modifier.tazPressableCard(onClick = onClick, shape = TazRadius.tile)
            ) {
                when (page) {
                    0 -> HeroBasketBanner()
                    1 -> DeliveryPromoBanner()
                    2 -> CoinsPromoBanner()
                    else -> HeroBasketBanner()
                }
            }
        }

        Spacer(Modifier.height(TazSpace.md))
        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically
        ) {
            repeat(banners.size) { i ->
                val active = pagerState.currentPage == i
                Box(
                    Modifier.padding(horizontal = 3.dp)
                        .width(if (active) 18.dp else 6.dp)
                        .height(6.dp)
                        .clip(TazRadius.pill)
                        .background(if (active) TazColors.Green else TazColors.CardBorder)
                )
            }
        }
    }
}

// ---------------------------------------------------------------------------
// Category grid — 4 up, straight on the cream ground, no card wrapper.
// ---------------------------------------------------------------------------

@Composable
private fun CategoryGrid(categories: List<Category>) {
    val app = LocalAppState.current
    Box(Modifier.guidedTarget("categories")) {
        Column(
            Modifier.fillMaxWidth().padding(horizontal = TazSpace.gutter),
            verticalArrangement = Arrangement.spacedBy(InnerGap)
        ) {
            categories.chunked(4).forEach { rowCats ->
                Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    rowCats.forEach { cat ->
                        CategoryCell {
                            CategoryTile(
                                cat,
                                size = CategoryTileSize,
                                onClick = { app.navigate(Screen.CategoryDetail(cat.id)) }
                            )
                        }
                    }
                    // keep tiles aligned when the last row is short
                    repeat(4 - rowCats.size) { Spacer(Modifier.weight(1f)) }
                }
            }
        }
    }
}

/** One of four equal columns — the tile centres in it and can never overflow. */
@Composable
private fun RowScope.CategoryCell(content: @Composable () -> Unit) {
    Box(Modifier.weight(1f), contentAlignment = Alignment.Center) { content() }
}

// ---------------------------------------------------------------------------
// Footer — quiet sign-off, not a second brand moment.
// ---------------------------------------------------------------------------

@Composable
private fun HomeFooter() {
    Column(
        Modifier.fillMaxWidth().padding(vertical = TazSpace.xxl),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        LogoImage(height = 18.dp)
        Spacer(Modifier.height(TazSpace.sm))
        Text(
            BrandCopy.promise,
            fontSize = TazType.captionSize,
            color = TazColors.TextTertiary
        )
    }
}

// ---------------------------------------------------------------------------
// Restore notice — disclosure that a saved cart came back.
// ---------------------------------------------------------------------------

@Composable
private fun RestoreNoticeBanner() {
    val app = LocalAppState.current
    val notice = app.restoreNotice ?: return
    Row(
        Modifier.fillMaxWidth()
            .padding(horizontal = TazSpace.gutter, vertical = TazSpace.md)
            .clip(TazRadius.card)
            .background(TazColors.SuccessSoft)
            .padding(horizontal = TazSpace.md, vertical = TazSpace.md),
        verticalAlignment = Alignment.CenterVertically
    ) {
        TazIcon(TazIcons.Success, null, size = TazSize.iconSm, tint = TazColors.Success)
        Spacer(Modifier.width(TazSpace.sm))
        Text(
            notice,
            fontSize = TazType.captionSize,
            lineHeight = TazType.captionLine,
            color = TazColors.TextPrimary,
            modifier = Modifier.weight(1f)
        )
        Text(
            "OK",
            fontSize = TazType.captionSize,
            fontWeight = TazType.savingsWeight,
            color = TazColors.Success,
            modifier = Modifier
                .defaultMinSize(minHeight = TazSize.touchTarget)
                .clip(TazRadius.pill)
                .tazPressable(onClick = { app.restoreNotice = null }, pressScale = TazPress.compact)
                .padding(horizontal = TazSpace.sm)
                .wrapContentHeight()
        )
    }
}
