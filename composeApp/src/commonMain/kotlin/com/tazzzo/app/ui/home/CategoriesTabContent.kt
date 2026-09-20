package com.tazzzo.app.ui.home

import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import com.tazzzo.app.LocalAppState
import com.tazzzo.app.Screen
import com.tazzzo.app.config.PromotionConfig
import com.tazzzo.app.data.repository.ServiceLocator
import com.tazzzo.app.data.repository.Taxonomy
import com.tazzzo.app.theme.TazColors
import com.tazzzo.app.theme.TazIcons
import com.tazzzo.app.theme.TazRadius
import com.tazzzo.app.theme.TazSize
import com.tazzzo.app.theme.TazSpace
import com.tazzzo.app.theme.TazType
import com.tazzzo.app.ui.common.CategoryTile
import com.tazzzo.app.ui.common.ChipTone
import com.tazzzo.app.ui.common.SkeletonBlock
import com.tazzzo.app.ui.common.StateHost
import com.tazzzo.app.ui.common.TazChip
import com.tazzzo.app.ui.common.TazIcon
import com.tazzzo.app.ui.interaction.TazPress
import com.tazzzo.app.ui.interaction.tazPressable
import com.tazzzo.app.ui.state.UiState
import com.tazzzo.app.ui.state.rememberLoad
import androidx.compose.ui.platform.testTag

private val TileSize: Dp = 74.dp
private val GroupGap: Dp = 24.dp
private val RowGap: Dp = 12.dp
private const val AllGroups = "All"

/**
 * "Categories" bottom tab — every aisle, grouped, with an honest depth count.
 *
 * Follows the supplied redesign: a search entry, a row of group pills that
 * actually filter, a promotion spotlight, then per-group sections whose tiles
 * carry a count.
 *
 * The mockup's counts ("340+", "1,840 Items") were comp values. Every number
 * here is counted from the catalogue, so the screen can never advertise an
 * aisle deeper than it is.
 *
 * Two mockup controls are deliberately absent. Its filter button beside the
 * search field has nothing to filter at this level — filters belong to a result
 * list, and Search already owns them. Its per-group "See All" is redundant once
 * the group pills filter, which is the same action with better feedback.
 */
@Composable
fun CategoriesTabContent() {
    val app = LocalAppState.current
    val categories = rememberLoad { Taxonomy.categories() }
    val counts = rememberLoad { ServiceLocator.catalog.getCounts() }
    val countMap = (counts.state as? UiState.Success)?.data ?: emptyMap()

    // Survives navigation: opening an aisle and coming back must not silently
    // throw away the group the customer chose.
    var selectedGroup by rememberSaveable { mutableStateOf(AllGroups) }

    Column(Modifier.fillMaxSize().background(TazColors.Cream)) {
        Column(
            Modifier.fillMaxWidth().zIndex(1f)
                .shadow(4.dp, spotColor = Color.Black.copy(alpha = 0.16f))
                .background(TazColors.Surface)
                .statusBarsPadding()
                .padding(top = TazSpace.md, bottom = TazSpace.md)
        ) {
            Text(
                "All categories",
                fontSize = TazType.h1Size, fontWeight = TazType.h1Weight,
                lineHeight = TazType.h1Line, color = TazColors.TextPrimary,
                modifier = Modifier.padding(horizontal = TazSpace.gutter)
            )
            Spacer(Modifier.height(TazSpace.sm))

            // Search is an entry point, not a field: tapping goes to Search,
            // which owns the query, the suggestions and the filters.
            Row(
                Modifier.fillMaxWidth().padding(horizontal = TazSpace.gutter)
                    .clip(TazRadius.card).background(TazColors.SurfaceSunken)
                    .semantics(mergeDescendants = true) { contentDescription = "Search products" }
                    .tazPressable(
                        onClick = { app.navigate(Screen.Search) },
                        pressScale = TazPress.compact, shape = TazRadius.card
                    )
                    .heightIn(min = TazSize.touchTarget)
                    .padding(horizontal = TazSpace.md, vertical = TazSpace.sm),
                verticalAlignment = Alignment.CenterVertically
            ) {
                TazIcon(TazIcons.Search, null, size = TazSize.iconSm, tint = TazColors.TextTertiary)
                Spacer(Modifier.width(TazSpace.sm))
                Text(
                    "Search milk, atta, onion…", fontSize = TazType.bodySize,
                    color = TazColors.TextTertiary, maxLines = 1, overflow = TextOverflow.Ellipsis
                )
            }
        }

        StateHost(
            handle = categories,
            modifier = Modifier.weight(1f).fillMaxWidth(),
            loading = { CategoriesSkeleton() }
        ) { list ->
            val groups = list.map { it.group }.distinct()
            val visible =
                if (selectedGroup == AllGroups) list else list.filter { it.group == selectedGroup }
            val shownGroups = visible.map { it.group }.distinct()

            LazyColumn(
                // Tagged so a test can scroll this list deterministically: the
                // group headers below the fold are not composed until they are,
                // and "assert it is present" would be asserting on the viewport
                // rather than on the screen.
                Modifier.fillMaxSize().testTag("categoriesList"),
                contentPadding = PaddingValues(bottom = TazSpace.cartBarClearance)
            ) {
                item(key = "pills") {
                    GroupPills(
                        groups = groups,
                        selected = selectedGroup,
                        counts = countMap,
                        categories = list,
                        onSelect = { selectedGroup = it }
                    )
                }
                item(key = "spotlight") { PromotionSpotlight() }

                shownGroups.forEach { group ->
                    val inGroup = visible.filter { it.group == group }
                    item(key = "header-$group") {
                        GroupHeader(
                            title = group,
                            items = inGroup.sumOf { countMap[it.id] ?: 0 }
                        )
                    }
                    items(inGroup.chunked(4)) { row ->
                        Row(
                            Modifier.fillMaxWidth()
                                .padding(horizontal = TazSpace.screenEdge, vertical = RowGap / 2),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            row.forEach { cat ->
                                TileCell {
                                    CategoryTile(
                                        cat,
                                        size = TileSize,
                                        count = countMap[cat.id],
                                        onClick = { app.navigate(Screen.CategoryDetail(cat.id)) }
                                    )
                                }
                            }
                            repeat(4 - row.size) { Spacer(Modifier.weight(1f)) }
                        }
                    }
                    item(key = "gap-$group") { Spacer(Modifier.height(GroupGap)) }
                }
            }
        }
    }
}

/**
 * The group filter.
 *
 * Selection lives in `Modifier.selectable` so a screen reader announces the
 * chosen pill as selected rather than as one of five identical buttons.
 */
@Composable
private fun GroupPills(
    groups: List<String>,
    selected: String,
    counts: Map<String, Int>,
    categories: List<com.tazzzo.app.data.model.Category>,
    onSelect: (String) -> Unit,
) {
    Row(
        Modifier.fillMaxWidth().horizontalScroll(rememberScrollState())
            .padding(horizontal = TazSpace.screenEdge, vertical = TazSpace.sm),
        horizontalArrangement = Arrangement.spacedBy(TazSpace.sm)
    ) {
        GroupPill(AllGroups, selected == AllGroups) { onSelect(AllGroups) }
        groups.forEach { g ->
            GroupPill(g, selected == g) { onSelect(g) }
        }
    }
}

@Composable
private fun GroupPill(label: String, selected: Boolean, onClick: () -> Unit) {
    Row(
        Modifier.heightIn(min = TazSize.chipHeight).clip(TazRadius.pill)
            .background(if (selected) TazColors.Green else TazColors.Surface)
            .tazPressable(
                onClick = onClick, pressScale = TazPress.compact,
                shape = TazRadius.pill, role = Role.Tab, selected = selected
            )
            .padding(horizontal = TazSpace.md, vertical = TazSpace.sm),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            label, fontSize = TazType.captionSize, fontWeight = FontWeight.SemiBold,
            color = if (selected) TazColors.White else TazColors.TextSecondary,
            maxLines = 1, overflow = TextOverflow.Ellipsis
        )
    }
}

/** Group title with the number of SKUs actually behind it. */
@Composable
private fun GroupHeader(title: String, items: Int) {
    Row(
        Modifier.fillMaxWidth()
            .padding(horizontal = TazSpace.screenEdge, vertical = TazSpace.sm),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            title, fontSize = TazType.h2Size, fontWeight = TazType.h2Weight,
            color = TazColors.TextPrimary, maxLines = 1, overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f, fill = false)
        )
        if (items > 0) {
            Spacer(Modifier.width(TazSpace.sm))
            // standalone: this chip is the only place the aisle's depth is
            // stated, so suppressing its semantics would hide the number from
            // a screen reader entirely.
            TazChip(
                if (items == 1) "1 item" else "$items items",
                ChipTone.Brand, standalone = true
            )
        }
    }
}

/**
 * The promotion spotlight, driven by the real promotion set.
 *
 * The mockup hard-codes "Flat ₹100 OFF … code FRESH100". This renders whatever
 * coupon is genuinely live, with the minimum spend that gates it, so the banner
 * and the cart can never disagree. No live coupon means no banner.
 */
@Composable
private fun PromotionSpotlight() {
    val coupon = PromotionConfig.active.firstOrNull { it.isCoupon } ?: return
    Column(
        Modifier.fillMaxWidth().padding(horizontal = TazSpace.screenEdge, vertical = TazSpace.sm)
            .clip(TazRadius.card).background(TazColors.Green).padding(TazSpace.lg)
    ) {
        Text(
            coupon.description, fontSize = TazType.titleSize, fontWeight = TazType.titleWeight,
            lineHeight = TazType.titleLine, color = TazColors.White,
            maxLines = 2, overflow = TextOverflow.Ellipsis
        )
        Spacer(Modifier.height(TazSpace.sm))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                "Use code", fontSize = TazType.captionSize,
                color = TazColors.White.copy(alpha = 0.85f)
            )
            Spacer(Modifier.width(TazSpace.sm))
            Box(
                Modifier.clip(TazRadius.chip).background(TazColors.White.copy(alpha = 0.18f))
                    .padding(horizontal = TazSpace.sm, vertical = TazSpace.xxs)
            ) {
                Text(
                    coupon.couponCode.orEmpty(), fontSize = TazType.captionSize,
                    fontWeight = FontWeight.Bold, color = TazColors.White, maxLines = 1
                )
            }
            coupon.validUntilLabel?.let {
                Spacer(Modifier.width(TazSpace.sm))
                Text(
                    "until $it", fontSize = TazType.microSize,
                    color = TazColors.White.copy(alpha = 0.85f), maxLines = 1
                )
            }
        }
    }
}

/** One of four equal columns — the tile centres in it and can never overflow. */
@Composable
private fun RowScope.TileCell(content: @Composable () -> Unit) {
    Box(Modifier.weight(1f), contentAlignment = Alignment.Center) { content() }
}

/** Shimmering tile grid matching the loaded layout's silhouette exactly. */
@Composable
private fun CategoriesSkeleton() {
    Column(
        Modifier.fillMaxWidth().padding(
            start = TazSpace.screenEdge, end = TazSpace.screenEdge, top = TazSpace.lg
        ),
        verticalArrangement = Arrangement.spacedBy(GroupGap)
    ) {
        repeat(2) {
            Column(verticalArrangement = Arrangement.spacedBy(RowGap)) {
                SkeletonBlock(width = 140.dp, height = 20.dp)
                repeat(2) {
                    Row(
                        Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        repeat(4) {
                            Column(
                                Modifier.weight(1f),
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                SkeletonBlock(
                                    width = TileSize, height = TileSize,
                                    corner = TazRadius.tileDp
                                )
                                Spacer(Modifier.height(TazSpace.sm))
                                SkeletonBlock(width = 52.dp, height = 10.dp)
                            }
                        }
                    }
                }
            }
        }
    }
}
