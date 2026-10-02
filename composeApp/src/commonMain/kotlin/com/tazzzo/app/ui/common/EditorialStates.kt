package com.tazzzo.app.ui.common

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.tazzzo.app.data.catalog.CatalogFailure
import com.tazzzo.app.data.catalog.RetryPolicy
import com.tazzzo.app.data.catalog.hint
import com.tazzzo.app.data.catalog.title
import com.tazzzo.app.theme.TazColors
import com.tazzzo.app.theme.TazIcons
import com.tazzzo.app.theme.TazRadius
import com.tazzzo.app.theme.TazSpace
import com.tazzzo.app.theme.TazType
import kotlinx.coroutines.delay

/*
 * The shared editorial states of the catalogue surfaces (UI-03): empty, failed and loading, in the approved Tazzzo
 * language — cream surface, a sage circular well with one icon, a Newsreader headline, concise Poppins support and at
 * most one recovery action. Customer copy only: no status codes, no exception names, no server text.
 */

/** The illustration well: one icon in a sage circle. */
@Composable
private fun EditorialStateIcon(icon: ImageVector) {
    Box(Modifier.size(84.dp).clip(CircleShape).background(TazColors.GreenSoft), contentAlignment = Alignment.Center) {
        TazIcon(icon, null, size = 34.dp, tint = TazColors.BrandEditorial)
    }
}

@Composable
fun EditorialEmptyState(
    icon: ImageVector,
    title: String,
    body: String? = null,
    actionLabel: String? = null,
    onAction: (() -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    Column(
        modifier.fillMaxWidth().padding(horizontal = TazSpace.xxl, vertical = TazSpace.xxxl),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        EditorialStateIcon(icon)
        Spacer(Modifier.height(TazSpace.xl))
        EditorialText(listOf(plain(title)), size = STATE_TITLE_SIZE, lineHeight = STATE_TITLE_LINE, color = TazColors.TextPrimary)
        if (body != null) {
            Spacer(Modifier.height(TazSpace.sm))
            Text(body, fontSize = TazType.bodySize, lineHeight = TazType.bodyLine, color = TazColors.TextSecondary, textAlign = TextAlign.Center)
        }
        if (actionLabel != null && onAction != null) {
            Spacer(Modifier.height(TazSpace.xxl))
            TazzzoPrimaryButton(actionLabel, onClick = onAction, modifier = Modifier.fillMaxWidth(0.76f))
        }
    }
}

/**
 * A catalogue failure in the editorial language, with a retry that respects [RetryPolicy] (429 waits for Retry-After,
 * 5xx backs off, a lost connection may retry at once). [consecutiveFailures] is caller-owned so the countdown survives
 * the panel leaving and re-entering composition.
 */
@Composable
fun EditorialFailureState(
    failure: CatalogFailure,
    consecutiveFailures: Int,
    onRetry: () -> Unit,
    modifier: Modifier = Modifier,
    compact: Boolean = false
) {
    val wait = RetryPolicy.waitSeconds(failure, consecutiveFailures)
    var remaining by remember(failure, consecutiveFailures) { mutableStateOf(wait) }
    LaunchedEffect(failure, consecutiveFailures) { while (remaining > 0) { delay(1_000); remaining -= 1 } }
    Column(
        modifier.fillMaxWidth().padding(horizontal = TazSpace.xxl, vertical = if (compact) TazSpace.lg else TazSpace.xxxl)
            .semantics { liveRegion = LiveRegionMode.Polite },
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        if (!compact) { EditorialStateIcon(TazIcons.Offline); Spacer(Modifier.height(TazSpace.xl)) }
        EditorialText(
            listOf(plain(failure.title)),
            size = if (compact) TazType.editorialSectionSize else STATE_TITLE_SIZE,
            lineHeight = if (compact) TazType.editorialSectionLine else STATE_TITLE_LINE,
            color = TazColors.TextPrimary
        )
        Spacer(Modifier.height(TazSpace.sm))
        Text(failure.hint, fontSize = TazType.bodySize, lineHeight = TazType.bodyLine, color = TazColors.TextSecondary, textAlign = TextAlign.Center)
        if (failure.isRetryable) {
            Spacer(Modifier.height(if (compact) TazSpace.lg else TazSpace.xxl))
            TazzzoPrimaryButton(
                if (remaining > 0) "Try again in ${remaining}s" else "Try again",
                onClick = onRetry, enabled = remaining == 0, trailingArrow = false, modifier = Modifier.fillMaxWidth(0.76f)
            )
        }
    }
}

private val STATE_TITLE_SIZE = 24.sp
private val STATE_TITLE_LINE = 28.sp

// ---- layout-shaped skeletons -----------------------------------------------------------------------------------------

/** A category tile's silhouette: square well, one caption line. */
@Composable
fun CategoryTileSkeleton(modifier: Modifier = Modifier) {
    Column(modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        Box(Modifier.fillMaxWidth().aspectRatio(1f).clip(TazRadius.tile).background(TazColors.CardBorder.copy(alpha = 0.7f)))
        Spacer(Modifier.height(TazSpace.sm))
        SkeletonBlock(width = 56.dp, height = 11.dp)
    }
}

/** The canonical product card's silhouette: square well, two name lines, a price and a round control. */
@Composable
fun CatalogCardSkeleton(modifier: Modifier = Modifier, width: Dp? = null) {
    Column((if (width != null) modifier.width(width) else modifier.fillMaxWidth()).clip(TazRadius.card).background(TazColors.Surface).padding(TazSpace.sm)) {
        Box(Modifier.fillMaxWidth().aspectRatio(1f).clip(TazRadius.tile).background(TazColors.SurfaceSunken))
        Spacer(Modifier.height(TazSpace.sm))
        SkeletonBlock(height = 12.dp)
        Spacer(Modifier.height(TazSpace.xs))
        SkeletonBlock(width = 64.dp, height = 12.dp)
        Spacer(Modifier.height(TazSpace.sm))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            SkeletonBlock(width = 48.dp, height = 15.dp)
            SkeletonBlock(width = 36.dp, height = 36.dp, corner = 18.dp)
        }
    }
}
