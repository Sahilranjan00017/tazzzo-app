package com.tazzzo.app.ui.common

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.tazzzo.app.data.model.Product
import com.tazzzo.app.theme.TazColors

/**
 * The ONE way a product image is rendered anywhere in Tazzzo.
 *
 * Why this exists as architecture rather than an `Image()` call: the catalogue
 * backend will eventually serve real packshots, and every surface that shows a
 * product (card, PDP hero, cart row, order line, rail) must then pick them up
 * without any screen being redesigned. Everything that varies between those
 * surfaces is a parameter here; everything that must NOT vary — the container,
 * the crop rule, the fallback, the loading treatment — is fixed here.
 *
 * Rules encoded:
 *  - Fixed aspect container. An image never dictates layout height, so a
 *    portrait bottle and a landscape pack produce identically sized cards.
 *  - `ContentScale.Fit` with padding, never `Crop`, for packshots: cropping a
 *    product photo cuts off the brand and is the classic way grocery grids end
 *    up looking broken. Nothing is ever stretched — Fit preserves aspect.
 *  - A consistent, neutral ground (`SurfaceSunken`) behind every image so
 *    photos shot on different backgrounds still sit in a uniform grid.
 *  - Three explicit states: loading (skeleton), loaded, and missing/failed
 *    (graceful fallback) — the missing state is a first-class design, not an
 *    accident.
 *
 * TEMPORARY DEMO IMAGERY: until [Product.imageUrl] is populated by the
 * catalogue backend, this falls back to the product's emoji glyph. That glyph
 * is a DEVELOPMENT PLACEHOLDER and must not be presented as production
 * photography — see docs/DESIGN_SPEC.md and the P1 entry in BLOCKERS.md.
 */

/** Result of an image load attempt. */
sealed interface ProductImageState {
    data object Loading : ProductImageState
    data class Ready(val bitmap: ImageBitmap) : ProductImageState
    /** No URL, or the load failed — callers render the fallback. */
    data object Unavailable : ProductImageState
}

/**
 * Pluggable loader seam. Today the default returns [ProductImageState.Unavailable]
 * because there is no image backend and no networking layer yet — inventing one
 * would be fabrication. When the catalogue serves URLs, implement this ONCE
 * (Coil/Ktor) and provide it at the app root; every product surface updates.
 */
interface ProductImageLoader {
    suspend fun load(url: String): ProductImageState
}

object NoOpProductImageLoader : ProductImageLoader {
    override suspend fun load(url: String): ProductImageState = ProductImageState.Unavailable
}

val LocalProductImageLoader = staticCompositionLocalOf<ProductImageLoader> { NoOpProductImageLoader }

@Composable
fun ProvideProductImageLoader(loader: ProductImageLoader, content: @Composable () -> Unit) {
    CompositionLocalProvider(LocalProductImageLoader provides loader, content = content)
}

/**
 * Renders a product's image inside a fixed-aspect, neutral container.
 *
 * @param aspectRatio 1.52f for grid cards, 1.4f for the PDP hero (the real
 *        container ratios — see ProductCardImageAspect / HeroAspect).
 * @param glyphSize size of the fallback glyph — the only thing surfaces tune.
 */
@Composable
fun ProductImage(
    product: Product,
    modifier: Modifier = Modifier,
    aspectRatio: Float = 1f,
    glyphSize: androidx.compose.ui.unit.TextUnit = 44.sp,
    background: Color = TazColors.SurfaceSunken,
    contentPadding: Dp = 10.dp,
    dimmed: Boolean = false,
    overlay: @Composable BoxScope.() -> Unit = {}
) {
    val loader = LocalProductImageLoader.current
    val url = product.imageUrl

    var state by remember(product.id, url) {
        mutableStateOf(if (url.isNullOrBlank()) ProductImageState.Unavailable else ProductImageState.Loading)
    }

    LaunchedEffect(product.id, url) {
        if (!url.isNullOrBlank()) {
            state = try {
                loader.load(url)
            } catch (t: Throwable) {
                ProductImageState.Unavailable   // never crash a grid over an image
            }
        }
    }

    Box(
        modifier.aspectRatio(aspectRatio).background(background),
        contentAlignment = Alignment.Center
    ) {
        when (val s = state) {
            ProductImageState.Loading ->
                SkeletonBlock(modifier = Modifier.fillMaxSize(), corner = 0.dp)

            is ProductImageState.Ready ->
                Image(
                    bitmap = s.bitmap,
                    contentDescription = product.name,
                    modifier = Modifier.fillMaxSize().padding(contentPadding),
                    // Fit, never Crop: a cropped packshot loses the brand.
                    contentScale = ContentScale.Fit,
                    alpha = if (dimmed) 0.45f else 1f
                )

            ProductImageState.Unavailable ->
                // Development placeholder — NOT production photography.
                Text(
                    product.emoji,
                    fontSize = glyphSize,
                    modifier = Modifier.padding(contentPadding)
                        .alpha(if (dimmed) 0.45f else 1f)
                )
        }
        overlay()
    }
}
