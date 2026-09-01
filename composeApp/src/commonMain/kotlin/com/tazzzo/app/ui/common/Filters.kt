package com.tazzzo.app.ui.common

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import com.tazzzo.app.data.model.Product
import com.tazzzo.app.theme.TazColors
import com.tazzzo.app.theme.TazIcons
import com.tazzzo.app.theme.TazRadius
import com.tazzzo.app.theme.TazSize
import com.tazzzo.app.theme.TazSpace
import com.tazzzo.app.theme.TazType
import androidx.compose.ui.unit.dp

/**
 * Client-side filtering/sorting over a loaded product list.
 *
 * Contract note: with a real catalogue these become query parameters on the
 * listing/search endpoints (sort=, in_stock=, brand=, max_price=) and the
 * server does the work — this type is the single place that mapping happens,
 * so screens stay unchanged.
 */
data class ProductFilters(
    val sort: SortOption = SortOption.RELEVANCE,
    val inStockOnly: Boolean = false,
    val brands: Set<String> = emptySet()
) {
    val activeCount: Int
        get() = (if (inStockOnly) 1 else 0) + brands.size +
            (if (sort != SortOption.RELEVANCE) 1 else 0)
}

enum class SortOption(val label: String) {
    RELEVANCE("Relevance"),
    PRICE_LOW("Price: low to high"),
    PRICE_HIGH("Price: high to low"),
    DISCOUNT("Biggest discount"),
    RATING("Top rated")
}

fun List<Product>.applyFilters(f: ProductFilters): List<Product> {
    var out = this
    if (f.inStockOnly) out = out.filter { it.isPurchasable }
    if (f.brands.isNotEmpty()) out = out.filter { it.brand in f.brands }
    out = when (f.sort) {
        SortOption.RELEVANCE -> out
        SortOption.PRICE_LOW -> out.sortedBy { it.price }
        SortOption.PRICE_HIGH -> out.sortedByDescending { it.price }
        SortOption.DISCOUNT -> out.sortedByDescending { it.discountPercent }
        SortOption.RATING -> out.sortedByDescending { it.rating }
    }
    return out
}

// ---------------------------------------------------------------------------
// UI
// ---------------------------------------------------------------------------

/** Horizontal chip bar: sort entry point + quick toggles. */
@Composable
fun FilterBar(
    filters: ProductFilters,
    brands: List<String>,
    onChange: (ProductFilters) -> Unit,
    onOpenSort: () -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier.fillMaxWidth().horizontalScroll(rememberScrollState())
            .padding(horizontal = TazSpace.gutter, vertical = TazSpace.sm),
        horizontalArrangement = Arrangement.spacedBy(TazSpace.sm)
    ) {
        FilterChip(
            label = if (filters.sort == SortOption.RELEVANCE) "Sort" else filters.sort.label,
            selected = filters.sort != SortOption.RELEVANCE,
            leading = TazIcons.Filter,
            trailing = TazIcons.Dropdown,
            onClick = onOpenSort
        )
        FilterChip(
            label = "In stock",
            selected = filters.inStockOnly,
            leading = if (filters.inStockOnly) TazIcons.Check else null,
            onClick = { onChange(filters.copy(inStockOnly = !filters.inStockOnly)) }
        )
        brands.forEach { brand ->
            FilterChip(
                label = brand,
                selected = brand in filters.brands,
                leading = if (brand in filters.brands) TazIcons.Check else null,
                onClick = {
                    val next = if (brand in filters.brands) filters.brands - brand else filters.brands + brand
                    onChange(filters.copy(brands = next))
                }
            )
        }
    }
}

@Composable
private fun FilterChip(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
    leading: ImageVector? = null,
    trailing: ImageVector? = null
) {
    val ink = if (selected) TazColors.White else TazColors.TextPrimary
    Row(
        Modifier.height(TazSize.chipHeight)
            .clip(TazRadius.pill)
            .background(if (selected) TazColors.Green else TazColors.Surface)
            .border(
                BorderStroke(1.dp, if (selected) TazColors.Green else TazColors.CardBorder),
                TazRadius.pill
            )
            .clickable { onClick() }
            .padding(horizontal = TazSpace.md),
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (leading != null) {
            TazIcon(leading, null, size = TazSize.iconXs, tint = ink)
            Spacer(Modifier.width(TazSpace.xs + 2.dp))
        }
        Text(
            label,
            color = ink,
            fontSize = TazType.captionSize,
            fontWeight = FontWeight.SemiBold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
        if (trailing != null) {
            Spacer(Modifier.width(TazSpace.xxs))
            TazIcon(trailing, null, size = TazSize.iconSm, tint = ink)
        }
    }
}

/** Sort picker sheet. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SortSheet(
    current: SortOption,
    onSelect: (SortOption) -> Unit,
    onDismiss: () -> Unit
) {
    ModalBottomSheet(onDismissRequest = onDismiss, containerColor = TazColors.Surface) {
        Column(Modifier.padding(TazSpace.xl).navigationBarsPadding()) {
            Text("Sort by", fontSize = TazType.h2Size, fontWeight = TazType.h2Weight,
                color = TazColors.TextPrimary)
            Spacer(Modifier.height(TazSpace.md))
            SortOption.entries.forEach { option ->
                Row(
                    Modifier.fillMaxWidth().clip(TazRadius.chip)
                        .clickable { onSelect(option); onDismiss() }
                        .padding(vertical = TazSpace.md, horizontal = TazSpace.xs),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        Modifier.size(TazSpace.xl).clip(CircleShape)
                            .background(if (option == current) TazColors.Green else TazColors.Surface)
                            .border(
                                BorderStroke(
                                    1.5.dp,
                                    if (option == current) TazColors.Green else TazColors.BorderStrong
                                ),
                                CircleShape
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        if (option == current) {
                            TazIcon(
                                TazIcons.Check, null,
                                size = TazSize.iconXs, tint = TazColors.White
                            )
                        }
                    }
                    Spacer(Modifier.width(TazSpace.md))
                    Text(
                        option.label, fontSize = TazType.titleSize,
                        fontWeight = if (option == current) FontWeight.Bold else FontWeight.Normal,
                        color = TazColors.TextPrimary,
                        maxLines = 1, overflow = TextOverflow.Ellipsis
                    )
                }
            }
        }
    }
}
