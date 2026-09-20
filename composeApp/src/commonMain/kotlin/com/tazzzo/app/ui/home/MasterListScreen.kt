package com.tazzzo.app.ui.home

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.tazzzo.app.LocalAppState
import com.tazzzo.app.data.model.Product
import com.tazzzo.app.data.repository.ServiceLocator
import com.tazzzo.app.theme.TazColors
import com.tazzzo.app.theme.TazIcons
import com.tazzzo.app.theme.TazRadius
import com.tazzzo.app.theme.TazSize
import com.tazzzo.app.theme.TazSpace
import com.tazzzo.app.theme.TazType
import com.tazzzo.app.ui.common.EmptyState
import com.tazzzo.app.ui.common.PillButton
import com.tazzzo.app.ui.common.ProductImage
import com.tazzzo.app.ui.common.QuantityStepper
import com.tazzzo.app.ui.common.StateHost
import com.tazzzo.app.ui.common.TazIcon
import com.tazzzo.app.ui.common.TazTopBar
import com.tazzzo.app.ui.state.rememberLoad
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.OutlinedTextField
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction
import com.tazzzo.app.data.search.ShoppingList
import com.tazzzo.app.ui.common.MicButton
import com.tazzzo.app.ui.common.ProductCard
import com.tazzzo.app.ui.onboarding.tazFieldColors
import com.tazzzo.app.ui.state.UiState

/**
 * Master List — the things this customer actually rebuys.
 *
 * Built from real order history and ranked by how many separate orders each
 * product appears in, so the list is evidence of a habit rather than a
 * wishlist somebody has to curate by hand. A grocery customer's basket is
 * mostly the same every week; this is the screen that admits it.
 *
 * Deliberately NOT seeded from bestsellers when there is no history. Showing
 * strangers' popular items as "your list" would be a claim about this person
 * that the data does not support — so a new customer gets an honest empty
 * state that sends them to the aisles instead.
 *
 * Prices and stock are read live at render, never carried over from the old
 * order: adding from here goes through the same cart path as any other screen,
 * so a price change or an out-of-stock is surfaced, not silently inherited.
 */
@Composable
fun MasterListScreen() {
    val app = LocalAppState.current
    val orders = rememberLoad { ServiceLocator.orders.getOrders() }

    Column(Modifier.fillMaxSize().background(TazColors.Cream)) {
        TazTopBar("Master List", onBack = { app.back() })

        // The list builder. Type or speak several things at once; each becomes
        // its own row of matching packs to choose from.
        ListBuilder()

        StateHost(
            handle = orders,
            modifier = Modifier.weight(1f).fillMaxWidth(),
            empty = {
                EmptyState(
                    emoji = "🧾",
                    title = "Your list builds itself",
                    body = "Order once and the things you buy again show up here, " +
                        "ranked by how often you buy them.",
                    actionLabel = "Start shopping",
                    onAction = { app.goHome() }
                )
            }
        ) { orderList ->
            // How many DISTINCT orders each product appears in. Counting orders
            // rather than units means one bulk purchase of ten does not
            // outrank a staple bought every single week.
            val ranked: List<Pair<Product, Int>> = orderList
                .flatMap { order -> order.lines.map { it.product }.distinctBy { it.id } }
                .groupBy { it.id }
                .map { (_, items) -> items.first() to items.size }
                .sortedWith(compareByDescending<Pair<Product, Int>> { it.second }.thenBy { it.first.name })

            val available = ranked.filter { it.first.isPurchasable }
            val notInCart = available.filter { app.quantityOf(it.first) == 0 }

            Column(Modifier.fillMaxSize()) {
                LazyColumn(
                    Modifier.weight(1f).fillMaxWidth(),
                    contentPadding = PaddingValues(
                        top = TazSpace.md, bottom = TazSpace.cartBarClearance
                    )
                ) {
                    item {
                        Text(
                            if (ranked.size == 1) "1 product you've bought before"
                            else "${ranked.size} products you've bought before",
                            fontSize = TazType.captionSize, color = TazColors.TextSecondary,
                            modifier = Modifier.padding(
                                horizontal = TazSpace.gutter, vertical = TazSpace.sm
                            )
                        )
                    }
                    items(ranked, key = { it.first.id }) { (product, timesOrdered) ->
                        MasterListRow(product, timesOrdered)
                        HorizontalDivider(
                            Modifier.padding(horizontal = TazSpace.gutter),
                            color = TazColors.CardBorder
                        )
                    }
                }

                // One tap to restock everything not already in the basket. It
                // says how many it will add, because a button that changes the
                // cart must say what it is about to do.
                if (notInCart.isNotEmpty()) {
                    Column(
                        Modifier.fillMaxWidth().background(TazColors.Surface)
                            .navigationBarsPadding().padding(TazSpace.gutter)
                    ) {
                        PillButton(
                            text = if (notInCart.size == 1) "Add 1 item to cart"
                            else "Add all ${notInCart.size} items to cart",
                            onClick = { notInCart.forEach { app.addToCart(it.first) } },
                            modifier = Modifier.fillMaxWidth()
                        )
                        val skipped = ranked.size - available.size
                        if (skipped > 0) {
                            Spacer(Modifier.height(TazSpace.sm))
                            Text(
                                if (skipped == 1) "1 product is out of stock and will be skipped"
                                else "$skipped products are out of stock and will be skipped",
                                fontSize = TazType.microSize, color = TazColors.TextTertiary,
                                modifier = Modifier.fillMaxWidth()
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun MasterListRow(product: Product, timesOrdered: Int) {
    Row(
        Modifier.fillMaxWidth().padding(
            horizontal = TazSpace.gutter, vertical = TazSpace.md
        ),
        verticalAlignment = Alignment.CenterVertically
    ) {
        ProductImage(
            product,
            Modifier.size(56.dp).clip(TazRadius.chip),
            glyphSize = TazType.h2Size, contentPadding = TazSpace.xs
        )
        Spacer(Modifier.width(TazSpace.md))
        Column(Modifier.weight(1f)) {
            Text(
                product.name, fontSize = TazType.productNameSize,
                fontWeight = TazType.productNameWeight, lineHeight = TazType.productNameLine,
                color = TazColors.TextPrimary, maxLines = 2, overflow = TextOverflow.Ellipsis
            )
            Text(
                product.unit, fontSize = TazType.unitSize, color = TazColors.TextTertiary,
                maxLines = 1
            )
            Spacer(Modifier.height(TazSpace.xxs))
            Row(verticalAlignment = Alignment.Bottom) {
                Text(
                    "₹${product.price}", fontSize = TazType.priceSize,
                    fontWeight = TazType.priceWeight, color = TazColors.TextPrimary, maxLines = 1
                )
                if (product.mrp > product.price) {
                    Spacer(Modifier.width(TazSpace.xs))
                    Text(
                        "₹${product.mrp}", fontSize = TazType.mrpSize,
                        color = TazColors.TextTertiary,
                        textDecoration = TextDecoration.LineThrough, maxLines = 1
                    )
                }
            }
            // Evidence, not a badge: how many separate orders it appeared in.
            if (timesOrdered > 1) {
                Spacer(Modifier.height(TazSpace.xxs))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    TazIcon(
                        TazIcons.OrderAgain, null,
                        size = TazSize.iconXs, tint = TazColors.Green
                    )
                    Spacer(Modifier.width(TazSpace.xs))
                    Text(
                        "In $timesOrdered of your orders",
                        fontSize = TazType.microSize, fontWeight = FontWeight.Medium,
                        color = TazColors.Green, maxLines = 1
                    )
                }
            }
        }
        Spacer(Modifier.width(TazSpace.md))
        QuantityStepper(product, Modifier.width(TazSize.stepperInlineWidth))
    }
}

/**
 * Build a basket from a list, the way people actually write one.
 *
 * Type "aata, doodh, maggi" — or say it — and each term becomes a row of the
 * packs that match, to pick from. The engine's Hindi vocabulary comes for free,
 * so "aata" finds atta and "doodh" finds milk.
 *
 * The rows exist because resolving each term to ONE product would be wrong:
 * "oil" matches four oils at four prices, and choosing silently puts something
 * in a basket the customer never picked. Ambiguity is shown, not guessed —
 * which is also the honest answer for a voice order.
 */
@Composable
private fun ListBuilder() {
    val app = LocalAppState.current
    var typed by rememberSaveable { mutableStateOf("") }
    var submitted by rememberSaveable { mutableStateOf("") }
    val terms = remember(submitted) { ShoppingList.parse(submitted) }

    val matches = rememberLoad<List<Pair<String, List<Product>>>>(
        submitted, isEmpty = { false }
    ) {
        terms.map { term -> term to ServiceLocator.catalog.search(term) }
    }

    Column(Modifier.fillMaxWidth().background(TazColors.Surface).padding(TazSpace.gutter)) {
        Text(
            "Add several things at once",
            fontSize = TazType.titleSize, fontWeight = TazType.titleWeight,
            color = TazColors.TextPrimary
        )
        Spacer(Modifier.height(TazSpace.xxs))
        Text(
            "Separate them with commas — we'll show every pack that matches",
            fontSize = TazType.captionSize, lineHeight = TazType.captionLine,
            color = TazColors.TextSecondary
        )
        Spacer(Modifier.height(TazSpace.md))

        Row(verticalAlignment = Alignment.CenterVertically) {
            OutlinedTextField(
                value = typed,
                onValueChange = { typed = it },
                modifier = Modifier.weight(1f)
                    .semantics { contentDescription = "Your shopping list" },
                placeholder = {
                    Text(
                        "aata, doodh, maggi", fontSize = TazType.bodySize,
                        color = TazColors.TextTertiary
                    )
                },
                singleLine = false,
                maxLines = 3,
                shape = TazRadius.card,
                colors = tazFieldColors(),
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                keyboardActions = KeyboardActions(onDone = { submitted = typed })
            )
            Spacer(Modifier.width(TazSpace.sm))
            // Voice reaches the same place. It opens the honest "coming soon"
            // sheet today — speech-to-text is [BACKEND REQUIRED] — and when it
            // lands it fills this same field, so the rows below are unchanged.
            MicButton(TazSize.avatar) { app.showVoiceSheet = true }
        }

        Spacer(Modifier.height(TazSpace.md))
        PillButton(
            text = "Show my list",
            onClick = { submitted = typed },
            enabled = ShoppingList.parse(typed).isNotEmpty(),
            disabledHint = "Type at least one product to continue",
            modifier = Modifier.fillMaxWidth()
        )
    }

    if (terms.isEmpty()) return

    val resolved = (matches.state as? UiState.Success)?.data ?: return
    Spacer(Modifier.height(TazSpace.sm))
    resolved.forEach { (term, products) ->
        Column(Modifier.fillMaxWidth().padding(top = TazSpace.md)) {
            Row(
                Modifier.fillMaxWidth().padding(horizontal = TazSpace.gutter),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    term.replaceFirstChar { it.uppercase() },
                    fontSize = TazType.h2Size, fontWeight = TazType.h2Weight,
                    color = TazColors.TextPrimary, maxLines = 1,
                    overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f)
                )
                if (products.isNotEmpty()) {
                    Text(
                        if (products.size == 1) "1 match" else "${products.size} matches",
                        fontSize = TazType.captionSize, color = TazColors.TextTertiary
                    )
                }
            }
            Spacer(Modifier.height(TazSpace.sm))
            if (products.isEmpty()) {
                // Named, not a generic empty state: the customer needs to know
                // WHICH of the things they asked for could not be found.
                Text(
                    "Nothing matched \"$term\". Try another word for it.",
                    fontSize = TazType.captionSize, color = TazColors.TextSecondary,
                    modifier = Modifier.padding(horizontal = TazSpace.gutter)
                )
            } else {
                LazyRow(
                    Modifier.fillMaxWidth(),
                    contentPadding = PaddingValues(horizontal = TazSpace.screenEdge),
                    horizontalArrangement = Arrangement.spacedBy(TazSpace.sm)
                ) {
                    items(products, key = { "$term-${it.id}" }) { ProductCard(it) }
                }
            }
        }
    }
}
