package com.tazzzo.app.ui.home

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.tazzzo.app.LocalAppState
import com.tazzzo.app.data.repository.ServiceLocator
import com.tazzzo.app.theme.TazColors
import com.tazzzo.app.theme.TazIcons
import com.tazzzo.app.theme.TazRadius
import com.tazzzo.app.theme.TazSize
import com.tazzzo.app.theme.TazSpace
import com.tazzzo.app.theme.TazType
import com.tazzzo.app.ui.common.EmptyState
import com.tazzzo.app.ui.common.StateHost
import com.tazzzo.app.ui.common.TazIcon
import com.tazzzo.app.ui.common.TazTopBar
import com.tazzzo.app.ui.state.rememberLoad

/**
 * Saved addresses — the same repository the checkout address step uses, so
 * the two surfaces can never disagree. Adding happens in checkout's inline
 * form today; this screen is the read/manage view.
 */
@Composable
fun AddressesScreen() {
    val app = LocalAppState.current
    val load = rememberLoad(isEmpty = { it.isEmpty() }) {
        ServiceLocator.addresses.getAddresses()
    }

    Column(Modifier.fillMaxSize().background(TazColors.Cream)) {
        TazTopBar("Saved addresses", onBack = { app.back() })
        StateHost(
            load,
            modifier = Modifier.fillMaxSize(),
            empty = {
                EmptyState(
                    "📍", "No saved addresses",
                    "Add an address while checking out and it will appear here."
                )
            }
        ) { addresses ->
            LazyColumn(contentPadding = TazSpace.screen) {
                items(addresses, key = { it.id }) { address ->
                    Row(
                        Modifier.fillMaxWidth()
                            .padding(bottom = TazSpace.md)
                            .clip(TazRadius.card)
                            .background(TazColors.Surface)
                            .border(BorderStroke(1.dp, TazColors.CardBorder), TazRadius.card)
                            .padding(TazSpace.lg)
                    ) {
                        TazIcon(
                            TazIcons.Location, null,
                            size = TazSize.iconSm, tint = TazColors.Green
                        )
                        Spacer(Modifier.width(TazSpace.md))
                        Column(Modifier.weight(1f)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(
                                    address.label, fontSize = TazType.titleSize,
                                    fontWeight = TazType.titleWeight,
                                    lineHeight = TazType.titleLine,
                                    color = TazColors.TextPrimary,
                                    modifier = Modifier.weight(1f)
                                )
                                if (!address.isServiceable) {
                                    Spacer(Modifier.width(TazSpace.sm))
                                    NotServiceableChip()
                                }
                            }
                            Spacer(Modifier.height(TazSpace.xs))
                            Text(
                                listOf(address.line1, address.line2, address.pincode)
                                    .filter { it.isNotBlank() }.joinToString(", "),
                                fontSize = TazType.bodySize, color = TazColors.TextSecondary,
                                lineHeight = TazType.bodyLine
                            )
                        }
                    }
                }
                item {
                    Text(
                        "To add a new address, use “Add new address” during checkout.",
                        fontSize = TazType.captionSize, lineHeight = TazType.captionLine,
                        color = TazColors.TextTertiary,
                        modifier = Modifier.padding(TazSpace.sm)
                    )
                }
            }
        }
    }
}

@Composable
private fun NotServiceableChip() {
    Box(
        Modifier.clip(TazRadius.pill).background(TazColors.WarningSoft)
            .padding(horizontal = TazSpace.sm, vertical = TazSpace.xs)
    ) {
        Text(
            "Not serviceable", fontSize = TazType.microSize,
            lineHeight = TazType.microLine, fontWeight = FontWeight.Bold,
            color = TazColors.Warning
        )
    }
}
