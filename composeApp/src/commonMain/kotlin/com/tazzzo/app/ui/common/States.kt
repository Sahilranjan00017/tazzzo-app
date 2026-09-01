package com.tazzzo.app.ui.common

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.tween
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.tazzzo.app.theme.TazColors
import com.tazzzo.app.theme.TazIcons
import com.tazzzo.app.theme.TazMotion
import com.tazzzo.app.theme.TazRadius
import com.tazzzo.app.theme.TazSize
import com.tazzzo.app.theme.TazSpace
import com.tazzzo.app.theme.TazType
import com.tazzzo.app.ui.state.LoadError
import com.tazzzo.app.ui.state.LoadHandle
import com.tazzzo.app.ui.state.UiState

// ---------------------------------------------------------------------------
// Skeletons — a shimmering placeholder reads as faster than a spinner because
// it shows the shape of what is coming.
// ---------------------------------------------------------------------------

@Composable
private fun shimmerAlpha(): Float {
    val transition = rememberInfiniteTransition()
    val alpha by transition.animateFloat(
        initialValue = 0.45f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(TazMotion.ambient), RepeatMode.Reverse)
    )
    return alpha
}

/** A single shimmering block. Compose these into screen-shaped skeletons. */
@Composable
fun SkeletonBlock(
    width: Dp? = null,
    height: Dp = 14.dp,
    modifier: Modifier = Modifier,
    corner: Dp = 6.dp
) {
    val alpha = shimmerAlpha()
    Box(
        (if (width != null) modifier.width(width) else modifier.fillMaxWidth())
            .height(height)
            .clip(RoundedCornerShape(corner))
            .background(TazColors.CardBorder.copy(alpha = alpha))
    )
}

/**
 * Skeleton matching the product card silhouette exactly — same width, same
 * image well, same body padding, same 40dp control block — so nothing shifts
 * when the real card arrives.
 */
@Composable
fun ProductCardSkeleton(modifier: Modifier = Modifier) {
    Column(
        modifier
            .width(ProductCardWidth)
            .clip(TazRadius.card)
            .background(TazColors.Surface)
    ) {
        Box(
            Modifier.fillMaxWidth().height(ProductCardImageHeight)
                .clip(
                    RoundedCornerShape(topStart = TazRadius.cardDp, topEnd = TazRadius.cardDp)
                )
                .background(TazColors.SurfaceSunken)
        )
        Column(
            Modifier.fillMaxWidth().padding(ProductCardPadding),
            verticalArrangement = Arrangement.spacedBy(TazSpace.sm)
        ) {
            SkeletonBlock(height = 13.dp)
            SkeletonBlock(width = 62.dp, height = 11.dp)
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                SkeletonBlock(width = 44.dp, height = 15.dp)
                SkeletonBlock(
                    width = 74.dp, height = TazSize.buttonHeightSm,
                    corner = TazSize.buttonHeightSm / 2
                )
            }
        }
    }
}

/** Skeleton for a horizontal product rail. */
@Composable
fun ProductRailSkeleton(modifier: Modifier = Modifier) {
    Column(modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(TazSpace.md)) {
        SkeletonBlock(width = 150.dp, height = 18.dp, modifier = Modifier.padding(start = TazSpace.gutter))
        Row(
            Modifier.padding(horizontal = TazSpace.gutter),
            horizontalArrangement = Arrangement.spacedBy(TazSpace.md)
        ) {
            repeat(3) { ProductCardSkeleton() }
        }
    }
}

// ---------------------------------------------------------------------------
// Empty / error states
// ---------------------------------------------------------------------------

/** The illustration well: one icon, one sunken circle, no decoration. */
@Composable
private fun StateIcon(icon: ImageVector, tint: Color = TazColors.TextSecondary) {
    Box(
        Modifier.size(72.dp).clip(CircleShape).background(TazColors.SurfaceSunken),
        contentAlignment = Alignment.Center
    ) {
        TazIcon(icon, null, size = TazSize.iconLg, tint = tint)
    }
}

/**
 * Screens still describe an empty state with a familiar glyph; this maps that
 * intent onto the one icon family so no emoji reaches the UI layer.
 */
private fun stateIconFor(emoji: String): ImageVector = when (emoji) {
    "🔍" -> TazIcons.Search
    "🛒" -> TazIcons.Cart
    "📍" -> TazIcons.Location
    "🪙" -> TazIcons.Coin
    "📦" -> TazIcons.Bag
    else -> TazIcons.Inventory
}

@Composable
fun EmptyState(
    emoji: String,
    title: String,
    body: String? = null,
    actionLabel: String? = null,
    onAction: (() -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    Column(
        modifier.fillMaxWidth().padding(TazSpace.xxl),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        StateIcon(stateIconFor(emoji))
        Spacer(Modifier.height(TazSpace.lg))
        Text(
            title, fontSize = TazType.h2Size, fontWeight = TazType.h2Weight,
            lineHeight = TazType.h2Line, color = TazColors.TextPrimary,
            textAlign = TextAlign.Center
        )
        if (body != null) {
            Spacer(Modifier.height(TazSpace.xs))
            Text(
                body, fontSize = TazType.bodySize, color = TazColors.TextSecondary,
                textAlign = TextAlign.Center, lineHeight = TazType.bodyLine
            )
        }
        if (actionLabel != null && onAction != null) {
            Spacer(Modifier.height(TazSpace.xl))
            PillButton(actionLabel, onAction, Modifier.fillMaxWidth())
        }
    }
}

/**
 * Error state with retry. Shows customer-facing copy only — never a status code.
 */
@Composable
fun ErrorState(
    error: LoadError,
    onRetry: (() -> Unit)?,
    modifier: Modifier = Modifier
) {
    Column(
        modifier.fillMaxWidth().padding(TazSpace.xxl),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        StateIcon(
            when (error.kind) {
                LoadError.Kind.Network -> TazIcons.Offline
                LoadError.Kind.Timeout -> TazIcons.Slot
                else -> TazIcons.Error
            },
            tint = TazColors.Danger
        )
        Spacer(Modifier.height(TazSpace.lg))
        Text(
            error.message, fontSize = TazType.h2Size, fontWeight = TazType.h2Weight,
            lineHeight = TazType.h2Line, color = TazColors.TextPrimary,
            textAlign = TextAlign.Center
        )
        Spacer(Modifier.height(TazSpace.xs))
        Text(
            error.hint, fontSize = TazType.bodySize, color = TazColors.TextSecondary,
            textAlign = TextAlign.Center, lineHeight = TazType.bodyLine
        )
        if (onRetry != null && error.isRetryable) {
            Spacer(Modifier.height(TazSpace.xl))
            PillButton("Try again", onRetry)
        }
    }
}

/**
 * Thin persistent banner for connectivity loss.
 *
 * DELIBERATELY NOT WIRED — and this is the exact dependency list, not a TODO.
 * Three things are missing and none of them can be invented here:
 *
 *  1. A connectivity source. There is no HTTP client and no platform
 *     reachability API in the project (no Ktor, no ConnectivityManager, no
 *     NWPathMonitor). Wiring this needs an `expect/actual ConnectivityObserver`
 *     — Android `ConnectivityManager.NetworkCallback`, iOS `NWPathMonitor` —
 *     which is real platform work that must be verified on physical devices.
 *  2. A placement decision. Where this banner sits, and whether it pushes
 *     content or overlays it, is a change to frozen screens and belongs to the
 *     design owner, not to integration work.
 *  3. A policy decision. OS reachability reports "connected" for a captive
 *     portal or a dead uplink. The honest signal is a failed request, which
 *     LoadError.Kind.Network already carries per screen. Whether Tazzzo shows a
 *     global banner at all, or keeps relying on per-screen error states, has
 *     not been decided.
 *
 * Until all three are settled this stays unused rather than being driven by a
 * fabricated signal. Tracked in BLOCKERS.md.
 */
@Composable
fun OfflineBanner(modifier: Modifier = Modifier) {
    Row(
        modifier.fillMaxWidth().background(TazColors.TextPrimary)
            .padding(horizontal = TazSpace.lg, vertical = TazSpace.sm),
        verticalAlignment = Alignment.CenterVertically
    ) {
        TazIcon(TazIcons.Offline, null, size = TazSize.iconXs, tint = TazColors.White)
        Spacer(Modifier.width(TazSpace.sm))
        Text(
            "You're offline — showing your last update",
            color = TazColors.White, fontSize = TazType.captionSize,
            lineHeight = TazType.captionLine
        )
    }
}

// ---------------------------------------------------------------------------
// Host — renders the right thing for a UiState. This is what screens use.
// ---------------------------------------------------------------------------

/**
 * Renders loading / empty / error / content for a [LoadHandle] so no screen
 * ever has to hand-roll the four cases again.
 */
@Composable
fun <T> StateHost(
    handle: LoadHandle<T>,
    modifier: Modifier = Modifier,
    loading: @Composable () -> Unit = { DefaultLoading() },
    empty: @Composable () -> Unit = {
        EmptyState("🧺", "Nothing here yet", "Try another aisle or search for something.")
    },
    error: @Composable (LoadError, () -> Unit) -> Unit = { e, retry -> ErrorState(e, retry) },
    content: @Composable (T) -> Unit
) {
    Box(modifier) {
        when (val state = handle.state) {
            is UiState.Loading -> loading()
            is UiState.Empty -> empty()
            is UiState.Failure -> error(state.error) { handle.retry() }
            is UiState.Success -> content(state.data)
        }
    }
}

@Composable
private fun DefaultLoading() {
    Column(
        Modifier.fillMaxWidth().padding(vertical = TazSpace.xl),
        verticalArrangement = Arrangement.spacedBy(TazSpace.xl)
    ) {
        repeat(2) { ProductRailSkeleton() }
    }
}
