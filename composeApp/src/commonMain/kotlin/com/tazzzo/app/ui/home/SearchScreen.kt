package com.tazzzo.app.ui.home

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.tazzzo.app.LocalAppState
import com.tazzzo.app.config.BrandCopy
import com.tazzzo.app.data.model.Product
import com.tazzzo.app.data.repository.ServiceLocator
import com.tazzzo.app.ui.interaction.TazPress
import com.tazzzo.app.ui.interaction.tazPressable
import com.tazzzo.app.theme.TazColors
import com.tazzzo.app.theme.TazIcons
import com.tazzzo.app.theme.TazRadius
import com.tazzzo.app.theme.TazSize
import com.tazzzo.app.theme.TazSpace
import com.tazzzo.app.theme.TazType
import com.tazzzo.app.ui.common.CartBar
import com.tazzzo.app.ui.common.EmptyState
import kotlinx.coroutines.CancellationException
import com.tazzzo.app.ui.state.toLoadError
import com.tazzzo.app.ui.state.rememberLoad
import com.tazzzo.app.ui.state.UiState
import com.tazzzo.app.ui.state.LoadError
import com.tazzzo.app.ui.common.ErrorState
import com.tazzzo.app.ui.common.FilterBar
import com.tazzzo.app.ui.common.MicButton
import com.tazzzo.app.ui.common.PillButton
import com.tazzzo.app.ui.common.ProductCard
import com.tazzzo.app.ui.common.ProductFilters
import com.tazzzo.app.ui.common.ProductRail
import com.tazzzo.app.ui.common.SectionHeader
import com.tazzzo.app.ui.common.SortSheet
import com.tazzzo.app.ui.common.TazIcon
import com.tazzzo.app.ui.common.applyFilters
import kotlinx.coroutines.delay

private val popularSearches = listOf(
    "Milk", "Atta", "Maggi", "Chips", "Soap", "Chocolate", "Paneer", "Coffee"
)

private val SectionGap: Dp = 24.dp

@Composable
fun SearchScreen() {
    val searchFocus = remember { FocusRequester() }
    LaunchedEffect(Unit) { searchFocus.requestFocus() }
    val app = LocalAppState.current

    var query by remember { mutableStateOf("") }
    var results by remember { mutableStateOf<List<Product>>(emptyList()) }
    var searching by remember { mutableStateOf(false) }
    /** The query whose results are currently on screen — "" until one settles. */
    var searchedQuery by remember { mutableStateOf("") }
    var filters by remember { mutableStateOf(ProductFilters()) }
    var showSortSheet by remember { mutableStateOf(false) }
    /** Set when the search itself failed. Distinct from "no results". */
    var searchError by remember { mutableStateOf<LoadError?>(null) }
    var searchAttempt by remember { mutableStateOf(0) }

    // Idle-state rail only. It loads independently so a bestsellers failure
    // never blocks searching.
    val bestsellers = rememberLoad { ServiceLocator.catalog.getBestsellers() }

    LaunchedEffect(query, searchAttempt) {
        if (query.isBlank()) {
            results = emptyList()
            searchedQuery = ""
            searching = false
            searchError = null
        } else {
            // Debounce silently; the previous results stay on screen and a thin
            // progress bar under the header signals the load — no spinner flash.
            // The deliberate exception to StateHost: swapping to a full loading
            // state on every keystroke is the spinner flash this screen was
            // designed to avoid. The error model is still the shared one.
            delay(250)
            searching = true
            searchError = null
            try {
                results = ServiceLocator.catalog.search(query)
                searchedQuery = query
                filters = ProductFilters()
                // Record once per settled (debounced) query that produced results.
                if (results.isNotEmpty()) app.recordSearch(query)
            } catch (cancellation: CancellationException) {
                throw cancellation
            } catch (t: Throwable) {
                // A failed search must never look like "nothing matched".
                searchError = t.toLoadError()
                results = emptyList()
                searchedQuery = query
            } finally {
                searching = false
            }
        }
    }

    Box(Modifier.fillMaxSize().background(TazColors.Cream)) {
        Column(Modifier.fillMaxSize()) {
            // ----- Header: back + the search field + mic -----
            SearchHeader(
                query = query,
                onQueryChange = { query = it },
                focusRequester = searchFocus,
                onBack = { app.back() },
                onMic = { app.showVoiceSheet = true }
            )

            // Thin, non-blocking load signal — the grid below stays visible.
            if (searching) {
                LinearProgressIndicator(
                    modifier = Modifier.fillMaxWidth().height(2.dp),
                    color = TazColors.Green,
                    trackColor = TazColors.SurfaceSunken
                )
            }

            when {
                // ----- Idle: recent + popular searches + bestsellers rail -----
                query.isBlank() -> Column(
                    Modifier.fillMaxSize().verticalScroll(rememberScrollState())
                ) {
                    Spacer(Modifier.height(TazSpace.sm))
                    if (app.recentSearches.isNotEmpty()) {
                        SectionHeader("Recent searches")
                        SearchChipRows(
                            terms = app.recentSearches.toList(),
                            recent = true,
                            onClick = { query = it }
                        )
                        Spacer(Modifier.height(SectionGap))
                    }
                    SectionHeader("Popular searches")
                    SearchChipRows(
                        terms = popularSearches,
                        recent = false,
                        onClick = { query = it }
                    )
                    Spacer(Modifier.height(SectionGap))
                    // Honest label: this rail is getBestsellers — we have no trend data.
                    (bestsellers.state as? UiState.Success)?.data?.let { rail ->
                        ProductRail("Bestsellers", rail)
                    }
                    Spacer(Modifier.height(TazSpace.cartBarClearance))
                }

                // ----- First query still settling: keep the area calm -----
                searchedQuery.isEmpty() -> Box(Modifier.fillMaxSize())

                // ----- The search call itself failed -----
                searchError != null -> ErrorState(
                    error = searchError!!,
                    onRetry = { searchAttempt++ },
                    modifier = Modifier.fillMaxSize()
                )

                // ----- No results (for the settled query) -----
                results.isEmpty() -> Column(
                    Modifier.fillMaxSize().verticalScroll(rememberScrollState()),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Spacer(Modifier.height(TazSpace.xxl))
                    EmptyState(
                        emoji = "🔍",
                        title = "No results for \"$searchedQuery\"",
                        body = "Check the spelling, or try a simpler word — hindi works too: doodh, aata, sabun"
                    )
                    Row(
                        Modifier.padding(horizontal = TazSpace.gutter),
                        horizontalArrangement = Arrangement.spacedBy(TazSpace.sm)
                    ) {
                        popularSearches.take(3).forEach { chip ->
                            SearchChip(chip, recent = false) { query = chip }
                        }
                    }
                    Spacer(Modifier.height(TazSpace.xl))
                    PillButton(
                        "Ask by voice — ${BrandCopy.voiceStatus}",
                        onClick = { app.showVoiceSheet = true },
                        filled = false
                    )
                    Spacer(Modifier.height(TazSpace.cartBarClearance))
                }

                // ----- Results grid (with filters) -----
                else -> {
                    val filtered = results.applyFilters(filters)
                    val brands = remember(results) {
                        results.map { it.brand }.filter { it.isNotBlank() }.distinct()
                    }
                    Column(Modifier.fillMaxSize()) {
                        FilterBar(
                            filters = filters,
                            brands = brands,
                            onChange = { filters = it },
                            onOpenSort = { showSortSheet = true }
                        )
                        if (filtered.isEmpty()) {
                            EmptyState(
                                emoji = "🧺",
                                title = "Nothing matches these filters",
                                body = "We found ${results.size} results for \"$searchedQuery\" — clear the filters to see them.",
                                actionLabel = "Clear filters",
                                onAction = { filters = ProductFilters() }
                            )
                        } else {
                            LazyColumn(
                                Modifier.fillMaxSize(),
                                contentPadding = PaddingValues(
                                    start = TazSpace.gutter,
                                    top = TazSpace.sm,
                                    end = TazSpace.gutter,
                                    bottom = TazSpace.cartBarClearance
                                ),
                                verticalArrangement = Arrangement.spacedBy(TazSpace.md)
                            ) {
                                items(filtered.chunked(2)) { rowProducts ->
                                    Row(horizontalArrangement = Arrangement.spacedBy(TazSpace.md)) {
                                        rowProducts.forEach { p ->
                                            ProductCard(p, Modifier.weight(1f))
                                        }
                                        if (rowProducts.size == 1) Spacer(Modifier.weight(1f))
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
        CartBar()

        if (showSortSheet) {
            SortSheet(
                current = filters.sort,
                onSelect = { filters = filters.copy(sort = it) },
                onDismiss = { showSortSheet = false }
            )
        }
    }
}

// ---------------------------------------------------------------------------
// Header — the same lifted field as home, so arriving here feels like the
// search bar simply grew a keyboard.
// ---------------------------------------------------------------------------

@Composable
private fun SearchHeader(
    query: String,
    onQueryChange: (String) -> Unit,
    focusRequester: FocusRequester,
    onBack: () -> Unit,
    onMic: () -> Unit
) {
    var focused by remember { mutableStateOf(false) }
    Row(
        Modifier
            .fillMaxWidth()
            .background(TazColors.Surface)
            .statusBarsPadding()
            .padding(horizontal = TazSpace.md, vertical = TazSpace.sm),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            Modifier
                .size(TazSize.touchTarget)
                .clip(CircleShape)
                .background(TazColors.SurfaceSunken)
                .tazPressable(onClick = { onBack() }, pressScale = TazPress.compact),
            contentAlignment = Alignment.Center
        ) {
            TazIcon(TazIcons.Back, "Back", size = TazSize.iconSm, tint = TazColors.TextPrimary)
        }
        Spacer(Modifier.width(TazSpace.sm))
        Row(
            Modifier
                .weight(1f)
                .height(TazSize.inputHeight)
                .shadow(3.dp, TazRadius.card, spotColor = Color.Black.copy(alpha = 0.14f))
                .clip(TazRadius.card)
                .background(TazColors.Surface)
                .border(
                    BorderStroke(
                        if (focused) 1.5.dp else 1.dp,
                        if (focused) TazColors.Green else TazColors.BorderStrong
                    ),
                    TazRadius.card
                )
                .padding(start = TazSpace.md, end = TazSpace.xs),
            verticalAlignment = Alignment.CenterVertically
        ) {
            TazIcon(
                TazIcons.Search, null,
                size = TazSize.iconSm,
                tint = if (focused) TazColors.Green else TazColors.TextTertiary
            )
            Spacer(Modifier.width(TazSpace.sm))
            Box(Modifier.weight(1f), contentAlignment = Alignment.CenterStart) {
                if (query.isEmpty()) {
                    Text(
                        "Search for atta, milk, soap…",
                        fontSize = TazType.bodySize,
                        color = TazColors.TextTertiary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                BasicTextField(
                    value = query,
                    onValueChange = onQueryChange,
                    modifier = Modifier
                        .fillMaxWidth()
                        .focusRequester(focusRequester)
                        .onFocusChanged { focused = it.isFocused },
                    singleLine = true,
                    textStyle = LocalTextStyle.current.copy(
                        fontSize = TazType.bodySize,
                        color = TazColors.TextPrimary
                    ),
                    cursorBrush = SolidColor(TazColors.Green),
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search)
                )
            }
            if (query.isNotEmpty()) {
                Box(
                    Modifier
                        .size(TazSize.iconLg)
                        .clip(CircleShape)
                        .tazPressable(onClick = { onQueryChange("") }, pressScale = TazPress.compact),
                    contentAlignment = Alignment.Center
                ) {
                    TazIcon(
                        TazIcons.Close, "Clear search",
                        size = TazSize.iconSm, tint = TazColors.TextTertiary
                    )
                }
            }
            MicButton(TazSize.avatar) { onMic() }
        }
    }
}

// ---------------------------------------------------------------------------
// Search term chips
// ---------------------------------------------------------------------------

/** Pill chip for a recent or popular term. Recent ones carry the search mark. */
@Composable
private fun SearchChip(label: String, recent: Boolean, onClick: () -> Unit) {
    Row(
        Modifier
            .height(TazSize.chipHeight)
            .clip(TazRadius.pill)
            .background(TazColors.Surface)
            .border(BorderStroke(1.dp, TazColors.CardBorder), TazRadius.pill)
            .tazPressable(onClick = { onClick() }, pressScale = TazPress.compact)
            .padding(horizontal = TazSpace.md),
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (recent) {
            TazIcon(
                TazIcons.Search, null,
                size = TazSize.iconXs, tint = TazColors.TextTertiary
            )
            Spacer(Modifier.width(TazSpace.xs + TazSpace.xxs))
        }
        Text(
            label,
            fontSize = TazType.captionSize,
            fontWeight = TazType.productNameWeight,
            color = TazColors.TextPrimary,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

/** Chips laid out three per row inside the page gutter. */
@Composable
private fun SearchChipRows(terms: List<String>, recent: Boolean, onClick: (String) -> Unit) {
    Column(
        Modifier.padding(horizontal = TazSpace.gutter),
        verticalArrangement = Arrangement.spacedBy(TazSpace.sm)
    ) {
        terms.chunked(3).forEach { rowChips ->
            Row(horizontalArrangement = Arrangement.spacedBy(TazSpace.sm)) {
                rowChips.forEach { chip -> SearchChip(chip, recent) { onClick(chip) } }
            }
        }
    }
}
