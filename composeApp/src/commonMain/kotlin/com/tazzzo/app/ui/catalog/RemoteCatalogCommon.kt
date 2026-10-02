package com.tazzzo.app.ui.catalog

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.OutlinedTextField
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.tazzzo.app.data.catalog.BannerTone
import com.tazzzo.app.data.catalog.CatalogFailure
import com.tazzzo.app.data.catalog.Pincode
import com.tazzzo.app.data.catalog.RetryPolicy
import com.tazzzo.app.data.catalog.banner
import com.tazzzo.app.data.catalog.hint
import com.tazzzo.app.data.catalog.title
import com.tazzzo.app.data.repository.ServiceLocator
import com.tazzzo.app.theme.TazColors
import com.tazzzo.app.theme.TazIcons
import com.tazzzo.app.theme.TazRadius
import com.tazzzo.app.theme.TazSize
import com.tazzzo.app.theme.TazSpace
import com.tazzzo.app.theme.TazType
import com.tazzzo.app.ui.common.PillButton
import com.tazzzo.app.ui.common.TazIcon
import com.tazzzo.app.ui.interaction.TazPress
import com.tazzzo.app.ui.interaction.tazPressable
import kotlinx.coroutines.delay

/**
 * Shared pieces of the REMOTE-catalogue screens (PR-04C). Real data wiring only: the layout reuses
 * existing tokens and components; the approved visual design is a separate PR.
 */

/** A neutral stand-in for product / category imagery (the image loader is a later PR). */
@Composable
fun NeutralPlaceholder(modifier: Modifier = Modifier, iconSize: androidx.compose.ui.unit.Dp = 28.dp) {
    Box(modifier.background(TazColors.SurfaceSunken), contentAlignment = Alignment.Center) {
        TazIcon(TazIcons.Inventory, null, size = iconSize, tint = TazColors.TextDisabled)
    }
}

/**
 * A catalogue failure with a retry that respects [RetryPolicy]: 429 waits for Retry-After, 503/5xx
 * back off, a lost connection can retry at once. Copy is app-written; server text never reaches it.
 *
 * @param consecutiveFailures how many times in a row this load has failed (caller-owned, so it
 *   survives the panel leaving and re-entering composition while a retry is in flight).
 */
@Composable
fun FailurePanel(
    failure: CatalogFailure,
    consecutiveFailures: Int,
    onRetry: () -> Unit,
    modifier: Modifier = Modifier,
    compact: Boolean = false
) {
    val wait = RetryPolicy.waitSeconds(failure, consecutiveFailures)
    var remaining by remember(failure, consecutiveFailures) { mutableStateOf(wait) }
    LaunchedEffect(failure, consecutiveFailures) {
        while (remaining > 0) { delay(1_000); remaining -= 1 }
    }
    Column(
        modifier.fillMaxWidth().padding(if (compact) TazSpace.md else TazSpace.xxl),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        if (!compact) {
            TazIcon(TazIcons.Error, null, size = 32.dp, tint = TazColors.Danger)
            Spacer(Modifier.height(TazSpace.md))
        }
        Text(
            failure.title, fontSize = if (compact) TazType.bodySize else TazType.h2Size,
            fontWeight = if (compact) FontWeight.SemiBold else TazType.h2Weight,
            color = TazColors.TextPrimary, textAlign = TextAlign.Center
        )
        Spacer(Modifier.height(TazSpace.xs))
        Text(failure.hint, fontSize = TazType.captionSize, color = TazColors.TextSecondary, textAlign = TextAlign.Center)
        if (failure.isRetryable) {
            Spacer(Modifier.height(TazSpace.md))
            PillButton(
                text = if (remaining > 0) "Try again in ${remaining}s" else "Try again",
                onClick = onRetry,
                enabled = remaining == 0
            )
        }
    }
}

/**
 * Where "we deliver to your PIN" is told. States follow [com.tazzzo.app.data.catalog.ServiceabilityState];
 * ETA appears only when the server sent one. Tapping "Change" edits the PIN, which resets every
 * paginated list keyed on it.
 */
@Composable
fun ServiceabilityBannerView(modifier: Modifier = Modifier) {
    val ctx = ServiceLocator.launchContext
    val pin by ctx.pin.collectAsState()
    val state by ctx.state.collectAsState()
    var editing by remember { mutableStateOf(false) }

    val banner = state.banner(pin)
    Column(modifier.fillMaxWidth()) {
        if (banner != null) {
            val (bg, ink) = when (banner.tone) {
                BannerTone.Positive -> TazColors.SuccessSoft to TazColors.Success
                BannerTone.Warning -> TazColors.WarningSoft to TazColors.Warning
                BannerTone.Error -> TazColors.DangerSoft to TazColors.Danger
                BannerTone.Neutral -> TazColors.SurfaceSunken to TazColors.TextSecondary
            }
            Row(
                Modifier.fillMaxWidth().background(bg).padding(horizontal = TazSpace.lg, vertical = TazSpace.sm),
                verticalAlignment = Alignment.CenterVertically
            ) {
                TazIcon(TazIcons.Location, null, size = 16.dp, tint = ink)
                Spacer(Modifier.size(TazSpace.sm))
                Text(banner.text, fontSize = TazType.captionSize, fontWeight = FontWeight.SemiBold, color = ink, modifier = Modifier.weight(1f))
                if (banner.retryable) BannerAction("Retry", ink) { ctx.refresh() }
                BannerAction(if (editing) "Cancel" else "Change", ink) { editing = !editing }
            }
        }
        if (editing) PinEditor(onDone = { editing = false })
    }
}

@Composable
private fun BannerAction(text: String, ink: Color, onClick: () -> Unit) {
    Text(
        text, fontSize = TazType.captionSize, fontWeight = FontWeight.Bold, color = ink,
        modifier = Modifier.defaultMinSize(minHeight = 32.dp).clip(TazRadius.chip)
            .tazPressable(onClick = onClick, pressScale = TazPress.compact)
            .padding(horizontal = TazSpace.sm, vertical = TazSpace.xs)
    )
}

@Composable
private fun PinEditor(onDone: () -> Unit) {
    val ctx = ServiceLocator.launchContext
    var text by remember { mutableStateOf("") }
    val valid = Pincode.isValid(text)
    Row(
        Modifier.fillMaxWidth().background(TazColors.Surface).padding(TazSpace.md),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(TazSpace.sm)
    ) {
        OutlinedTextField(
            value = text,
            onValueChange = { text = it.filter { c -> c in '0'..'9' }.take(6) },
            modifier = Modifier.weight(1f),
            singleLine = true,
            placeholder = { Text("6-digit PIN code", fontSize = TazType.bodySize) },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)
        )
        // A typed PIN is a MANUAL location: it clears any selected saved address, so only one source is ever active.
        PillButton("Update", onClick = { if (ServiceLocator.deliveryLocation.setManualPin(text)) onDone() }, enabled = valid)
    }
}
