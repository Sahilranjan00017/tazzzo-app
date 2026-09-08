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
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
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
import androidx.compose.ui.text.font.FontWeight
import com.tazzzo.app.config.priceBandLabel
import com.tazzzo.app.theme.TazMotion
import com.tazzzo.app.ui.common.ProductCard
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.graphics.Brush

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

private val SectionGap: Dp = 20.dp
private val InnerGap: Dp = 12.dp
// 74dp put the two-line label almost level with the picture, so a grid of
// them read as a wall of text with thumbnails attached. At 86dp the
// photograph leads and the label supports it.
private val CategoryTileSize: Dp = 86.dp

/** Matches the banner height inside ui/common so the carousel never resizes. */
private val BannerHeight: Dp = 150.dp

/** Everything the home feed needs, loaded as one unit. */
private data class HomeFeed(
    val banners: List<PromoBanner>,
    val categories: List<Category>,
    val rails: Map<String, List<Product>>,
    /** Distinct products from real past orders — empty when no order history. */
    val orderAgain: List<Product>,
    /** Largest MRP-vs-price savings among the loaded rails. Data, not a claim. */
    val deals: List<Product> = emptyList(),
    /** Everyday staples for the two-up grid. Real, purchasable, deduped. */
    val essentials: List<Product> = emptyList()
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
        val rails = mapOf(
            "Bestsellers" to catalog.getBestsellers(),
            "Snacks & Munchies" to catalog.getProducts("munchies"),
            "Dairy, Bread & Eggs" to catalog.getProducts("dairy"),
            "Personal Care" to (catalog.getProducts("personal") + catalog.getProducts("skincare")),
            "Sweet Tooth" to catalog.getProducts("sweet"),
            "Cleaning Essentials" to catalog.getProducts("cleaning")
        )
        val deals = rails.values.flatten().distinctBy { it.id }
            .filter { it.mrp > it.price && it.isPurchasable }
            .sortedByDescending { it.mrp - it.price }
            .take(10)
        HomeFeed(
            banners = catalog.getBanners(),
            categories = Taxonomy.categories(catalog),
            rails = rails,
            orderAgain = pastOrders
                .flatMap { it.lines }
                .map { it.product }
                .distinctBy { it.id }
                .take(8),
            deals = deals,
            essentials = run {
                // Deduplicate against everything else the feed already draws.
                // A product shown twice on one screen is worse than a shorter
                // section: it wastes the slot, and two controls announcing
                // "Add Fresh Onion to cart" are indistinguishable to a screen
                // reader — the same accessibility defect the hero tiles hit.
                val alreadyShown = (
                    rails.values.flatten() +
                        pastOrders.flatMap { it.lines }.map { it.product }
                    ).map { it.id }.toSet() + deals.map { it.id }
                (catalog.getProducts("fruits") + catalog.getProducts("atta"))
                    .distinctBy { it.id }
                    .filter { it.isPurchasable && it.id !in alreadyShown }
                    .take(8)
            }
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

        // Order on Home: festival, today's prices, then the aisles.
        //
        // Two full-bleed colour zones — green, then orange — with the cream
        // directory beneath them gives the scroll a change of scale. Leading
        // with twenty uniform category tiles instead put the first rupee two
        // screens down, and buried the campaign entirely.
        //
        // The hero took over from the promo carousel, which was removed for an
        // unsubstantiated "SAVE 8-20%" claim (D6) and third-party trade dress
        // in its photograph. Zepto leads with its campaign and puts the
        // directory under it; Blinkit runs no campaign at all. Tazzzo has one
        // and it is live, so this follows Zepto.
        item { CampaignHero() }
        item { Spacer(Modifier.height(SectionGap)) }
        item { PriceBandDealsPanel(data.deals) }
        item { Spacer(Modifier.height(SectionGap)) }
        item { CouponRail() }
        item { Spacer(Modifier.height(SectionGap)) }

        // ----------------------------------------------- the aisle directory
        // Grouped, not a single truncated grid. Blinkit and Zepto both open on
        // the aisle directory itself — heading, 4-up grid, next heading — and
        // depth lives in the aisle. Showing eight tiles under one heading with
        // a "See all" made the customer's second tap a whole extra screen just
        // to see the rest of the shop.
        data.categories.groupBy { it.group }.forEach { (group, cats) ->
            item(key = "cat-header-$group") { SectionHeader(title = group) }
            item(key = "cat-grid-$group") { CategoryGrid(cats) }
            item(key = "cat-gap-$group") { Spacer(Modifier.height(TazSpace.md)) }
        }


        // ------------------------------------ 4. bestsellers + order again
        item { Spacer(Modifier.height(SectionGap)) }
        // Coupons that exist in config, with their thresholds stated. A coupon
        // card that hides its minimum is a disappointment waiting in the cart.
        // Data-backed: the biggest rupee savings among products already loaded
        // for this feed. No historical price claims — just MRP vs price today.
        item { Spacer(Modifier.height(SectionGap)) }
        item { ProductRail("Bestsellers", data.rails["Bestsellers"] ?: emptyList()) }
        // Everyday staples, two-up, so the densest part of the basket is
        // reachable without opening an aisle.
        if (data.essentials.isNotEmpty()) {
            item { Spacer(Modifier.height(SectionGap)) }
            item { EssentialsGrid(data.essentials) }
        }
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
            // The logo is deliberately absent. Home's most valuable row is the
            // address and the search field; a wordmark on the screen someone
            // opens twenty times a week earns nothing and costs ~26dp of the
            // fold. It still leads the splash, the sign-in and the footer.
            //
            // Removing it left this row holding only a spacer, which pushed the
            // address onto a line of its own beneath an empty gap. The address
            // now shares this row, which is the arrangement it should have had:
            // where the order is going, then the three standing actions.
            Row(
                Modifier.weight(1f)
                    .guidedTarget("location")
                    .defaultMinSize(minHeight = TazSize.touchTarget)
                    .tazPressable(onClick = { /* address picker — demo only */ }, pressScale = TazPress.compact),
                verticalAlignment = Alignment.CenterVertically
            ) {
                TazIcon(TazIcons.Location, null, size = TazSize.iconXs, tint = TazColors.Green)
                Spacer(Modifier.width(TazSpace.xs))
                Text(
                    app.user.address,
                    fontSize = TazType.titleSize, fontWeight = TazType.titleWeight,
                    color = TazColors.TextPrimary,
                    maxLines = 1, overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f, fill = false)
                )
                TazIcon(TazIcons.Dropdown, null, size = TazSize.iconXs, tint = TazColors.TextSecondary)
            }
            Box(Modifier.guidedTarget("coins")) {
                CoinChip(app.user.coinBalance) { app.navigate(Screen.Coins) }
            }
            // No mic here. Two microphones on one screen — this one and the one
            // inside the search field — opened the same sheet and read as two
            // different features. Voice belongs in the search bar, where
            // speaking is the alternative to typing.
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

        // Row 2 used to repeat the address here. It moved up into row 1 when
        // the logo left, so there is nothing between identity and search.

        // --- Search, with voice inside it, and the Master List beside it -----
        // Zepto puts a second entry point to the right of its search field.
        // Ours is the Master List: the regulars a grocery customer rebuys, which
        // is the highest-value thing that slot can hold in a repeat-purchase
        // business.
        Spacer(Modifier.height(TazSpace.md))
        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(TazSpace.sm),
            verticalAlignment = Alignment.CenterVertically
        ) {
            HomeSearchBar(Modifier.weight(1f))
            MasterListButton()
        }
    }
}

@Composable
private fun HomeSearchBar(modifier: Modifier = Modifier) {
    val app = LocalAppState.current
    Row(
        modifier
            .fillMaxWidth()
            .height(TazSize.inputHeight)
            .guidedTarget("search")
            .shadow(3.dp, TazRadius.card, spotColor = Color.Black.copy(alpha = 0.14f))
            .clip(TazRadius.card)
            .background(TazColors.Surface)
            .border(BorderStroke(1.dp, TazColors.CardBorder), TazRadius.card)
            .semantics(mergeDescendants = true) { contentDescription = "Search products" }
            .tazPressable(onClick = { app.navigate(Screen.Search) }, pressScale = TazPress.compact)
            .padding(start = TazSpace.md, end = TazSpace.sm),
        verticalAlignment = Alignment.CenterVertically
    ) {
        TazIcon(TazIcons.Search, null, size = TazSize.iconSm, tint = TazColors.TextTertiary)
        Spacer(Modifier.width(TazSpace.sm))
        RotatingSearchHint(Modifier.weight(1f))
        Spacer(Modifier.width(TazSpace.sm))
        // Sized down from `avatar`: the field lost width to Master List beside
        // it, and at the old size the mic was clipped by the field's own
        // rounded edge — a control cut in half by its own container.
        MicButton(TazSize.micButton) { app.showVoiceSheet = true }
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

@Composable
private fun CouponRail() {
    val coupons = com.tazzzo.app.config.PromotionConfig.active.filter { it.isCoupon }
    if (coupons.isEmpty()) return
    val app = LocalAppState.current
    SectionHeader("Coupons & offers")
    Row(
        Modifier.fillMaxWidth().horizontalScroll(rememberScrollState())
            .padding(horizontal = TazSpace.gutter),
        horizontalArrangement = Arrangement.spacedBy(TazSpace.md)
    ) {
        coupons.forEach { c ->
            Column(
                Modifier.width(150.dp).clip(TazRadius.card).background(TazColors.GreenSoft)
                    .border(BorderStroke(1.dp, TazColors.Green.copy(alpha = 0.25f)), TazRadius.card)
                    .tazPressableCard(
                        onClick = { app.couponCode = c.couponCode; app.navigate(Screen.Cart) },
                        shape = TazRadius.card
                    )
                    .padding(TazSpace.md)
            ) {
                Text(
                    c.flatRupees?.let { "FLAT ₹$it OFF" } ?: c.percent?.let { "$it% OFF" } ?: c.title,
                    fontSize = TazType.titleSize, fontWeight = TazType.titleWeight, color = TazColors.Green
                )
                Spacer(Modifier.height(TazSpace.xxs))
                Text(
                    if (c.minOrderRupees > 0) "above ₹${c.minOrderRupees}" else "no minimum",
                    fontSize = TazType.captionSize, color = TazColors.TextSecondary
                )
                Spacer(Modifier.height(TazSpace.sm))
                Box(Modifier.clip(TazRadius.chip).background(TazColors.Surface).padding(horizontal = TazSpace.sm, vertical = TazSpace.xxs)) {
                    Text(c.couponCode ?: "", fontSize = TazType.microSize, fontWeight = TazType.microWeight, color = TazColors.TextPrimary)
                }
            }
        }
    }
    Spacer(Modifier.height(TazSpace.lg))
}

/**
 * Festival / seasonal hero. Reads [com.tazzzo.app.config.CampaignConfig];
 * renders nothing when there is no campaign, so Home never shows an empty slot.
 *
 * Artwork is [ASSET REQUIRED]: with no `heroImageUrl` the hero is a
 * typographic treatment over Tazzzo green, with the collection's own category
 * art (openly licensed, already attributed) as the visual cue. It is a real
 * module with real destinations — the CTA opens the first collection aisle,
 * each tile opens its aisle — not a decorative rectangle.
 */
@Composable
private fun CampaignHero() {
    val campaign = com.tazzzo.app.config.CampaignConfig.current ?: return
    val app = LocalAppState.current
    val byId = (Taxonomy.cached() ?: emptyList()).associateBy { it.id }
    // Edge to edge, not another card in the gutter. Every other block on Home
    // is an inset rounded rectangle, so a hero that is also an inset rounded
    // rectangle has no way to read as the loudest thing on the page. Bleeding
    // it to both edges and carrying a gradient is the one move that gives the
    // scroll a change of scale.
    Column(
        Modifier.fillMaxWidth()
            .background(
                Brush.linearGradient(listOf(TazColors.GreenDark, TazColors.GreenMid))
            )
            .tazPressableCard(
                onClick = { campaign.categoryIds.firstOrNull()?.let { app.navigate(Screen.CategoryDetail(it)) } },
                shape = androidx.compose.ui.graphics.RectangleShape
            )
            .padding(horizontal = TazSpace.gutter, vertical = TazSpace.xl)
    ) {
        campaign.validUntilLabel?.let {
            Text(
                it.uppercase(), fontSize = TazType.microSize, fontWeight = TazType.microWeight,
                letterSpacing = TazType.labelTracking, color = TazColors.White.copy(alpha = 0.7f)
            )
            Spacer(Modifier.height(TazSpace.xs))
        }
        Text(
            campaign.title, fontSize = TazType.h1Size, fontWeight = TazType.h1Weight,
            lineHeight = TazType.h1Line, color = TazColors.White
        )
        Spacer(Modifier.height(TazSpace.xxs))
        Text(campaign.subtitle, fontSize = TazType.bodySize, color = TazColors.White.copy(alpha = 0.85f))
        Spacer(Modifier.height(TazSpace.lg))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(TazSpace.sm)) {
            campaign.categoryIds.take(4).forEach { id ->
                val cat = byId[id] ?: return@forEach
                Column(
                    Modifier.weight(1f)
                        .clip(TazRadius.card).background(TazColors.White.copy(alpha = 0.10f))
                        // Distinct label: the SAME category also appears in the grid
                        // below. Two controls announcing "Vegetables & Fruits" are
                        // indistinguishable to a screen reader (and to a test).
                        .semantics(mergeDescendants = true) {
                            contentDescription = "${campaign.title}: ${cat.name}"
                        }
                        .tazPressableCard(onClick = { app.navigate(Screen.CategoryDetail(cat.id)) }, shape = TazRadius.card)
                        .padding(TazSpace.sm),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    // Art where a vetted photograph exists, the category's own
                    // emoji where it does not. Four tiles were withdrawn for
                    // trade dress and an unverified licence, and without this
                    // branch their campaign tiles rendered as an empty box.
                    val art = com.tazzzo.app.ui.common.categoryArtFor(cat.id)
                    if (art != null) {
                        androidx.compose.foundation.Image(
                            painter = org.jetbrains.compose.resources.painterResource(art),
                            contentDescription = null,   // decorative; the tile carries the label
                            modifier = Modifier.size(64.dp).clip(TazRadius.chip),
                            contentScale = androidx.compose.ui.layout.ContentScale.Crop
                        )
                    } else {
                        Box(
                            Modifier.size(64.dp).clip(TazRadius.chip)
                                .background(TazColors.White.copy(alpha = 0.14f)),
                            contentAlignment = Alignment.Center
                        ) { Text(cat.emoji, fontSize = 28.sp) }
                    }
                    Spacer(Modifier.height(TazSpace.xs))
                    Text(
                        // Two lines, not one. `substringBefore(",")` only
                        // shortens names that HAVE a comma — "Dairy, Bread &
                        // Eggs" becomes "Dairy", but "Vegetables & Fruits" and
                        // "Sweet Tooth" have none and were chopped mid-word on
                        // a 384dp screen ("Vegetabl", "Sweet To"). Verified on a
                        // Galaxy S24 FE, which is narrower than the emulator.
                        // First word only, one line. The full name wrapped to two
                        // lines and still truncated ("Pooja & Religiou…") in a
                        // 64dp column — a label that fits is worth more here
                        // than a complete one, because the picture identifies it.
                        cat.name.substringBefore(",").substringBefore(" &").trim(),
                        fontSize = TazType.microSize,
                        fontWeight = TazType.microWeight, lineHeight = TazType.microLine,
                        textAlign = TextAlign.Center,
                        color = TazColors.White, maxLines = 2, overflow = TextOverflow.Ellipsis
                    )
                }
            }
        }
        Spacer(Modifier.height(TazSpace.lg))
        Box(
            Modifier.clip(TazRadius.pill).background(TazColors.White)
                .padding(horizontal = TazSpace.lg, vertical = TazSpace.sm)
        ) {
            Text(
                campaign.ctaLabel, fontSize = TazType.buttonSize, fontWeight = TazType.buttonWeight,
                color = TazColors.GreenDark
            )
        }
    }
    Spacer(Modifier.height(SectionGap))
}

private val SearchHints = listOf(
    "Search \"milk\", \"atta\", \"soap\"…",
    "Search \"onion\", \"tomato\", \"palak\"…",
    "Search \"agarbatti\", \"diya\", \"ghee\"…",
    "Search \"biscuits\", \"chips\", \"tea\"…"
)

/**
 * The search field's cycling hint.
 *
 * Gated on [MotionSettings.ambientEnabled], like every other perpetual
 * animation in the app. An ungated `while (true) { delay(…) }` never lets
 * Compose reach idle, and the instrumented suite waits on idle — one rotating
 * placeholder would hang all six journey tests rather than fail them, which is
 * far harder to diagnose than a red assertion.
 */
@Composable
private fun RotatingSearchHint(modifier: Modifier = Modifier) {
    var index by remember { mutableStateOf(0) }
    val rotating = MotionSettings.ambientEnabled
    LaunchedEffect(rotating) {
        if (!rotating) return@LaunchedEffect
        while (true) {
            delay(3_200)
            index = (index + 1) % SearchHints.size
        }
    }
    Crossfade(SearchHints[index], animationSpec = tween(TazMotion.normal), label = "searchHint") {
        Text(
            it, fontSize = TazType.bodySize, color = TazColors.TextTertiary,
            maxLines = 1, overflow = TextOverflow.Ellipsis
        )
    }
}

/**
 * Deals, grouped four to a row with the price band each item genuinely falls in.
 *
 * The mockup shows "₹9 STORE" / "₹19 STORE" ribbons. Tazzzo runs no price-point
 * store and nothing sells at ₹9, so those bands would label empty shelves. The
 * band here is read off the item's own price, which makes it both accurate and
 * impossible to leave empty.
 *
 * No countdown. `Campaign` carries display copy, not an end timestamp, and the
 * client does not gate merchandising on its own clock.
 */
@Composable
private fun PriceBandDealsPanel(deals: List<Product>) {
    if (deals.isEmpty()) return
    val app = LocalAppState.current
    val shown = deals.take(8)
    // Full bleed, like the hero. Two edge-to-edge colour zones with cream
    // between them give the page a rhythm; four inset cards in a row do not.
    Column(
        Modifier.fillMaxWidth().background(TazColors.OrangeSoft)
            .padding(vertical = TazSpace.lg)
    ) {
        Row(
            Modifier.fillMaxWidth().padding(horizontal = TazSpace.md, vertical = TazSpace.xs),
            verticalAlignment = Alignment.CenterVertically
        ) {
            TazIcon(TazIcons.Offer, null, size = TazSize.iconSm, tint = TazColors.Orange)
            Spacer(Modifier.width(TazSpace.sm))
            Text(
                "Today's deals", fontSize = TazType.h2Size, fontWeight = TazType.h2Weight,
                color = TazColors.TextPrimary, modifier = Modifier.weight(1f)
            )
            Text(
                "See all", fontSize = TazType.captionSize, fontWeight = FontWeight.SemiBold,
                color = TazColors.Orange,
                modifier = Modifier
                    .semantics(mergeDescendants = true) { contentDescription = "See all deals" }
                    .tazPressable(
                        onClick = { app.homeTab = HomeTab.DEALS },
                        pressScale = TazPress.compact
                    )
                    .padding(TazSpace.xs)
            )
        }
        shown.chunked(4).forEach { row ->
            Row(
                Modifier.fillMaxWidth().padding(
                    horizontal = TazSpace.sm, vertical = TazSpace.xs
                ),
                horizontalArrangement = Arrangement.spacedBy(TazSpace.xs)
            ) {
                row.forEach { product ->
                    Box(Modifier.weight(1f)) {
                        ProductCard(
                            product,
                            width = null,
                            compact = true,
                            ribbon = priceBandLabel(product.price),
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                }
                repeat(4 - row.size) { Spacer(Modifier.weight(1f)) }
            }
        }
    }
}

/**
 * Everyday staples, two to a row.
 *
 * The count is the size of the list actually drawn — the mockup's "42 Items"
 * was a comp value, and a header that overstates the section it introduces is
 * the same defect as a category tile overstating its aisle.
 */
@Composable
private fun EssentialsGrid(products: List<Product>) {
    if (products.isEmpty()) return
    Column(Modifier.fillMaxWidth()) {
        Row(
            Modifier.fillMaxWidth().padding(
                horizontal = TazSpace.screenEdge, vertical = TazSpace.sm
            ),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                "Fresh & daily essentials", fontSize = TazType.h2Size,
                fontWeight = TazType.h2Weight, color = TazColors.TextPrimary,
                maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f)
            )
            Text(
                if (products.size == 1) "1 item" else "${products.size} items",
                fontSize = TazType.captionSize, color = TazColors.TextTertiary, maxLines = 1
            )
        }
        products.chunked(2).forEach { row ->
            Row(
                Modifier.fillMaxWidth().padding(
                    horizontal = TazSpace.screenEdge, vertical = TazSpace.xs
                ),
                horizontalArrangement = Arrangement.spacedBy(TazSpace.sm)
            ) {
                row.forEach { product ->
                    Box(Modifier.weight(1f)) {
                        ProductCard(
                            product, width = null, modifier = Modifier.fillMaxWidth()
                        )
                    }
                }
                repeat(2 - row.size) { Spacer(Modifier.weight(1f)) }
            }
        }
    }
}

/**
 * Entry to the Master List, sitting beside the search field.
 *
 * Labelled and sized as a peer of search rather than a chip on it: a customer
 * either hunts for something new (search) or restocks what they always buy
 * (this). Those are the two ways into a grocery basket and they deserve equal
 * billing.
 */
@Composable
private fun MasterListButton() {
    val app = LocalAppState.current
    // Outlined, not filled. Filled green made it the loudest thing on the
    // screen, competing with the search field it sits beside — and search is
    // still the more common way in. Same height, same radius, quieter voice.
    Column(
        Modifier
            .height(TazSize.inputHeight)
            .clip(TazRadius.card)
            .background(TazColors.GreenSoft)
            .border(BorderStroke(1.dp, TazColors.Green.copy(alpha = 0.25f)), TazRadius.card)
            .semantics(mergeDescendants = true) { contentDescription = "Master List" }
            .tazPressable(
                onClick = { app.navigate(Screen.MasterList) },
                pressScale = TazPress.compact, shape = TazRadius.card
            )
            .padding(horizontal = TazSpace.md),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        TazIcon(TazIcons.Receipt, null, size = TazSize.iconSm, tint = TazColors.Green)
        Spacer(Modifier.height(2.dp))
        Text(
            "My list", fontSize = TazType.microSize, fontWeight = TazType.microWeight,
            color = TazColors.Green, maxLines = 1
        )
    }
}
