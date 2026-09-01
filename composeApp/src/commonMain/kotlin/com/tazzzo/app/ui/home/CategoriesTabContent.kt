package com.tazzzo.app.ui.home

import androidx.compose.foundation.background
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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import com.tazzzo.app.LocalAppState
import com.tazzzo.app.Screen
import com.tazzzo.app.data.repository.ServiceLocator
import com.tazzzo.app.data.repository.Taxonomy
import com.tazzzo.app.theme.TazColors
import com.tazzzo.app.theme.TazRadius
import com.tazzzo.app.theme.TazSpace
import com.tazzzo.app.theme.TazType
import com.tazzzo.app.ui.common.CategoryTile
import com.tazzzo.app.ui.common.SectionHeader
import com.tazzzo.app.ui.common.SkeletonBlock
import com.tazzzo.app.ui.common.StateHost
import com.tazzzo.app.ui.state.rememberLoad

private val TileSize: Dp = 74.dp
private val GroupGap: Dp = 24.dp
private val RowGap: Dp = 12.dp

/** "Categories" bottom tab — every category grouped by its top-level group. */
@Composable
fun CategoriesTabContent() {
    val app = LocalAppState.current
    val categories = rememberLoad { Taxonomy.categories() }

    Column(Modifier.fillMaxSize().background(TazColors.Cream)) {
        // ----- Header: a brand bar, same weight as home's -------------------
        Column(
            Modifier
                .fillMaxWidth()
                .zIndex(1f)
                .shadow(4.dp, spotColor = Color.Black.copy(alpha = 0.16f))
                .background(TazColors.Surface)
                .statusBarsPadding()
                .padding(horizontal = TazSpace.gutter)
                .padding(top = TazSpace.md, bottom = TazSpace.lg)
        ) {
            Text(
                "All categories",
                fontSize = TazType.h1Size,
                fontWeight = TazType.h1Weight,
                lineHeight = TazType.h1Line,
                color = TazColors.TextPrimary
            )
            Spacer(Modifier.height(TazSpace.xxs))
            Text(
                "Everything Tazzzo delivers",
                fontSize = TazType.captionSize,
                lineHeight = TazType.captionLine,
                color = TazColors.TextSecondary
            )
        }

        // Loading, empty and error (with retry) all come from StateHost —
        // no more hand-rolled `var loading by remember` spinner.
        StateHost(
            handle = categories,
            modifier = Modifier.weight(1f).fillMaxWidth(),
            loading = { CategoriesSkeleton() }
        ) { list ->
            val groups = list.map { it.group }.distinct()
            LazyColumn(
                Modifier.fillMaxSize(),
                contentPadding = PaddingValues(
                    top = TazSpace.sm, bottom = TazSpace.cartBarClearance
                )
            ) {
                groups.forEach { group ->
                    item(key = "header-$group") { SectionHeader(group) }
                    val rows = list.filter { it.group == group }.chunked(4)
                    items(rows) { row ->
                        // Tiles sit on the cream ground — no card wrapper, so
                        // the section header alone carries the grouping.
                        Row(
                            Modifier.fillMaxWidth()
                                .padding(horizontal = TazSpace.gutter, vertical = RowGap / 2),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            row.forEach { cat ->
                                TileCell {
                                    CategoryTile(
                                        cat,
                                        size = TileSize,
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
            start = TazSpace.gutter, end = TazSpace.gutter, top = TazSpace.lg
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
