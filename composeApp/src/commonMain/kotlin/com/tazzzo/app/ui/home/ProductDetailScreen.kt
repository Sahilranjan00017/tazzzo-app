package com.tazzzo.app.ui.home

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.tazzzo.app.LocalAppState
import com.tazzzo.app.analytics.Analytics
import com.tazzzo.app.analytics.AnalyticsEvents
import com.tazzzo.app.config.AppConfig
import com.tazzzo.app.config.DeliveryCopy
import com.tazzzo.app.data.model.Availability
import com.tazzzo.app.data.model.Product
import com.tazzzo.app.data.repository.ServiceLocator
import com.tazzzo.app.data.repository.Taxonomy
import com.tazzzo.app.theme.TazColors
import com.tazzzo.app.theme.TazIcons
import com.tazzzo.app.theme.TazRadius
import com.tazzzo.app.theme.TazSize
import com.tazzzo.app.theme.TazSpace
import com.tazzzo.app.theme.TazType
import com.tazzzo.app.ui.common.ProductImage
import com.tazzzo.app.ui.common.ProductRail
import com.tazzzo.app.ui.common.QuantityStepper
import com.tazzzo.app.ui.common.RatingRow
import com.tazzzo.app.ui.common.TazIcon
import com.tazzzo.app.ui.common.TazTopBar
import com.tazzzo.app.ui.state.rememberLoad
import com.tazzzo.app.ui.common.EmptyState
import com.tazzzo.app.ui.common.StateHost

/**
 * Product detail page.
 *
 * Loads through the repository (not MockCatalog) so it keeps working when the
 * catalogue moves to the backend. Availability and quantity limits come from
 * the product model — the same rules the cart enforces.
 *
 * Visual structure: a flat sunken hero well (the product is the only colour),
 * a cream identity block, white information cards, and a pinned purchase
 * footer that carries the price and the stepper at every scroll position.
 */
@Composable
fun ProductDetailScreen(productId: String) {
    val app = LocalAppState.current

    androidx.compose.runtime.LaunchedEffect(productId) {
        Analytics.track(AnalyticsEvents.PRODUCT_VIEW, mapOf("product_id" to productId))
    }

    val load = rememberLoad(productId, isEmpty = { it == null }) {
        ServiceLocator.catalog.getProduct(productId)?.let { product ->
            val similar = ServiceLocator.catalog
                .getProducts(product.categoryId)
                .filter { it.id != product.id }
            val categoryName = Taxonomy.categories()
                .find { it.id == product.categoryId }?.name
            ProductDetailData(product, similar, categoryName)
        }
    }

    // No CartBar overlay here: the pinned purchase footer already shows the
    // live quantity for this product, and the two bars would fight for the
    // same bottom edge.
    Column(Modifier.fillMaxSize().background(TazColors.Cream)) {
        TazTopBar("Product details", onBack = { app.back() })
        StateHost(
            load,
            modifier = Modifier.fillMaxSize(),
            empty = {
                EmptyState(
                    "🔍", "Product not found",
                    "It may have been removed from the catalogue.",
                    actionLabel = "Browse products", onAction = { app.back() }
                )
            }
        ) { data -> data?.let { ProductDetailContent(it) } }
    }
}

private data class ProductDetailData(
    val product: Product,
    val similar: List<Product>,
    val categoryName: String?
)

/** Hero well height — the single largest visual commitment on the page. */
private val HeroHeight = 280.dp
/** PDP hero container aspect — fixed so packshots never dictate page height. */
private const val HeroAspect = 1.4f

/** Product art size inside the well. The emoji is CONTENT, not iconography. */
private val HeroEmojiSize = 120.sp

@Composable
private fun ProductDetailContent(data: ProductDetailData) {
    val product = data.product
    // Scrollable content on top; the purchase controls live in a pinned
    // footer below it, so price + stepper are always reachable.
    Column(Modifier.fillMaxSize().background(TazColors.Cream)) {
        Column(Modifier.weight(1f).verticalScroll(rememberScrollState())) {

            // ---- hero well ------------------------------------------------
            // Flat sunken panel: nothing competes with the produce itself.
            ProductImage(
                product = product,
                modifier = Modifier.fillMaxWidth(),
                aspectRatio = HeroAspect,
                glyphSize = HeroEmojiSize,
                contentPadding = TazSpace.xxl,
                dimmed = !product.isPurchasable
            ) {
                if (!product.isPurchasable) {
                    Box(
                        Modifier.clip(TazRadius.pill)
                            .background(TazColors.Scrim)
                            .padding(horizontal = TazSpace.lg, vertical = TazSpace.sm)
                    ) {
                        Text(
                            if (product.availability == Availability.NotServiceable)
                                "Unavailable in your area" else "Currently out of stock",
                            color = TazColors.White,
                            fontSize = TazType.titleSize, fontWeight = TazType.titleWeight,
                            maxLines = 1, overflow = TextOverflow.Ellipsis
                        )
                    }
                }
            }
            Hairline()

            Column(Modifier.padding(horizontal = TazSpace.gutter)) {
                Spacer(Modifier.height(TazSpace.lg))

                // ---- identity -------------------------------------------
                if (data.categoryName != null) {
                    Text(
                        data.categoryName.uppercase(),
                        fontSize = TazType.microSize, fontWeight = TazType.microWeight,
                        letterSpacing = TazType.labelTracking, color = TazColors.GreenMid,
                        maxLines = 1, overflow = TextOverflow.Ellipsis
                    )
                    Spacer(Modifier.height(TazSpace.sm))
                }
                Text(
                    product.name,
                    fontSize = TazType.h1Size, fontWeight = TazType.h1Weight,
                    color = TazColors.TextPrimary, lineHeight = TazType.h1Line
                )
                Spacer(Modifier.height(TazSpace.xs))
                Text(
                    "${product.brand} · ${product.unit}",
                    fontSize = TazType.bodySize, color = TazColors.TextSecondary,
                    lineHeight = TazType.bodyLine
                )
                Spacer(Modifier.height(TazSpace.sm))
                // Self-hides when no rating source exists — never a fabricated score.
                RatingRow(product.rating, product.ratingCount)

                // Config-sourced promise only: renders nothing when unverified.
                DeliveryCopy.short(AppConfig.deliveryPromise)?.let { eta ->
                    Spacer(Modifier.height(TazSpace.sm))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        TazIcon(
                            TazIcons.Slot, null,
                            size = TazSize.iconXs, tint = TazColors.TextSecondary
                        )
                        Spacer(Modifier.width(TazSpace.xs))
                        Text(
                            "Delivery in $eta", fontSize = TazType.captionSize,
                            color = TazColors.TextSecondary
                        )
                    }
                }

                // ---- stock hint (from the model, never invented) ---------
                val stock = product.availability
                if (stock is Availability.LowStock) {
                    Spacer(Modifier.height(TazSpace.md))
                    Row(
                        Modifier.clip(TazRadius.chip).background(TazColors.WarningSoft)
                            .padding(horizontal = TazSpace.sm, vertical = TazSpace.xs),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        TazIcon(
                            TazIcons.Error, null,
                            size = TazSize.iconXs, tint = TazColors.Warning
                        )
                        Spacer(Modifier.width(TazSpace.xs))
                        Text(
                            "Only ${stock.remaining} left at this price",
                            fontSize = TazType.captionSize, fontWeight = TazType.savingsWeight,
                            color = TazColors.Warning, maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }

                // ---- information cards ------------------------------------
                if (product.highlights.isNotEmpty()) {
                    Spacer(Modifier.height(TazSpace.xl))
                    InfoCard("Why you'll like it") {
                        product.highlights.forEachIndexed { index, highlight ->
                            if (index > 0) Spacer(Modifier.height(TazSpace.sm))
                            Row(verticalAlignment = Alignment.Top) {
                                TazIcon(
                                    TazIcons.Check, null,
                                    modifier = Modifier.padding(top = TazSpace.xxs),
                                    size = TazSize.iconSm, tint = TazColors.Success
                                )
                                Spacer(Modifier.width(TazSpace.sm))
                                Text(
                                    highlight, fontSize = TazType.bodySize,
                                    color = TazColors.TextPrimary, lineHeight = TazType.bodyLine
                                )
                            }
                        }
                    }
                    Spacer(Modifier.height(TazSpace.md))
                } else {
                    Spacer(Modifier.height(TazSpace.xl))
                }

                InfoCard("Good to know") {
                    InfoRow("Unit", product.unit)
                    Spacer(Modifier.height(TazSpace.md))
                    InfoRow("Brand", product.brand)
                    Spacer(Modifier.height(TazSpace.md))
                    InfoRow("Max per order", "${product.maxOrderQuantity}")
                    Spacer(Modifier.height(TazSpace.md))
                    InfoRow(
                        "Availability",
                        when (val a = product.availability) {
                            Availability.InStock -> "In stock"
                            is Availability.LowStock -> "Low stock (${a.remaining} left)"
                            Availability.OutOfStock -> "Out of stock"
                            Availability.NotServiceable -> "Not available in your area"
                        }
                    )
                }
            }

            // ---- similar products ----------------------------------------
            if (data.similar.isNotEmpty()) {
                Spacer(Modifier.height(TazSpace.xxl))
                ProductRail("Similar products", data.similar)
            }
            Spacer(Modifier.height(TazSpace.xl))
        }

        PurchaseFooter(product)
    }
}

// ---------------------------------------------------------------------------
// Pinned purchase footer
// ---------------------------------------------------------------------------

/**
 * The price and the control never scroll away. The upward shadow and the top
 * hairline make the bar read as a layer of the page rather than a strip that
 * was bolted onto the bottom of it.
 */
@Composable
private fun PurchaseFooter(product: Product) {
    Column(
        Modifier.fillMaxWidth()
            .shadow(12.dp, spotColor = Color.Black.copy(alpha = 0.30f))
            .background(TazColors.Surface)
    ) {
        Hairline()
        Row(
            Modifier.fillMaxWidth().navigationBarsPadding().padding(TazSpace.lg),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.Bottom) {
                    Text(
                        "₹${product.price}",
                        fontSize = TazType.priceHeroSize, fontWeight = FontWeight.Bold,
                        color = TazColors.TextPrimary, maxLines = 1
                    )
                    if (product.mrp > product.price) {
                        Spacer(Modifier.width(TazSpace.sm))
                        Text(
                            "₹${product.mrp}",
                            fontSize = TazType.mrpSize, color = TazColors.TextTertiary,
                            textDecoration = TextDecoration.LineThrough,
                            maxLines = 1, modifier = Modifier.padding(bottom = TazSpace.xxs)
                        )
                    }
                }
                if (product.mrp > product.price) {
                    Spacer(Modifier.height(TazSpace.xxs))
                    Text(
                        "You save ₹${product.mrp - product.price} (${product.discountPercent}% off)",
                        fontSize = TazType.savingsSize, fontWeight = TazType.savingsWeight,
                        color = TazColors.Success, maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
            Spacer(Modifier.width(TazSpace.md))
            QuantityStepper(product)
        }
    }
}

// ---------------------------------------------------------------------------
// Small building blocks
// ---------------------------------------------------------------------------

/** The 1dp rule used to separate layers without reaching for a shadow. */
@Composable
private fun Hairline() {
    Box(Modifier.fillMaxWidth().height(1.dp).background(TazColors.CardBorder))
}

@Composable
private fun InfoCard(title: String, content: @Composable ColumnScope.() -> Unit) {
    Column(
        Modifier.fillMaxWidth()
            .clip(TazRadius.card)
            .background(TazColors.Surface)
            .border(BorderStroke(1.dp, TazColors.CardBorder), TazRadius.card)
            .padding(TazSpace.lg)
    ) {
        Text(
            title, fontSize = TazType.titleSize, fontWeight = TazType.titleWeight,
            color = TazColors.TextPrimary, lineHeight = TazType.titleLine
        )
        Spacer(Modifier.height(TazSpace.md))
        content()
    }
}

@Composable
private fun InfoRow(label: String, value: String) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.Top) {
        Text(
            label, fontSize = TazType.bodySize, color = TazColors.TextSecondary,
            lineHeight = TazType.bodyLine, modifier = Modifier.weight(1f)
        )
        Spacer(Modifier.width(TazSpace.md))
        Text(
            value, fontSize = TazType.bodySize, fontWeight = FontWeight.SemiBold,
            color = TazColors.TextPrimary, lineHeight = TazType.bodyLine
        )
    }
}
