package com.tazzzo.app.ui.catalog

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.tazzzo.app.HomeTab
import com.tazzzo.app.LocalAppState
import com.tazzzo.app.Screen
import com.tazzzo.app.data.catalog.CatalogProduct
import com.tazzzo.app.data.catalog.PagedState
import com.tazzzo.app.data.catalog.SearchQueryCheck
import com.tazzzo.app.data.catalog.productSearch
import com.tazzzo.app.data.repository.ServiceLocator
import com.tazzzo.app.theme.TazColors
import com.tazzzo.app.theme.TazIcons
import com.tazzzo.app.theme.TazRadius
import com.tazzzo.app.theme.TazSize
import com.tazzzo.app.theme.TazSpace
import com.tazzzo.app.theme.TazType
import com.tazzzo.app.theme.tazEditorialFamily
import com.tazzzo.app.ui.common.CartBar
import com.tazzzo.app.ui.common.EditorialEmptyState
import com.tazzzo.app.ui.common.EditorialFailureState
import com.tazzzo.app.ui.common.EditorialText
import com.tazzzo.app.ui.common.TazIcon
import com.tazzzo.app.ui.common.plain
import com.tazzzo.app.ui.interaction.TazPress
import com.tazzzo.app.ui.interaction.tazPressable

/**
 * Search in REMOTE mode: `GET /v1/search` (products only, every word a prefix match on product name and brand, no ranking).
 * The query is trimmed and checked against the backend's grammar before anything is sent ([com.tazzzo.app.data.catalog.SearchQueryRules]),
 * runs after a short pause in typing or at once on the keyboard's Search key, and pages by cursor. Results are the canonical
 * product card (price, MRP, stock, add to cart). Nothing typed shows the customer's recent searches. The query is never logged.
 */
@Composable
fun RemoteSearchScreen() {
    val app = LocalAppState.current
    val scope = rememberCoroutineScope()
    val search = remember { productSearch(scope, ServiceLocator.remoteCatalog, ServiceLocator.launchContext.pin) }
    val input by search.input.collectAsState()
    val check by search.check.collectAsState()
    val results by search.results.collectAsState()
    val keyboard = LocalSoftwareKeyboardController.current
    var failures by remember { mutableStateOf(0) }

    fun run(query: String) {
        search.submit(query)?.let { app.recordSearch(it) }
        keyboard?.hide()
    }

    // A `search:` banner (or any caller of openSearch) hands over a query: prefill it and run it once.
    LaunchedEffect(app.searchPrefill) { app.searchPrefill?.let { q -> app.searchPrefill = null; run(q) } }
    // A query that produced products is remembered, once per query (the first page arriving, not every append).
    val valid = (check as? SearchQueryCheck.Valid)?.text
    LaunchedEffect(valid, results is PagedState.Content) { if (valid != null && results is PagedState.Content) app.recordSearch(valid) }
    LaunchedEffect(results is PagedState.FirstPageFailed) { if (results is PagedState.FirstPageFailed) failures++ else if (results is PagedState.Content) failures = 0 }

    SearchScreenLayout(
        input = input,
        body = searchBody(check, results, app.recentSearches.toList()),
        consecutiveFailures = failures,
        autoFocus = app.searchPrefill == null && input.isEmpty(),
        actions = SearchActions(
            back = { app.back() },
            input = { search.onInput(it) },
            submit = { run(input) },
            runRecent = { run(it) },
            clearRecent = { app.clearRecentSearches() },
            openProduct = { app.navigate(Screen.ProductDetail(it.productId)) },
            shop = { app.homeTab = HomeTab.SHOP; app.goHome() },
            retry = { search.refresh() },
            retryAppend = { search.retryAppend() },
            loadMore = { search.loadMore() }
        )
    )
}

class SearchActions(
    val back: () -> Unit, val input: (String) -> Unit, val submit: () -> Unit, val runRecent: (String) -> Unit, val clearRecent: () -> Unit,
    val openProduct: (CatalogProduct) -> Unit, val shop: () -> Unit, val retry: () -> Unit, val retryAppend: () -> Unit, val loadMore: () -> Unit
)

/** The Search layout, independent of its data source (evidence renders it with sample state). */
@Composable
fun SearchScreenLayout(
    input: String, body: SearchBody, actions: SearchActions, consecutiveFailures: Int = 1, autoFocus: Boolean = false,
    gridState: LazyGridState = rememberLazyGridState()
) {
    Box(Modifier.fillMaxSize().background(TazColors.Cream).testTag("searchScreen")) {
    Column(Modifier.fillMaxSize()) {
        Row(
            Modifier.fillMaxWidth().statusBarsPadding().padding(start = TazSpace.md, end = TazSpace.lg, top = TazSpace.sm),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                Modifier.size(TazSize.touchTarget).clip(CircleShape).background(TazColors.Surface)
                    .tazPressable(onClick = actions.back, pressScale = TazPress.compact, role = Role.Button)
                    .semantics { contentDescription = "Back" },
                contentAlignment = Alignment.Center
            ) { TazIcon(TazIcons.Back, null, size = TazSize.iconSm, tint = TazColors.BrandEditorial) }
            Spacer(Modifier.width(TazSpace.md))
            EditorialText(listOf(plain(ShopCopy.SEARCH_TITLE)), size = TazType.editorialHeadlineSize, lineHeight = TazType.editorialHeadlineLine, color = TazColors.BrandEditorial, textAlign = TextAlign.Start)
        }
        Spacer(Modifier.height(TazSpace.lg))
        SearchField(input, actions, autoFocus, Modifier.padding(horizontal = TazSpace.lg))
        Spacer(Modifier.height(TazSpace.md))
        Box(Modifier.weight(1f).fillMaxWidth()) {
            when (body) {
                is SearchBody.Start -> StartBody(body.recent, actions)
                is SearchBody.Invalid -> Text(
                    body.hint, fontSize = TazType.captionSize, color = TazColors.TextSecondary,
                    modifier = Modifier.padding(horizontal = TazSpace.xl).semantics { liveRegion = LiveRegionMode.Polite }.testTag("searchHint")
                )
                SearchBody.Loading -> ProductGridSkeleton()
                is SearchBody.Failed -> EditorialFailureState(body.failure, consecutiveFailures, onRetry = actions.retry)
                SearchBody.NoResults -> EditorialEmptyState(TazIcons.Search, ShopCopy.NO_RESULTS_TITLE, ShopCopy.NO_RESULTS_BODY, actionLabel = ShopCopy.BACK_TO_SHOP, onAction = actions.shop)
                is SearchBody.Results -> ResultsGrid(body.state, gridState, consecutiveFailures, actions)
            }
        }
    }
    // The server cart's "View cart" bar, so a product added from the results leads straight to the cart.
    CartBar()
    }
}

@Composable
private fun SearchField(input: String, actions: SearchActions, autoFocus: Boolean, modifier: Modifier) {
    val focus = remember { FocusRequester() }
    LaunchedEffect(autoFocus) { if (autoFocus) runCatching { focus.requestFocus() } }
    Row(
        modifier.fillMaxWidth().height(50.dp)
            .shadow(6.dp, TazRadius.pill, ambientColor = Color.Black.copy(alpha = 0.06f), spotColor = Color.Black.copy(alpha = 0.10f))
            .clip(TazRadius.pill).background(TazColors.Surface).padding(start = TazSpace.lg, end = TazSpace.sm),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(TazIcons.Search, contentDescription = null, tint = TazColors.TextSecondary, modifier = Modifier.size(22.dp))
        Spacer(Modifier.width(TazSpace.md))
        Box(Modifier.weight(1f), contentAlignment = Alignment.CenterStart) {
            if (input.isEmpty()) Text(ShopCopy.SEARCH_PLACEHOLDER, fontFamily = tazEditorialFamily(), fontSize = 15.sp, color = TazColors.TextTertiary, maxLines = 1, overflow = TextOverflow.Ellipsis)
            BasicTextField(
                value = input,
                onValueChange = actions.input,
                modifier = Modifier.fillMaxWidth().focusRequester(focus).semantics { contentDescription = ShopCopy.SEARCH_FIELD_DESCRIPTION }.testTag("searchField"),
                singleLine = true,
                textStyle = LocalTextStyle.current.copy(fontSize = TazType.bodySize, color = TazColors.TextPrimary),
                cursorBrush = SolidColor(TazColors.BrandEditorial),
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                keyboardActions = KeyboardActions(onSearch = { actions.submit() })
            )
        }
        if (input.isNotEmpty()) Box(
            Modifier.size(TazSize.touchTarget).clip(CircleShape).tazPressable(onClick = { actions.input("") }, pressScale = TazPress.compact, role = Role.Button),
            contentAlignment = Alignment.Center
        ) { TazIcon(TazIcons.Close, "Clear search", size = TazSize.iconSm, tint = TazColors.TextTertiary) }
    }
}

/** Nothing typed: recent searches as chips (tap to run, "Clear" to forget them), or the start prompt. */
@Composable
private fun StartBody(recent: List<String>, actions: SearchActions) {
    if (recent.isEmpty()) {
        EditorialEmptyState(TazIcons.Search, ShopCopy.SEARCH_START_TITLE, ShopCopy.SEARCH_START_BODY, actionLabel = ShopCopy.BACK_TO_SHOP, onAction = actions.shop)
        return
    }
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = TazSpace.lg).testTag("recentSearches")) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text(ShopCopy.RECENT_SEARCHES, fontSize = TazType.bodySize, fontWeight = FontWeight.SemiBold, color = TazColors.TextPrimary, modifier = Modifier.weight(1f))
            Box(
                Modifier.clip(TazRadius.pill).tazPressable(onClick = actions.clearRecent, pressScale = TazPress.compact, role = Role.Button)
                    .padding(horizontal = TazSpace.md, vertical = TazSpace.sm)
            ) { Text(ShopCopy.CLEAR_RECENT, fontSize = TazType.captionSize, fontWeight = FontWeight.SemiBold, color = TazColors.BrandEditorial) }
        }
        Spacer(Modifier.height(TazSpace.sm))
        LazyRow(horizontalArrangement = Arrangement.spacedBy(TazSpace.sm)) {
            items(recent.size, key = { recent[it] }) { i ->
                val term = recent[i]
                Box(
                    Modifier.height(TazSize.chipHeight).clip(TazRadius.pill).background(TazColors.Surface)
                        .border(BorderStroke(1.dp, TazColors.CardBorder), TazRadius.pill)
                        .tazPressable(onClick = { actions.runRecent(term) }, pressScale = TazPress.compact, role = Role.Button)
                        .semantics { contentDescription = "Search $term" }
                        .padding(horizontal = TazSpace.md),
                    contentAlignment = Alignment.Center
                ) { Text(term, fontSize = TazType.captionSize, color = TazColors.TextPrimary, maxLines = 1, overflow = TextOverflow.Ellipsis) }
            }
        }
        Spacer(Modifier.height(TazSpace.lg))
        Text(ShopCopy.SEARCH_START_BODY, fontSize = TazType.captionSize, color = TazColors.TextTertiary)
    }
}

@Composable
private fun ResultsGrid(state: PagedState.Content<CatalogProduct>, gridState: LazyGridState, consecutiveFailures: Int, actions: SearchActions) {
    val total = state.items.size
    LaunchedEffect(gridState, total) {
        snapshotFlow { gridState.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: -1 }
            .collect { last -> if (total > 0 && last >= total - PREFETCH_DISTANCE) actions.loadMore() }
    }
    LazyVerticalGrid(
        modifier = Modifier.fillMaxSize().testTag("searchResults"),
        state = gridState,
        columns = GridCells.Adaptive(PRODUCT_GRID_MIN_CELL_DP.dp),
        contentPadding = PaddingValues(start = TazSpace.screenEdge, end = TazSpace.screenEdge, top = TazSpace.sm, bottom = TazSpace.cartBarClearance),
        horizontalArrangement = Arrangement.spacedBy(TazSpace.sm),
        verticalArrangement = Arrangement.spacedBy(TazSpace.md)
    ) {
        items(state.items, key = { it.skuId }) { p -> TazProductCard(p, onClick = { actions.openProduct(p) }) }
        item(span = { GridItemSpan(maxLineSpan) }, key = "footer") { GridFooter(state, consecutiveFailures, onRetry = actions.retryAppend) }
    }
}
