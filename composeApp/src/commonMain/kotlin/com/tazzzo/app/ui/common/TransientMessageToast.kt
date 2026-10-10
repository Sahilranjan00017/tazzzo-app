package com.tazzzo.app.ui.common

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.tazzzo.app.LocalAppState
import com.tazzzo.app.data.catalog.CatalogMode
import com.tazzzo.app.data.cart.CartState
import com.tazzzo.app.data.repository.ServiceLocator
import com.tazzzo.app.theme.TazColors
import com.tazzzo.app.theme.TazMotion
import com.tazzzo.app.theme.TazRadius
import com.tazzzo.app.theme.TazSize
import com.tazzzo.app.theme.TazSpace
import com.tazzzo.app.theme.TazType
import kotlinx.coroutines.delay

/**
 * Small centered bottom toast for [com.tazzzo.app.TazzzoAppState.transientMessage] (e.g. "Only 3 left in stock"). Hosted once
 * at the App root so it shows over EVERY screen; auto-dismisses after 2.2 seconds and is announced to screen readers.
 *
 * [aboveNav]: the Home shell is on top, so the toast sits above the floating nav bar (and the cart bar when it is up). On a
 * pushed screen it clears the system bar plus the height a bottom action bar (cart bar, purchase bar) occupies.
 */
@Composable
fun BoxScope.TransientMessageToast(aboveNav: Boolean) {
    val app = LocalAppState.current
    val message = app.transientMessage
    // Held so the copy does not blank out mid-exit-animation.
    var lastMessage by remember { mutableStateOf("") }
    LaunchedEffect(message) { if (message != null) lastMessage = message }
    LaunchedEffect(message) {
        if (message != null) {
            delay(2200)
            app.transientMessage = null
        }
    }
    // The SERVER cart's count in REMOTE (observed, so the toast moves with the bar), the local demo cart in MOCK.
    val cartBarUp = if (ServiceLocator.catalogMode == CatalogMode.REMOTE) {
        val st by ServiceLocator.cart.state.collectAsState()
        ((st as? CartState.Loaded)?.cart?.itemCount ?: 0) > 0
    } else app.cartItemCount > 0
    val cartBarBand = 64.dp + TazSpace.md * 2          // bar height + its vertical padding
    val bottomInset = if (aboveNav) TazSize.navBarHeight + TazSpace.lg + (if (cartBarUp) cartBarBand else 0.dp)
        else TazSpace.cartBarClearance + TazSpace.lg
    AnimatedVisibility(
        visible = message != null,
        // Above the keyboard when it is open (imePadding; insets already consumed by navigationBarsPadding are not added twice).
        modifier = Modifier.align(Alignment.BottomCenter).then(if (aboveNav) Modifier else Modifier.navigationBarsPadding()).imePadding(),
        // Rises into place and fades out. A refusal that hard-cuts on screen reads as a glitch; the customer needs to see it arrive.
        enter = slideInVertically(tween(TazMotion.fast)) { it / 2 } + fadeIn(tween(TazMotion.fast)),
        exit = fadeOut(tween(TazMotion.fast))
    ) {
        Box(
            Modifier
                .padding(bottom = bottomInset)
                .clip(TazRadius.pill)
                .background(TazColors.TextPrimary)
                .padding(horizontal = TazSpace.lg, vertical = TazSpace.sm)
                .semantics { liveRegion = LiveRegionMode.Polite },
        ) {
            Text(lastMessage, color = TazColors.White, fontSize = TazType.captionSize, lineHeight = TazType.captionLine)
        }
    }
}
