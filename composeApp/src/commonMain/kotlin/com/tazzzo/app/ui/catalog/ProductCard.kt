package com.tazzzo.app.ui.catalog

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.tazzzo.app.data.catalog.CatalogProduct
import com.tazzzo.app.data.catalog.StockTone
import com.tazzzo.app.theme.TazColors
import com.tazzzo.app.theme.TazRadius
import com.tazzzo.app.theme.TazSpace
import com.tazzzo.app.theme.TazType
import com.tazzzo.app.ui.cart.RemoteAddControl
import com.tazzzo.app.ui.common.CatalogProductImage
import com.tazzzo.app.ui.interaction.TazPress
import com.tazzzo.app.ui.interaction.tazPressable

/**
 * THE product card (UI-03). One composable for the Home rail, the PLP grid and search results, so the three never
 * drift: a soft sunken image well with the real photo through the shared pipeline, a discount chip only when the
 * server sent a discount, the name, the selling price with the MRP struck through only when it really is higher,
 * and the compact deep-green add → stepper bound to the SERVER cart. No pack size (the backend has none), no
 * rating, no ETA, no invented stock copy.
 *
 * @param width fixed for a horizontal rail; null fills the grid cell.
 * @param wellShape the Home reference draws round wells in its rail; the grid uses the rounded tile. Same card.
 */
@Composable
fun TazProductCard(
    product: CatalogProduct,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    width: Dp? = null,
    wellShape: Shape = TazRadius.tile
) {
    val f = product.cardFacts()
    Column(
        (if (width != null) modifier.width(width) else modifier.fillMaxWidth())
            .clip(TazRadius.card)
            .tazPressable(onClick = onClick, pressScale = TazPress.card)
            .semantics { contentDescription = product.name }
            .padding(TazSpace.xs)
    ) {
        Box(Modifier.fillMaxWidth()) {
            CatalogProductImage(
                url = f.imageUrl, name = product.name,
                modifier = Modifier.fillMaxWidth().clip(wellShape),
                contentPadding = if (wellShape == CircleShape) 10.dp else 6.dp
            )
            f.discount?.let { d ->
                Box(
                    Modifier.align(Alignment.TopEnd).padding(TazSpace.xs).clip(TazRadius.pill).background(TazColors.GreenSoft)
                        .padding(horizontal = TazSpace.sm, vertical = 3.dp)
                ) { Text(d, fontSize = 10.sp, fontWeight = FontWeight.SemiBold, color = TazColors.Success, maxLines = 1) }
            }
        }
        Spacer(Modifier.height(TazSpace.sm))
        Text(
            product.name, fontSize = TazType.productNameSize, lineHeight = TazType.productNameLine, fontWeight = TazType.productNameWeight,
            color = TazColors.TextPrimary, maxLines = 2, minLines = 2, overflow = TextOverflow.Ellipsis
        )
        f.stockNote?.let {
            Text(
                it, fontSize = TazType.microSize, fontWeight = FontWeight.SemiBold,
                color = if (f.stockTone == StockTone.Unavailable) TazColors.Danger else TazColors.Warning, maxLines = 1
            )
        }
        Spacer(Modifier.height(TazSpace.xs))
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
            Column(Modifier.weight(1f, fill = false)) {
                if (f.price != null) {
                    Text(f.price, fontSize = TazType.priceSize, fontWeight = TazType.priceWeight, color = TazColors.TextPrimary, maxLines = 1)
                    f.mrp?.let { Text(it, fontSize = TazType.mrpSize, color = TazColors.TextTertiary, textDecoration = TextDecoration.LineThrough, maxLines = 1) }
                } else {
                    Text(ShopCopy.PRICE_UNAVAILABLE, fontSize = TazType.captionSize, color = TazColors.TextTertiary, maxLines = 1)
                }
            }
            Spacer(Modifier.width(TazSpace.sm))
            RemoteAddControl(product, compact = true)
        }
    }
}
