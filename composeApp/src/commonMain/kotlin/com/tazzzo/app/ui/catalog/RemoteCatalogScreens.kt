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
import com.tazzzo.app.ui.common.EditorialEmptyState
import com.tazzzo.app.ui.common.LogoImage
import com.tazzzo.app.ui.common.PillButton
import com.tazzzo.app.ui.common.SkeletonBlock
import com.tazzzo.app.ui.common.TazChip
import com.tazzzo.app.ui.common.TazIcon
import com.tazzzo.app.ui.common.TazTopBar
import com.tazzzo.app.ui.interaction.TazPress
import com.tazzzo.app.ui.interaction.tazPressable

/*
 * REAL-catalogue product page (PDP, PR-04C) and the shared unavailable surface. Shop, browsing, the PLP and Search
 * moved to ShopScreen.kt / BrowseScreen.kt / RemoteSearchScreen.kt in UI-03; the PDP keeps its PR-04C layout until
 * UI-04 applies the `Veg Page.jpeg` reference. Everything observes state holders and renders only what the backend sent.
 */

// ---------------------------------------------------------------------------------------------------
// Taxonomy: sections (TZS) -> categories (TZC), children loaded lazily
// ---------------------------------------------------------------------------------------------------

@Composable
internal fun rememberTaxonomyBrowser(): TaxonomyBrowser {
    val scope = rememberCoroutineScope()
    return remember { TaxonomyBrowser(scope, ServiceLocator.remoteCatalog) }
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
                PdpState.NotFound -> EditorialEmptyState(TazIcons.Bag, "This product isn't available", "It may have been removed or isn't sold in your area.", "Go back", onAction = { app.back() })
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
                        com.tazzzo.app.ui.cart.RemoteAddControl(p)
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

/** A surface that has no backend source in REMOTE mode (checkout fallback, coins, club, shopping list). Never fed from mock data. */
@Composable
fun UnavailableSurface(name: String) {
    val app = LocalAppState.current
    Column(Modifier.fillMaxSize().background(TazColors.Cream)) {
        TazTopBar(title = name, onBack = { app.back() })
        EditorialEmptyState(TazIcons.Info, "$name isn't available yet", "We're working on it.", "Go back", onAction = { app.back() })
    }
}
