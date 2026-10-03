package com.tazzzo.app.ui.catalog

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.tazzzo.app.HomeTab
import com.tazzzo.app.LocalAppState
import com.tazzzo.app.data.catalog.CatalogProduct
import com.tazzzo.app.data.catalog.PdpState
import com.tazzzo.app.data.catalog.StockTone
import com.tazzzo.app.data.catalog.productDetailHolder
import com.tazzzo.app.data.repository.ServiceLocator
import com.tazzzo.app.theme.TazColors
import com.tazzzo.app.theme.TazIcons
import com.tazzzo.app.theme.TazRadius
import com.tazzzo.app.theme.TazSize
import com.tazzzo.app.theme.TazSpace
import com.tazzzo.app.theme.TazType
import com.tazzzo.app.ui.cart.RemoteAddControl
import com.tazzzo.app.ui.common.CatalogProductImage
import com.tazzzo.app.ui.common.EditorialEmptyState
import com.tazzzo.app.ui.common.EditorialFailureState
import com.tazzzo.app.ui.common.EditorialText
import com.tazzzo.app.ui.common.SkeletonBlock
import com.tazzzo.app.ui.common.TazIcon
import com.tazzzo.app.ui.common.plain
import com.tazzzo.app.ui.interaction.TazPress
import com.tazzzo.app.ui.interaction.tazPressable
import tazzzo.resources.Res
import tazzzo.resources.bg_pdp_fallback

/**
 * The product page, built to the UI Page reference `Veg Page.jpeg` (UI-04): a large photographic hero over the upper
 * ~42% of the screen with a floating round back control and a real gallery counter, a cream panel that curves up over
 * the photo, the eyebrow (only when the vertical's name is known), the Newsreader product name, the price block with
 * the struck MRP and the restrained discount chip, the governed attributes as "Details", and a sticky purchase bar on
 * the SERVER cart. Everything that the reference shows and the backend does not send — pack size, description,
 * ratings, member price, pack selectors, "People also bought", share, wishlist — is deliberately absent.
 */
@Composable
fun RemoteProductDetailScreen(productId: String) {
    val app = LocalAppState.current
    val scope = rememberCoroutineScope()
    val reader = ServiceLocator.remoteCatalog
    val holder = remember(productId) { productDetailHolder(scope, reader, ServiceLocator.launchContext.pin) }
    LaunchedEffect(productId) { holder.open(productId) }
    val state by holder.state.collectAsState()
    var failures by remember(productId) { mutableStateOf(0) }
    LaunchedEffect(state) { if (state is PdpState.Content) failures = 0 }

    PdpScreenLayout(
        state = state,
        facts = (state as? PdpState.Content)?.detail?.pdpFacts { reader.taxonomy.nameOf(it) },
        consecutiveFailures = failures + 1,
        actions = PdpActions(
            back = { app.back() },
            backToShop = { app.homeTab = HomeTab.SHOP; app.back() },
            retry = { failures++; holder.retry() }
        ),
        banners = { ServiceabilityBannerView() }
    )
}

class PdpActions(val back: () -> Unit, val backToShop: () -> Unit, val retry: () -> Unit)

/** The reference layout, independent of its data source (evidence and tests render it with sample state). */
@Composable
fun PdpScreenLayout(
    state: PdpState,
    facts: PdpFacts?,
    actions: PdpActions,
    consecutiveFailures: Int = 1,
    banners: @Composable () -> Unit = {}
) {
    Box(Modifier.fillMaxSize().background(TazColors.Cream).testTag("pdp")) {
        when (state) {
            PdpState.Idle, PdpState.Loading -> PdpSkeleton()
            PdpState.NotFound -> Column(Modifier.fillMaxSize()) {
                Spacer(Modifier.statusBarsPadding().height(TazSize.touchTarget + TazSpace.xl))
                banners()
                EditorialEmptyState(TazIcons.Bag, PdpCopy.NOT_FOUND_TITLE, PdpCopy.NOT_FOUND_BODY, PdpCopy.BACK_TO_SHOP, onAction = actions.backToShop)
            }
            is PdpState.Failed -> Column(Modifier.fillMaxSize()) {
                Spacer(Modifier.statusBarsPadding().height(TazSize.touchTarget + TazSpace.xl))
                banners()
                EditorialFailureState(state.failure, consecutiveFailures, onRetry = actions.retry)
            }
            is PdpState.Content -> {
                val f = facts ?: state.detail.pdpFacts()
                PdpContent(state.detail.product, f, banners)
                PurchaseBar(state.detail.product, f, Modifier.align(Alignment.BottomCenter))
            }
        }
        FloatingBack(onBack = actions.back, Modifier.align(Alignment.TopStart))
    }
}

// ---- content ----------------------------------------------------------------------------------------------------------

@Composable
private fun PdpContent(product: CatalogProduct, f: PdpFacts, banners: @Composable () -> Unit) {
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).testTag("pdpContent")) {
        Hero(f)
        // The curved cream panel rises over the photo (reference: the white sheet with the soft upper edge).
        Column(Modifier.fillMaxWidth().offset(y = (-PDP_CURVE_OVERLAP_DP).dp)) {
            CurveCap()
            Column(Modifier.fillMaxWidth().background(TazColors.Cream)) {
                banners()
                Column(Modifier.fillMaxWidth().padding(horizontal = TazSpace.xl)) {
                    f.eyebrow?.let { Eyebrow(it); Spacer(Modifier.height(TazSpace.sm)) }
                    TitleAndPrice(f)
                    f.stockNote?.let {
                        Spacer(Modifier.height(TazSpace.sm))
                        Text(
                            it, fontSize = TazType.captionSize, fontWeight = FontWeight.SemiBold,
                            color = if (f.stockTone == StockTone.Unavailable) TazColors.Danger else if (f.stockTone == StockTone.Scarce) TazColors.Warning else TazColors.TextSecondary
                        )
                    }
                    if (f.details.isNotEmpty()) { Spacer(Modifier.height(TazSpace.xxl)); DetailsCard(f.details) }
                    // Room for the sticky purchase bar (height + margins + home indicator).
                    Spacer(Modifier.height(PURCHASE_BAR_CLEARANCE).navigationBarsPadding())
                }
            }
        }
    }
}

@Composable
private fun Hero(f: PdpFacts) {
    Box(Modifier.fillMaxWidth().aspectRatio(PDP_HERO_ASPECT).testTag("pdpHero")) {
        if (f.heroUrls.size > 1) {
            val pager = rememberPagerState { f.heroUrls.size }
            HorizontalPager(pager, Modifier.fillMaxSize(), key = { f.heroUrls[it] }) { page -> HeroImage(f.heroUrls[page], f.name) }
            // The reference's "1 / 5": the REAL gallery count, only when there is more than one image.
            Row(
                Modifier.align(Alignment.BottomEnd).padding(end = TazSpace.xl, bottom = (PDP_CURVE_OVERLAP_DP + 16).dp)
                    .shadow(6.dp, TazRadius.pill, ambientColor = Color.Black.copy(alpha = 0.08f)).clip(TazRadius.pill).background(TazColors.Surface)
                    .padding(horizontal = TazSpace.md, vertical = TazSpace.xs)
                    .semantics { contentDescription = "Image ${pager.currentPage + 1} of ${f.heroUrls.size}" },
                verticalAlignment = Alignment.CenterVertically
            ) {
                TazIcon(TazIcons.Back, null, size = TazSize.iconXs, tint = TazColors.BrandEditorial)
                Text("  ${pager.currentPage + 1} / ${f.heroUrls.size}  ", fontSize = TazType.captionSize, fontWeight = FontWeight.SemiBold, color = TazColors.BrandEditorial)
                TazIcon(TazIcons.Forward, null, size = TazSize.iconXs, tint = TazColors.BrandEditorial)
            }
        } else {
            HeroImage(f.heroUrls.firstOrNull(), f.name)
        }
    }
}

/** The shared pipeline image on the neutral PDP plate: Fit (a packshot is never cropped), skeleton wash while loading, the plate alone when missing. */
@Composable
private fun HeroImage(url: String?, name: String) {
    CatalogProductImage(
        url = url, name = name, modifier = Modifier.fillMaxSize(), aspectRatio = PDP_HERO_ASPECT,
        background = TazColors.CreamStrong, contentPadding = 0.dp, plate = Res.drawable.bg_pdp_fallback,
        contentScale = ContentScale.Fit, fallbackGlyphSize = 48.dp
    )
}

/** The soft upward curve where the cream panel meets the photo. */
@Composable
private fun CurveCap() {
    Canvas(Modifier.fillMaxWidth().height(PDP_CURVE_OVERLAP_DP.dp)) {
        val w = size.width; val h = size.height
        val p = Path().apply {
            moveTo(0f, h)
            lineTo(0f, h * 0.55f)
            quadraticTo(w * 0.18f, -h * 0.15f, w * 0.52f, h * 0.22f)
            quadraticTo(w * 0.80f, h * 0.50f, w, h * 0.10f)
            lineTo(w, h)
            close()
        }
        drawPath(p, TazColors.Cream)
    }
}

@Composable
private fun Eyebrow(text: String) {
    Text(text, fontSize = 10.sp, fontWeight = FontWeight.SemiBold, letterSpacing = 2.2.sp, color = TazColors.BrandEditorial)
}

/** Name left, price block right — the reference's two-column title row; the chip sits above the price. */
@Composable
private fun TitleAndPrice(f: PdpFacts) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.Top) {
        EditorialText(
            listOf(plain(f.name)), size = 34.sp, lineHeight = 38.sp, color = TazColors.TextPrimary, textAlign = TextAlign.Start,
            modifier = Modifier.weight(1f).semantics { contentDescription = f.name }
        )
        Spacer(Modifier.width(TazSpace.lg))
        Column(horizontalAlignment = Alignment.End) {
            f.discount?.let {
                Box(Modifier.clip(TazRadius.pill).background(TazColors.GreenSoft).padding(horizontal = TazSpace.md, vertical = TazSpace.xs)) {
                    Text(it.uppercase(), fontSize = TazType.captionSize, fontWeight = FontWeight.SemiBold, color = TazColors.BrandEditorial, maxLines = 1)
                }
                Spacer(Modifier.height(TazSpace.sm))
            }
            if (f.price != null) {
                Text(f.price, fontSize = 32.sp, fontWeight = FontWeight.Bold, color = TazColors.TextPrimary, maxLines = 1)
                f.mrp?.let { Text(it, fontSize = TazType.titleSize, color = TazColors.TextTertiary, textDecoration = TextDecoration.LineThrough, maxLines = 1) }
            } else {
                Text(PdpCopy.PRICE_UNAVAILABLE, fontSize = TazType.bodySize, color = TazColors.TextTertiary, maxLines = 1)
            }
        }
    }
}

@Composable
private fun DetailsCard(details: List<Pair<String, String>>) {
    Column(Modifier.fillMaxWidth().clip(TazRadius.tile).background(TazColors.Surface).padding(TazSpace.xl), verticalArrangement = Arrangement.spacedBy(TazSpace.md)) {
        EditorialText(listOf(plain(PdpCopy.DETAILS)), size = TazType.editorialSectionSize, lineHeight = TazType.editorialSectionLine, color = TazColors.TextPrimary, textAlign = TextAlign.Start)
        details.forEach { (label, value) ->
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.Top) {
                Text(label, fontSize = TazType.bodySize, color = TazColors.TextSecondary, modifier = Modifier.weight(1f))
                Spacer(Modifier.width(TazSpace.lg))
                Text(value, fontSize = TazType.bodySize, fontWeight = FontWeight.Medium, color = TazColors.TextPrimary, textAlign = TextAlign.End, modifier = Modifier.weight(1.4f))
            }
        }
    }
}

// ---- chrome -----------------------------------------------------------------------------------------------------------

@Composable
private fun FloatingBack(onBack: () -> Unit, modifier: Modifier) {
    Box(
        modifier.statusBarsPadding().padding(start = TazSpace.lg, top = TazSpace.sm)
            .size(TazSize.touchTarget).shadow(8.dp, CircleShape, ambientColor = Color.Black.copy(alpha = 0.10f)).clip(CircleShape).background(TazColors.Surface)
            .tazPressable(onClick = onBack, pressScale = TazPress.compact, role = Role.Button)
            .semantics { contentDescription = "Back" },
        contentAlignment = Alignment.Center
    ) { TazIcon(TazIcons.Back, null, size = TazSize.iconSm, tint = TazColors.TextPrimary) }
}

/** The sticky purchase surface: price block left, the real cart control right. The ONLY add control on the page. */
@Composable
private fun PurchaseBar(product: CatalogProduct, f: PdpFacts, modifier: Modifier) {
    Row(
        modifier.fillMaxWidth().padding(horizontal = TazSpace.lg).navigationBarsPadding().padding(bottom = TazSpace.md)
            .shadow(16.dp, TazRadius.sheetAll, ambientColor = Color.Black.copy(alpha = 0.10f), spotColor = Color.Black.copy(alpha = 0.16f))
            .clip(TazRadius.sheetAll).background(TazColors.Surface).padding(horizontal = TazSpace.lg, vertical = TazSpace.md).testTag("purchaseBar"),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Price over MRP: at 320dp a side-by-side pair wrapped the MRP under the price and clipped it.
        Column(Modifier.weight(1f)) {
            if (f.price != null) {
                Text(f.price, fontSize = 24.sp, fontWeight = FontWeight.Bold, color = TazColors.TextPrimary, maxLines = 1)
                f.mrp?.let { Text(it, fontSize = TazType.captionSize, color = TazColors.TextTertiary, textDecoration = TextDecoration.LineThrough, maxLines = 1) }
            } else Text(PdpCopy.PRICE_UNAVAILABLE, fontSize = TazType.bodySize, color = TazColors.TextTertiary, maxLines = 2)
        }
        Spacer(Modifier.width(TazSpace.md))
        RemoteAddControl(product, bar = true)
    }
}

// ---- skeleton ---------------------------------------------------------------------------------------------------------

/** PDP-shaped: hero, eyebrow, two title lines, price, a details block and the purchase bar. */
@Composable
private fun PdpSkeleton() {
    Column(Modifier.fillMaxSize().testTag("pdpSkeleton")) {
        Box(Modifier.fillMaxWidth().aspectRatio(PDP_HERO_ASPECT).background(TazColors.CreamStrong))
        Column(Modifier.fillMaxWidth().padding(horizontal = TazSpace.xl, vertical = TazSpace.xl), verticalArrangement = Arrangement.spacedBy(TazSpace.md)) {
            SkeletonBlock(width = 110.dp, height = 10.dp)
            Row(Modifier.fillMaxWidth()) {
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(TazSpace.sm)) { SkeletonBlock(height = 26.dp); SkeletonBlock(width = 160.dp, height = 26.dp) }
                Spacer(Modifier.width(TazSpace.lg))
                Column(horizontalAlignment = Alignment.End, verticalArrangement = Arrangement.spacedBy(TazSpace.sm)) { SkeletonBlock(width = 70.dp, height = 22.dp, corner = 11.dp); SkeletonBlock(width = 90.dp, height = 28.dp) }
            }
            Spacer(Modifier.height(TazSpace.lg))
            SkeletonBlock(height = 120.dp, corner = TazRadius.tileDp)
        }
        Spacer(Modifier.weight(1f))
        Row(Modifier.fillMaxWidth().padding(horizontal = TazSpace.lg).navigationBarsPadding().padding(bottom = TazSpace.md), verticalAlignment = Alignment.CenterVertically) {
            SkeletonBlock(width = 90.dp, height = 24.dp)
            Spacer(Modifier.weight(1f))
            SkeletonBlock(width = 180.dp, height = 54.dp, corner = 27.dp)
        }
    }
}

private val PURCHASE_BAR_CLEARANCE = 96.dp
