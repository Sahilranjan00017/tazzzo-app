package com.tazzzo.app.ui.common

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.tazzzo.app.theme.TazColors
import com.tazzzo.app.theme.TazIcons
import com.tazzzo.app.theme.TazRadius
import com.tazzzo.app.theme.TazSpace
import com.tazzzo.app.theme.tazEditorialFamily
import com.tazzzo.app.ui.interaction.TazPress
import com.tazzzo.app.ui.interaction.tazPressable

const val SEARCH_PLACEHOLDER = "Search for groceries, staples, and more..."

/**
 * The rounded cream search shell of the approved design (UI Page `Home.jpeg`), shared by Home, Shop and the Search
 * screen so the three are one control. With [onClick] it is a button that opens Search; with null it is a static
 * frame (the Search screen itself, where typing has nothing real to query yet). No scan affordance: there is no scanner.
 */
@Composable
fun TazSearchShell(onClick: (() -> Unit)?, modifier: Modifier = Modifier, placeholder: String = SEARCH_PLACEHOLDER) {
    Row(
        modifier.fillMaxWidth().height(50.dp)
            .shadow(6.dp, TazRadius.pill, ambientColor = Color.Black.copy(alpha = 0.06f), spotColor = Color.Black.copy(alpha = 0.10f))
            .clip(TazRadius.pill).background(TazColors.Surface)
            .then(if (onClick != null) Modifier.tazPressable(onClick = onClick, pressScale = TazPress.card).semantics { contentDescription = "Search" } else Modifier)
            .padding(horizontal = TazSpace.lg),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(TazIcons.Search, contentDescription = null, tint = TazColors.TextSecondary, modifier = Modifier.size(22.dp))
        Spacer(Modifier.width(TazSpace.md))
        Text(placeholder, fontFamily = tazEditorialFamily(), fontSize = 15.sp, color = TazColors.TextTertiary, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}
