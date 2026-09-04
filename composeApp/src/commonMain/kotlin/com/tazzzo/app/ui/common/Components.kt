package com.tazzzo.app.ui.common

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.scale
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.disabled
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.tazzzo.app.LocalAppState
import com.tazzzo.app.Screen
import com.tazzzo.app.config.AppConfig
import com.tazzzo.app.config.BrandCopy
import com.tazzzo.app.config.DeliveryCopy
import com.tazzzo.app.data.model.Availability
import com.tazzzo.app.data.model.Category
import com.tazzzo.app.data.model.Product
import androidx.compose.material3.CircularProgressIndicator
import com.tazzzo.app.ui.interaction.TazHaptic
import com.tazzzo.app.ui.interaction.TazPress
import com.tazzzo.app.ui.interaction.tazPressable
import com.tazzzo.app.ui.interaction.tazPressableCard
import com.tazzzo.app.ui.interaction.tazPressableIcon
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.SizeTransform
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.animation.Crossfade
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.semantics.Role
import com.tazzzo.app.theme.TazMotion
import com.tazzzo.app.ui.interaction.rememberHaptics
import com.tazzzo.app.theme.MotionSettings
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.foundation.lazy.LazyListState
import com.tazzzo.app.theme.TazColors
import com.tazzzo.app.theme.TazIcons
import com.tazzzo.app.theme.TazRadius
import com.tazzzo.app.theme.TazSize
import com.tazzzo.app.theme.TazSpace
import com.tazzzo.app.theme.TazType
import org.jetbrains.compose.resources.DrawableResource
import org.jetbrains.compose.resources.painterResource
import tazzzo.resources.Res
import tazzzo.resources.tazzzo_logo
import tazzzo.resources.hero_basket
import tazzzo.resources.banner_coins
import tazzzo.resources.cat_fruits
import tazzzo.resources.cat_dairy
import tazzzo.resources.cat_atta
import tazzzo.resources.cat_oil
import tazzzo.resources.cat_meat
import tazzzo.resources.cat_munchies
import tazzzo.resources.cat_drinks
import tazzzo.resources.cat_tea
import tazzzo.resources.cat_instant
import tazzzo.resources.cat_sweet
import tazzzo.resources.cat_bakery
import tazzzo.resources.cat_personal
import tazzzo.resources.cat_skincare
import tazzzo.resources.cat_pharma
import tazzzo.resources.cat_baby
import tazzzo.resources.cat_cleaning
import tazzzo.resources.cat_home
import tazzzo.resources.cat_pet
import tazzzo.resources.cat_paan

// ---------------------------------------------------------------------------
// Icon primitive
// ---------------------------------------------------------------------------

/**
 * The single way UI iconography enters a screen.
 *
 * Emoji are CONTENT (a product's 🍅, a category tile). Every control — back,
 * search, cart, steppers, chevrons, status — draws from [TazIcons] through this
 * helper so icons tint with the theme, scale with the type ramp and carry
 * semantics instead of being untinted, unlabelled glyphs.
 */
@Composable
fun TazIcon(
    icon: ImageVector,
    contentDescription: String?,
    modifier: Modifier = Modifier,
    size: Dp = TazSize.iconMd,
    tint: Color = TazColors.TextPrimary
) = Icon(icon, contentDescription, modifier.size(size), tint)

// ---------------------------------------------------------------------------
// Product-card geometry — shared with the skeleton in States.kt so the
// placeholder is always the exact silhouette of the thing that replaces it.
// ---------------------------------------------------------------------------

internal val ProductCardWidth: Dp = 158.dp
internal val ProductCardImageHeight: Dp = 104.dp
/** Card image container aspect — 158w x 104h ≈ 1.52. Fixed so the grid aligns. */
internal const val ProductCardImageAspect: Float = 1.52f
internal val ProductCardPadding: Dp = 10.dp
internal val ProductNameBlockHeight: Dp = 34.dp   // exactly two lines of productNameLine

/** Below this content width the price and the control cannot share a line. */
private val CardRowThreshold: Dp = 136.dp

// ---------------------------------------------------------------------------
// Brand
// ---------------------------------------------------------------------------

@Composable
fun LogoImage(height: Dp, modifier: Modifier = Modifier) {
    Image(
        painter = painterResource(Res.drawable.tazzzo_logo),
        contentDescription = "Tazzzo",
        modifier = modifier.height(height)
    )
}

/** TAZZZO wordmark rendered as text (for places where the PNG is too heavy). */
@Composable
fun TazWordmark(fontSize: androidx.compose.ui.unit.TextUnit) {
    Row {
        Text("TA", color = TazColors.Green, fontSize = fontSize, fontWeight = FontWeight.ExtraBold)
        Text("ZZZ", color = TazColors.Orange, fontSize = fontSize, fontWeight = FontWeight.ExtraBold)
        Text("O", color = TazColors.Green, fontSize = fontSize, fontWeight = FontWeight.ExtraBold)
    }
}

// ---------------------------------------------------------------------------
// Layout helpers
// ---------------------------------------------------------------------------

@Composable
fun TazTopBar(
    title: String,
    onBack: (() -> Unit)? = null,
    trailing: (@Composable RowScope.() -> Unit)? = null
) {
    Column(Modifier.fillMaxWidth().background(TazColors.Surface).statusBarsPadding()) {
        Row(
            Modifier.fillMaxWidth().height(TazSize.topBarHeight)
                .padding(horizontal = TazSpace.sm),
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (onBack != null) {
                Box(
                    Modifier.size(TazSize.touchTarget).clip(CircleShape)
                        .background(TazColors.SurfaceSunken)
                        .tazPressable(onClick = { onBack() }, pressScale = TazPress.compact),
                    contentAlignment = Alignment.Center
                ) {
                    TazIcon(TazIcons.Back, "Back", size = TazSize.iconSm)
                }
                Spacer(Modifier.width(TazSpace.sm))
            } else {
                Spacer(Modifier.width(TazSpace.xs))
            }
            Text(
                title, fontSize = TazType.h2Size, fontWeight = TazType.h2Weight,
                color = TazColors.TextPrimary, modifier = Modifier.weight(1f),
                maxLines = 1, overflow = TextOverflow.Ellipsis
            )
            if (trailing != null) trailing()
        }
        // hairline: separates the bar from cream content without a shadow
        Box(Modifier.fillMaxWidth().height(1.dp).background(TazColors.CardBorder))
    }
}

@Composable
fun SectionHeader(title: String, actionLabel: String? = null, onAction: (() -> Unit)? = null) {
    Row(
        Modifier.fillMaxWidth().padding(horizontal = TazSpace.gutter, vertical = TazSpace.sm),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            title, fontSize = TazType.h2Size, fontWeight = TazType.h2Weight,
            color = TazColors.TextPrimary, maxLines = 1, overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f)
        )
        if (actionLabel != null) {
            Row(
                Modifier.clip(TazRadius.pill).tazPressable(onClick = { onAction?.invoke() }, pressScale = TazPress.compact)
                    .padding(horizontal = TazSpace.sm, vertical = TazSpace.xs),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    actionLabel, fontSize = TazType.captionSize, fontWeight = TazType.savingsWeight,
                    color = TazColors.Success, maxLines = 1, overflow = TextOverflow.Ellipsis
                )
                Spacer(Modifier.width(TazSpace.xs))
                TazIcon(
                    TazIcons.Chevron, null,
                    size = TazSize.iconXs, tint = TazColors.Success
                )
            }
        }
    }
}

@Composable
fun PillButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    filled: Boolean = true,
    color: Color = TazColors.Green,
    enabled: Boolean = true,
    disabledHint: String? = null,
    loading: Boolean = false,
    loadingText: String? = null,
    haptic: TazHaptic? = TazHaptic.Tap
) {
    // A loading button is NOT a disabled button and must not look like one:
    // disabled means "you may not do this", loading means "I am doing it".
    // But it is equally not tappable — the old behaviour rewrote the label to
    // "Sending OTP…" while leaving the control fully enabled, so a second tap
    // produced a ripple and silently did nothing. The component now owns that,
    // so no caller can get it wrong again.
    val interactive = enabled && !loading

    val bg = when {
        loading -> if (filled) color else TazColors.Surface
        !enabled -> TazColors.GreenDisabled
        filled -> color
        else -> TazColors.Surface
    }
    val fg = when {
        loading -> if (filled) TazColors.White else color
        !enabled -> TazColors.TextPrimary          // dark-on-light: readable disabled state
        filled -> TazColors.White
        else -> color
    }
    Box(
        modifier
            .defaultMinSize(minHeight = TazSize.buttonHeight)
            .clip(TazRadius.pill)
            .background(bg)
            .border(
                BorderStroke(if (filled || !enabled) 0.dp else 1.5.dp, color),
                TazRadius.pill
            )
            .semantics {
                if (loading) {
                    // Announced as busy, not as broken.
                    stateDescription = loadingText ?: "Working"
                } else if (!enabled) {
                    disabled()
                    disabledHint?.let { stateDescription = it }
                }
            }
            .tazPressable(
                onClick = onClick,
                enabled = interactive,
                pressScale = TazPress.control,
                haptic = haptic
            )
            .padding(vertical = TazSpace.md, horizontal = TazSpace.xxl),
        contentAlignment = Alignment.Center
    ) {
        Row(
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (loading) {
                // Sized to the cap height of the label so the button never
                // changes height between idle and loading.
                CircularProgressIndicator(
                    modifier = Modifier.size(18.dp),
                    color = fg,
                    strokeWidth = 2.dp
                )
                Spacer(Modifier.width(TazSpace.sm))
            }
            Text(
                // two lines rather than one: an action label that truncates loses
                // its meaning, and long labels exist ("Verify & Start Shopping").
                if (loading) (loadingText ?: text) else text,
                color = fg, fontWeight = TazType.buttonWeight, fontSize = TazType.buttonSize,
                textAlign = TextAlign.Center, maxLines = 2, overflow = TextOverflow.Ellipsis
            )
        }
    }
}

// ---------------------------------------------------------------------------
// Marquee (auto-scrolling row, used on onboarding)
// ---------------------------------------------------------------------------

@Composable
fun MarqueeRow(
    reverse: Boolean = false,
    speedPxPerSec: Float = 40f,
    content: @Composable RowScope.() -> Unit
) {
    val scroll = rememberScrollState()
    Row(Modifier.fillMaxWidth().horizontalScroll(scroll, enabled = false)) {
        content()
        content()
    }
    LaunchedEffect(reverse, MotionSettings.ambientEnabled) {
        if (!MotionSettings.ambientEnabled) return@LaunchedEffect   // static row
        while (scroll.maxValue == 0 || scroll.maxValue == Int.MAX_VALUE) {
            withFrameNanos { }
        }
        val period = scroll.maxValue / 2
        if (period <= 0) return@LaunchedEffect
        val duration = ((period / speedPxPerSec) * 1000).toInt().coerceAtLeast(1000)
        while (true) {
            if (!reverse) {
                scroll.scrollTo(0)
                scroll.animateScrollTo(period, tween(duration, easing = LinearEasing))
            } else {
                scroll.scrollTo(period)
                scroll.animateScrollTo(0, tween(duration, easing = LinearEasing))
            }
        }
    }
}

// ---------------------------------------------------------------------------
// Small atoms
// ---------------------------------------------------------------------------

/** Content emoji (product / category art) in a tinted well — never UI glyphs. */
@Composable
fun EmojiBox(
    emoji: String,
    fontSize: androidx.compose.ui.unit.TextUnit,
    bg: Color,
    modifier: Modifier = Modifier,
    corner: Dp = TazRadius.cardDp
) {
    Box(
        modifier.clip(RoundedCornerShape(corner)).background(bg),
        contentAlignment = Alignment.Center
    ) { Text(emoji, fontSize = fontSize) }
}

@Composable
fun DiscountBadge(percent: Int, modifier: Modifier = Modifier) {
    if (percent > 0) {
        Box(
            modifier
                .clip(RoundedCornerShape(topStart = TazRadius.cardDp, bottomEnd = TazRadius.chipDp))
                .background(TazColors.Orange)
                .padding(horizontal = TazSpace.sm, vertical = 3.dp)
        ) {
            Text(
                "$percent% OFF", color = TazColors.White, fontSize = TazType.microSize,
                fontWeight = TazType.microWeight, maxLines = 1
            )
        }
    }
}

/**
 * Delivery-time chip. Renders NOTHING when there is no verified promise —
 * silence is better than a claim the backend cannot honour.
 */
@Composable
fun EtaChip(minutes: Int) {
    val label = DeliveryCopy.short(AppConfig.deliveryPromise) ?: return
    EtaChipLabel(label)
}

@Composable
private fun EtaChipLabel(label: String) {
    Row(
        Modifier.clip(TazRadius.chip).background(TazColors.SurfaceSunken)
            .padding(horizontal = TazSpace.sm, vertical = TazSpace.xxs),
        verticalAlignment = Alignment.CenterVertically
    ) {
        TazIcon(TazIcons.Slot, null, size = TazSize.iconXs, tint = TazColors.TextSecondary)
        Spacer(Modifier.width(TazSpace.xs))
        Text(
            label, fontSize = TazType.microSize, fontWeight = TazType.microWeight,
            color = TazColors.TextSecondary, maxLines = 1, overflow = TextOverflow.Ellipsis
        )
    }
}

private fun formatCount(count: Int): String =
    if (count >= 1000) "${count / 1000}.${(count % 1000) / 100}k" else "$count"

@Composable
fun RatingRow(rating: Double, count: Int) {
    // Honest-copy rule: no rating source exists yet, so zero-count ratings
    // render nothing rather than a fabricated score.
    if (count <= 0) return
    Row(verticalAlignment = Alignment.CenterVertically) {
        TazIcon(TazIcons.Star, null, size = TazSize.iconXs, tint = TazColors.CoinInk)
        Spacer(Modifier.width(TazSpace.xxs))
        Text(
            "$rating (${formatCount(count)})", fontSize = TazType.microSize,
            color = TazColors.TextSecondary, maxLines = 1
        )
    }
}

@Composable
fun PriceColumn(price: Int, mrp: Int) {
    Column {
        Text(
            "₹$price", fontSize = TazType.priceSize, fontWeight = TazType.priceWeight,
            color = TazColors.TextPrimary, maxLines = 1, overflow = TextOverflow.Ellipsis
        )
        if (mrp > price) {
            Text(
                "₹$mrp", fontSize = TazType.mrpSize, color = TazColors.TextTertiary,
                textDecoration = TextDecoration.LineThrough,
                maxLines = 1, overflow = TextOverflow.Ellipsis
            )
        }
    }
}

/**
 * Card price block: selling price and struck MRP on one baseline.
 *
 * The MRP is the flexible half (`weight(fill = false)` + ellipsis) so a long
 * price never pushes the stepper off the card — the selling price is measured
 * first and always renders in full.
 */
@Composable
private fun CardPriceBlock(price: Int, mrp: Int, modifier: Modifier = Modifier) {
    Row(modifier, verticalAlignment = Alignment.Bottom) {
        Text(
            "₹$price", fontSize = TazType.priceSize, fontWeight = TazType.priceWeight,
            color = TazColors.TextPrimary, maxLines = 1, overflow = TextOverflow.Ellipsis
        )
        if (mrp > price) {
            Spacer(Modifier.width(6.dp))
            Text(
                "₹$mrp", fontSize = TazType.mrpSize, color = TazColors.TextTertiary,
                textDecoration = TextDecoration.LineThrough,
                maxLines = 1, overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f, fill = false)
            )
        }
    }
}

// ---------------------------------------------------------------------------
// Coin chip + mic button (top bar, near each other as per spec)
// ---------------------------------------------------------------------------

@Composable
fun CoinChip(balance: Int, onClick: () -> Unit) {
    Row(
        Modifier.semantics { contentDescription = "Tazzzo Coins balance: $balance" }
            .defaultMinSize(minHeight = TazSize.touchTarget)
            .clip(TazRadius.pill).background(TazColors.CoinSoft)
            .border(BorderStroke(1.dp, TazColors.CoinInk), TazRadius.pill)
            .tazPressable(onClick = { onClick() }, pressScale = TazPress.compact).padding(horizontal = TazSpace.md, vertical = TazSpace.xs),
        verticalAlignment = Alignment.CenterVertically
    ) {
        TazIcon(TazIcons.Coin, null, size = TazSize.iconSm, tint = TazColors.CoinInk)
        Spacer(Modifier.width(TazSpace.xs))
        Text(
            "$balance", fontSize = TazType.captionSize, fontWeight = FontWeight.Bold,
            color = TazColors.TextPrimary, maxLines = 1
        )
    }
}

@Composable
fun MicButton(size: Dp = TazSize.micButton, onClick: () -> Unit) {
    // Two defects lived here, both invisible on screen and both found by an
    // instrumented test rather than by reading the code:
    //
    //  1. The LABEL was on the outer box and the CLICK was on the inner one, so
    //     a screen-reader user could focus "Voice shopping — coming soon" and
    //     had no way to activate it — the focused node carried no action.
    //  2. The 44dp `defaultMinSize` was on the outer box too, while the
    //     clickable was only the 36dp circle. The code looked like it enforced
    //     the accessibility minimum; the actual touch target was 36dp.
    //
    // Both are fixed by putting the label, the action and the touch area on one
    // node, and letting the circle be purely decorative inside it.
    Box(
        Modifier
            .defaultMinSize(minWidth = TazSize.touchTarget, minHeight = TazSize.touchTarget)
            .semantics(mergeDescendants = true) {
                contentDescription = "Voice shopping — coming soon"
            }
            .tazPressable(onClick = onClick, pressScale = TazPress.compact),
        contentAlignment = Alignment.Center
    ) {
        Box(
            Modifier.size(size).clip(CircleShape).background(TazColors.Green),
            contentAlignment = Alignment.Center
        ) { TazIcon(TazIcons.Mic, null, size = TazSize.iconSm, tint = TazColors.White) }
    }
}

// ---------------------------------------------------------------------------
// Product card + quantity stepper (the small cards used everywhere)
// ---------------------------------------------------------------------------

/**
 * The add / adjust control.
 *
 * Three states, one silhouette (pill, [TazSize.buttonHeightSm] tall) so the
 * card never reflows when a customer adds an item:
 *  - unavailable → sunken "Out of stock" chip
 *  - empty       → outlined success pill, "ADD"
 *  - in cart     → filled success pill with − / quantity / +
 */
@Composable
fun QuantityStepper(product: Product, modifier: Modifier = Modifier) {
    val app = LocalAppState.current
    val haptics = rememberHaptics()
    val qty = app.quantityOf(product)

    if (!product.isPurchasable) {
        Box(
            modifier.defaultMinSize(minWidth = 74.dp, minHeight = TazSize.buttonHeightSm)
                .clip(TazRadius.pill).background(TazColors.SurfaceSunken)
                .padding(horizontal = TazSpace.md, vertical = TazSpace.sm),
            contentAlignment = Alignment.Center
        ) {
            Text(
                if (product.availability == Availability.NotServiceable) "Unavailable" else "Out of stock",
                color = TazColors.TextSecondary, fontSize = TazType.microSize,
                fontWeight = TazType.microWeight, maxLines = 1, overflow = TextOverflow.Ellipsis
            )
        }
        return
    }

    val atLimit = qty >= product.purchasableLimit

    // ONE control that transforms, not two controls that swap.
    //
    // The pill silhouette is held constant (same min width, same height, same
    // radius) and only its interior crossfades, so the card never reflows and
    // the customer reads it as the ADD button *becoming* the stepper. The
    // container keeps its own identity across the change; only `qty == 0`
    // drives the interior.
    val inCart = qty > 0
    val containerColor by animateColorAsState(
        targetValue = if (inCart) TazColors.Success else TazColors.Surface,
        animationSpec = tween(TazMotion.fast),
        label = "stepperContainer"
    )
    Box(
        modifier
            .defaultMinSize(minWidth = 74.dp)
            .height(TazSize.buttonHeightSm)
            .clip(TazRadius.pill)
            .background(containerColor)
            .border(
                BorderStroke(if (inCart) 0.dp else 1.5.dp, TazColors.Success),
                TazRadius.pill
            ),
        contentAlignment = Alignment.Center
    ) {
        Crossfade(targetState = inCart, animationSpec = tween(TazMotion.fast), label = "stepper") { showStepper ->
            if (!showStepper) {
                Box(
                    Modifier
                        .fillMaxSize()
                        // Label merged BEFORE the pressable so it lands on the
                        // same accessibility node as the action (verified: the
                        // reverse order splits them into two nodes).
                        .semantics(mergeDescendants = true) {
                            contentDescription = "Add ${product.name} to cart"
                        }
                        .tazPressable(
                            onClick = { if (app.addToCart(product)) haptics.perform(TazHaptic.Add) },
                            pressScale = TazPress.control,
                            role = Role.Button
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        "ADD", color = TazColors.Success, fontSize = TazType.buttonSize,
                        fontWeight = TazType.buttonWeight, maxLines = 1
                    )
                }
            } else {
                Row(
                    Modifier.fillMaxSize(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // 44dp TOUCH target inside a 40dp VISUAL pill: the design
                    // spec's accessibility minimum was being violated by reusing
                    // the silhouette height as the hit area. The pill still
                    // renders at buttonHeightSm; only the touch region grows.
                    StepperTouchTarget(
                        contentDescription = "Decrease quantity of ${product.name}",
                        onClick = {
                            app.removeFromCart(product)
                            haptics.perform(TazHaptic.Add)
                        }
                    ) {
                        TazIcon(TazIcons.Minus, null, size = TazSize.iconSm, tint = TazColors.White)
                    }
                    // Quantity animates so a change is never silent, even when
                    // the finger is covering the + button that caused it.
                    AnimatedContent(
                        targetState = qty,
                        transitionSpec = {
                            if (targetState > initialState) {
                                (slideInVertically { it / 2 } + fadeIn(tween(TazMotion.fast))) togetherWith
                                    (slideOutVertically { -it / 2 } + fadeOut(tween(TazMotion.fast)))
                            } else {
                                (slideInVertically { -it / 2 } + fadeIn(tween(TazMotion.fast))) togetherWith
                                    (slideOutVertically { it / 2 } + fadeOut(tween(TazMotion.fast)))
                            }.using(SizeTransform(clip = false))
                        },
                        label = "qty"
                    ) { value ->
                        Text(
                            "$value", color = TazColors.White, fontSize = TazType.buttonSize,
                            fontWeight = FontWeight.Bold, textAlign = TextAlign.Center, maxLines = 1
                        )
                    }
                    StepperTouchTarget(
                        contentDescription =
                            if (atLimit) "Maximum quantity of ${product.name} reached"
                            else "Increase quantity of ${product.name}",
                        onClick = {
                            if (atLimit) {
                                // The screen barely changes when we refuse, so the
                                // hand has to be told. This is the canonical haptic.
                                haptics.perform(TazHaptic.Limit)
                                val stock = product.availability
                                app.transientMessage =
                                    if (stock is Availability.LowStock) "Only ${stock.remaining} left in stock"
                                    else "Limit of ${product.maxOrderQuantity} per order"
                            } else {
                                if (app.addToCart(product)) haptics.perform(TazHaptic.Add)
                            }
                        }
                    ) {
                        TazIcon(
                            TazIcons.Plus, null, size = TazSize.iconSm,
                            tint = if (atLimit) TazColors.White.copy(alpha = 0.45f) else TazColors.White
                        )
                    }
                }
            }
        }
    }
}

/**
 * A stepper control whose TOUCH area meets the 44dp accessibility minimum
 * while its VISUAL footprint stays at the compact 40dp pill height.
 *
 * The extra 4dp is taken outside the drawn pill, so nothing moves on screen.
 */
@Composable
private fun StepperTouchTarget(
    contentDescription: String,
    onClick: () -> Unit,
    content: @Composable () -> Unit
) {
    Box(
        Modifier
            .size(TazSize.buttonHeightSm)
            .wrapContentSize(unbounded = true)
            .size(TazSize.touchTarget)
            .semantics(mergeDescendants = true) { this.contentDescription = contentDescription }
            .tazPressable(
                onClick = onClick,
                pressScale = TazPress.compact,
                role = Role.Button
            ),
        contentAlignment = Alignment.Center
    ) {
        content()
    }
}

@Composable
fun ProductCard(product: Product, modifier: Modifier = Modifier) {
    val app = LocalAppState.current
    Box(
        modifier = modifier
            .width(ProductCardWidth)
            .shadow(2.dp, TazRadius.card, spotColor = Color.Black.copy(alpha = 0.18f))
            .clip(TazRadius.card)
            .background(TazColors.Surface)
            .border(BorderStroke(1.dp, TazColors.CardBorder), TazRadius.card)
            // Whole-card press response, clipped to the card's own 14dp radius.
            // No haptic: this is the most repeated tap in the app and a tick on
            // every product view would become noise. The scale answers the finger.
            .tazPressableCard(
                onClick = { app.navigate(Screen.ProductDetail(product.id)) },
                shape = TazRadius.card
            )
    ) {
        Column(Modifier.fillMaxWidth()) {
            // --- image well: flat, sunken, one colour for the whole grid ------
            // One image architecture for every product surface — fixed aspect,
            // Fit (never Crop), skeleton while loading, graceful fallback.
            ProductImage(
                product = product,
                modifier = Modifier.fillMaxWidth()
                    .clip(
                        RoundedCornerShape(
                            topStart = TazRadius.cardDp, topEnd = TazRadius.cardDp
                        )
                    ),
                aspectRatio = ProductCardImageAspect,
                glyphSize = 44.sp,
                // out of stock dims the produce, never the price or the name
                dimmed = !product.isPurchasable
            )

            // --- body ---------------------------------------------------------
            Column(Modifier.fillMaxWidth().padding(ProductCardPadding)) {
                Text(
                    product.name,
                    fontSize = TazType.productNameSize,
                    fontWeight = TazType.productNameWeight,
                    lineHeight = TazType.productNameLine,
                    color = TazColors.TextPrimary,
                    maxLines = 2, overflow = TextOverflow.Ellipsis,
                    // reserves two lines so short and long names align across the grid
                    modifier = Modifier.fillMaxWidth().heightIn(min = ProductNameBlockHeight)
                )
                Spacer(Modifier.height(TazSpace.xxs))
                Text(
                    product.unit, fontSize = TazType.unitSize, color = TazColors.TextTertiary,
                    maxLines = 1, overflow = TextOverflow.Ellipsis
                )
                // Honest copy: nothing renders unless the promise is verified.
                DeliveryCopy.short(AppConfig.deliveryPromise)?.let { eta ->
                    Spacer(Modifier.height(TazSpace.xxs))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        TazIcon(
                            TazIcons.Slot, null,
                            size = TazSize.iconXs, tint = TazColors.TextTertiary
                        )
                        Spacer(Modifier.width(TazSpace.xs))
                        Text(
                            eta, fontSize = TazType.microSize, fontWeight = TazType.microWeight,
                            color = TazColors.TextTertiary, maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
                Spacer(Modifier.height(TazSpace.sm))

                // Price + control. On a wide card they share a line; on the
                // narrow two-column grids (a 84dp sidebar leaves ~110dp of
                // content) they stack so neither is ever truncated.
                BoxWithConstraints(Modifier.fillMaxWidth()) {
                    val sideBySide = product.isPurchasable && maxWidth >= CardRowThreshold
                    if (sideBySide) {
                        Row(
                            Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            CardPriceBlock(product.price, product.mrp, Modifier.weight(1f))
                            QuantityStepper(product)
                        }
                    } else {
                        Column(Modifier.fillMaxWidth()) {
                            CardPriceBlock(product.price, product.mrp, Modifier.fillMaxWidth())
                            Spacer(Modifier.height(TazSpace.sm))
                            QuantityStepper(product, Modifier.fillMaxWidth())
                        }
                    }
                }
            }
        }

        // Both badges live in one top row: on a narrow two-column grid an
        // absolutely-positioned pair would overlap in the middle.
        Row(
            Modifier.fillMaxWidth().align(Alignment.TopStart),
            verticalAlignment = Alignment.Top
        ) {
            DiscountBadge(product.discountPercent)
            Spacer(Modifier.weight(1f))
            val stock = product.availability
            if (stock is Availability.LowStock) {
                Box(
                    Modifier.padding(TazSpace.sm)
                        .clip(TazRadius.chip).background(TazColors.WarningSoft)
                        .padding(horizontal = 6.dp, vertical = TazSpace.xxs)
                ) {
                    Text(
                        "Only ${stock.remaining} left", color = TazColors.Warning,
                        fontSize = TazType.microSize, fontWeight = TazType.microWeight,
                        maxLines = 1, overflow = TextOverflow.Ellipsis
                    )
                }
            }
        }
    }
}

/**
 * Horizontal product rail.
 *
 * Two things here are load-bearing, both learned the hard way in E1/E2.
 *
 * 1. **The rail's own scroll position is retained**, keyed by [title]. A rail
 *    is a LazyRow inside a LazyColumn, and the vertical list disposes rows that
 *    leave the viewport — so without a saved state, scrolling a rail sideways,
 *    scrolling the page down and back, and finding the rail reset to the start
 *    is not a bug in the rail: it is the outer list doing its job. Retaining it
 *    is what makes the page feel like it kept your place.
 *
 * 2. **`contentType` is declared.** Every item is the same kind of card, so
 *    Compose can reuse the composition and the layout node instead of building
 *    a fresh subtree per item. This is the difference between a rail that
 *    glides on a mid-range phone and one that hitches while scrolling.
 *
 * Nesting a LazyRow in a LazyColumn is supported precisely because the scroll
 * axes differ; the failure mode is state and reuse, not gesture conflict.
 */
@Composable
fun ProductRail(title: String, products: List<Product>, actionLabel: String? = null, onAction: (() -> Unit)? = null) {
    if (products.isEmpty()) return
    SectionHeader(title, actionLabel, onAction)
    val railState = rememberSaveable(title, saver = LazyListState.Saver) { LazyListState() }
    LazyRow(
        state = railState,
        contentPadding = PaddingValues(horizontal = TazSpace.gutter),
        horizontalArrangement = Arrangement.spacedBy(TazSpace.md)
    ) {
        items(
            products,
            key = { it.id },
            contentType = { "productCard" }
        ) { p -> ProductCard(p) }
    }
}

// ---------------------------------------------------------------------------
// Floating "view cart" bar
// ---------------------------------------------------------------------------

/**
 * @param aboveNav true on the tabbed shell, where a bottom navigation bar owns
 *   the bottom edge. Found during E1 verification: as an overlay aligned to
 *   the bottom, this bar drew EXACTLY over the nav bar, so the moment a
 *   customer added one item, Home / Categories / Order Again / Account became
 *   unreachable until the cart was emptied. With `aboveNav` the bar floats
 *   above the nav instead; content is still not displaced (no layout jump),
 *   and scrolling surfaces keep using [TazSpace.cartBarClearance].
 */
@Composable
fun BoxScope.CartBar(aboveNav: Boolean = false) {
    val app = LocalAppState.current
    val count = app.cartItemCount
    AnimatedVisibility(
        visible = count > 0,
        modifier = Modifier.align(Alignment.BottomCenter),
        enter = slideInVertically(tween(TazMotion.normal)) { it } + fadeIn(tween(TazMotion.fast)),
        exit = slideOutVertically(tween(TazMotion.fast)) { it } + fadeOut(tween(TazMotion.fast))
    ) {
        Row(
            Modifier
                // Clear the nav bar's full height (bar + its hairline). The nav
                // applies the system inset itself, so it is applied here once.
                .padding(bottom = if (aboveNav) TazSize.navBarHeight + 1.dp else 0.dp)
                .padding(horizontal = TazSpace.gutter, vertical = TazSpace.md)
                .navigationBarsPadding()
                .fillMaxWidth()
                .shadow(10.dp, RoundedCornerShape(16.dp), spotColor = Color.Black.copy(alpha = 0.32f))
                .clip(RoundedCornerShape(16.dp))
                .background(TazColors.Green)
                .tazPressable(onClick = { app.navigate(Screen.Cart) }, pressScale = TazPress.compact)
                .height(64.dp)
                .padding(horizontal = TazSpace.lg),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                Modifier.size(TazSize.buttonHeightSm).clip(CircleShape)
                    .background(TazColors.White.copy(alpha = 0.18f)),
                contentAlignment = Alignment.Center
            ) {
                TazIcon(TazIcons.Cart, null, size = TazSize.iconMd, tint = TazColors.White)
            }
            Spacer(Modifier.width(TazSpace.md))
            Column(Modifier.weight(1f)) {
                val bill = app.bill(app.cartLines())
                Text(
                    "$count item${if (count > 1) "s" else ""} · ₹${bill.itemTotal}",
                    color = TazColors.White, fontSize = TazType.titleSize,
                    fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis
                )
                val gap = AppConfig.charges.freeDeliveryAboveRupees - bill.itemTotal
                Text(
                    if (gap > 0) "Add ₹$gap more for free delivery"
                    else "Free delivery unlocked",
                    color = TazColors.White.copy(alpha = 0.8f), fontSize = TazType.microSize,
                    fontWeight = TazType.microWeight, maxLines = 1, overflow = TextOverflow.Ellipsis
                )
            }
            Spacer(Modifier.width(TazSpace.sm))
            Text(
                "View cart", color = TazColors.White, fontSize = TazType.buttonSize,
                fontWeight = TazType.buttonWeight, maxLines = 1
            )
            Spacer(Modifier.width(TazSpace.xs))
            TazIcon(TazIcons.Forward, null, size = TazSize.iconSm, tint = TazColors.White)
        }
    }
}


// ---------------------------------------------------------------------------
// v2: photography tiles, hero banner, guided-target anchors
// ---------------------------------------------------------------------------

/**
 * One category tile: the bundled photograph plus the minimum display data a
 * decorative surface needs to draw it.
 *
 * This is a DESIGN ASSET table, not catalogue data. It exists so the pre-auth
 * brand wall on the login screen can render instantly with no network call and
 * no failure mode — a login screen that waits on the catalogue service, or
 * empties out when it is down, is a worse login screen. Everything that sells
 * a product (names, prices, stock, taxonomy) still comes from
 * CatalogRepository; nothing here is shown as merchandise.
 *
 * The photographs are openly-licensed Wikimedia Commons placeholders
 * (docs/IMAGE_ATTRIBUTIONS.md) and are due for replacement before launch.
 *
 * CategoryArtTilesTest asserts this table stays aligned with the catalogue
 * taxonomy, so the two cannot silently drift apart.
 */
data class CategoryArtTile(
    val id: String,
    val label: String,
    val tint: Long,
    val art: DrawableResource
)

val categoryArtTiles: List<CategoryArtTile> = listOf(
    CategoryArtTile("fruits", "Vegetables & Fruits", 0xFFE8F5E9, Res.drawable.cat_fruits),
    CategoryArtTile("dairy", "Dairy, Bread & Eggs", 0xFFFFF8E1, Res.drawable.cat_dairy),
    CategoryArtTile("atta", "Atta, Rice & Dal", 0xFFFFF3E0, Res.drawable.cat_atta),
    CategoryArtTile("oil", "Oil, Masala & Dry Fruits", 0xFFFFFDE7, Res.drawable.cat_oil),
    CategoryArtTile("meat", "Chicken, Meat & Fish", 0xFFFFEBEE, Res.drawable.cat_meat),
    CategoryArtTile("munchies", "Munchies & Snacks", 0xFFFFF3E0, Res.drawable.cat_munchies),
    CategoryArtTile("drinks", "Cold Drinks & Juices", 0xFFE3F2FD, Res.drawable.cat_drinks),
    CategoryArtTile("tea", "Tea, Coffee & More", 0xFFEFEBE9, Res.drawable.cat_tea),
    CategoryArtTile("instant", "Instant & Frozen Food", 0xFFFCE4EC, Res.drawable.cat_instant),
    CategoryArtTile("sweet", "Sweet Tooth", 0xFFF3E5F5, Res.drawable.cat_sweet),
    CategoryArtTile("bakery", "Bakery & Biscuits", 0xFFFFF8E1, Res.drawable.cat_bakery),
    CategoryArtTile("personal", "Bath & Body", 0xFFE0F7FA, Res.drawable.cat_personal),
    CategoryArtTile("skincare", "Skin & Face Care", 0xFFFCE4EC, Res.drawable.cat_skincare),
    CategoryArtTile("pharma", "Pharma & Wellness", 0xFFE8F5E9, Res.drawable.cat_pharma),
    CategoryArtTile("baby", "Baby Care", 0xFFE3F2FD, Res.drawable.cat_baby),
    CategoryArtTile("cleaning", "Cleaning Essentials", 0xFFE8F5E9, Res.drawable.cat_cleaning),
    CategoryArtTile("home", "Home & Office", 0xFFFFF3E0, Res.drawable.cat_home),
    CategoryArtTile("pet", "Pet Care", 0xFFEFEBE9, Res.drawable.cat_pet),
    CategoryArtTile("paan", "Paan Corner", 0xFFE8F5E9, Res.drawable.cat_paan)
)

private val categoryArt: Map<String, DrawableResource> =
    categoryArtTiles.associate { it.id to it.art }


fun categoryArtFor(categoryId: String): DrawableResource? = categoryArt[categoryId]

/** Registers this element's on-screen bounds so the guided journey can spotlight it. */
fun Modifier.guidedTarget(key: String): Modifier = composed {
    val app = LocalAppState.current
    this.onGloballyPositioned { app.guidedTargets[key] = it.boundsInRoot() }
}

/** Photographic category tile with soft shadow — the v2 look. */
@Composable
fun CategoryTile(
    category: Category,
    size: Dp = TazSize.categoryTile,
    onClick: () -> Unit
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier.width(size + TazSpace.sm)
            // Tile press response. The label sits outside the artwork, so the
            // whole column scales together and the tile never looks detached
            // from its caption.
            .tazPressable(onClick = onClick, pressScale = TazPress.card)
    ) {
        Box(
            Modifier.size(size)
                .shadow(3.dp, TazRadius.tile, spotColor = Color.Black.copy(alpha = 0.22f))
                .clip(TazRadius.tile)
                .background(Color(category.tint))
        ) {
            val art = categoryArtFor(category.id)
            if (art != null) {
                Image(
                    painter = painterResource(art),
                    contentDescription = category.name,
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Crop
                )
            } else {
                // category.emoji is CONTENT — the fallback when art is missing.
                Text(category.emoji, fontSize = (size.value * 0.42f).sp,
                    modifier = Modifier.align(Alignment.Center))
            }
            Box(
                Modifier.fillMaxSize().background(
                    Brush.verticalGradient(
                        0.72f to Color.Transparent,
                        1f to Color.Black.copy(alpha = 0.12f)
                    )
                )
            )
        }
        Spacer(Modifier.height(TazSpace.xs))
        Text(
            category.name, fontSize = TazType.navLabelSize, fontWeight = FontWeight.SemiBold,
            color = TazColors.TextPrimary, textAlign = TextAlign.Center,
            maxLines = 2, overflow = TextOverflow.Ellipsis, lineHeight = TazType.microLine,
            modifier = Modifier.heightIn(min = 28.dp)
        )
    }
}

/** Marquee chip with a round photo — onboarding v2. */
@Composable
fun PhotoChip(category: Category) {
    Row(
        Modifier
            .shadow(3.dp, TazRadius.pill, spotColor = Color.Black.copy(alpha = 0.22f))
            .clip(TazRadius.pill)
            .background(TazColors.Surface)
            .border(BorderStroke(1.dp, TazColors.CardBorder), TazRadius.pill)
            .padding(start = TazSpace.xs + TazSpace.xxs, end = TazSpace.md, top = TazSpace.xs + TazSpace.xxs, bottom = TazSpace.xs + TazSpace.xxs),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(Modifier.size(TazSpace.xxxl).clip(CircleShape).background(Color(category.tint))) {
            val art = categoryArtFor(category.id)
            if (art != null) {
                Image(painterResource(art), category.name, Modifier.fillMaxSize(),
                    contentScale = ContentScale.Crop)
            } else {
                Text(category.emoji, fontSize = TazType.titleSize, modifier = Modifier.align(Alignment.Center))
            }
        }
        Spacer(Modifier.width(TazSpace.sm))
        Text(
            category.name, fontSize = TazType.productNameSize, fontWeight = FontWeight.SemiBold,
            color = TazColors.TextPrimary, maxLines = 1, overflow = TextOverflow.Ellipsis
        )
    }
}

// ---------------------------------------------------------------------------
// Promo banners — one height, one radius, one elevation, so the carousel
// never resizes between slides.
// ---------------------------------------------------------------------------

private val BannerHeight: Dp = 150.dp
private val BannerElevation: Dp = 6.dp

@Composable
private fun BannerChip(
    text: String,
    textColor: Color,
    bg: Color,
    icon: ImageVector? = null,
    modifier: Modifier = Modifier
) {
    Row(
        modifier.clip(TazRadius.pill).background(bg)
            .padding(horizontal = TazSpace.md, vertical = TazSpace.xs + TazSpace.xxs),
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (icon != null) {
            TazIcon(icon, null, size = TazSize.iconXs, tint = textColor)
            Spacer(Modifier.width(TazSpace.xs))
        }
        Text(
            text, color = textColor, fontSize = TazType.microSize,
            fontWeight = TazType.microWeight, maxLines = 1, overflow = TextOverflow.Ellipsis
        )
    }
}

/** Hero banner with the real Tazzzo basket photo from the flyer. */
@Composable
fun HeroBasketBanner(modifier: Modifier = Modifier) {
    Row(
        modifier
            .fillMaxWidth()
            .height(BannerHeight)
            .shadow(BannerElevation, TazRadius.tile, spotColor = Color.Black.copy(alpha = 0.26f))
            .clip(TazRadius.tile)
            .background(Brush.horizontalGradient(listOf(TazColors.Surface, TazColors.OrangeSoft))),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(
            Modifier.weight(1f).padding(
                start = TazSpace.lg, end = TazSpace.sm,
                top = TazSpace.md, bottom = TazSpace.md
            )
        ) {
            // Claim + sub are config-sourced (BrandCopy, decision D6) — never hard-coded.
            Text(
                BrandCopy.savingsClaim ?: BrandCopy.promise,
                fontSize = TazType.h1Size, fontWeight = TazType.h1Weight,
                lineHeight = TazType.h1Line, color = TazColors.Orange,
                maxLines = 2, overflow = TextOverflow.Ellipsis
            )
            Spacer(Modifier.height(TazSpace.xxs))
            Text(
                if (BrandCopy.savingsClaim != null) BrandCopy.savingsClaimSub else "",
                fontSize = TazType.captionSize, lineHeight = TazType.captionLine,
                color = TazColors.TextSecondary,
                maxLines = 2, overflow = TextOverflow.Ellipsis
            )
            Spacer(Modifier.height(TazSpace.sm))
            BannerChip(
                DeliveryCopy.headline(AppConfig.deliveryPromise),
                TazColors.White, TazColors.GreenDark, TazIcons.Delivery
            )
        }
        Image(
            painter = painterResource(Res.drawable.hero_basket),
            contentDescription = "Tazzzo basket",
            modifier = Modifier.fillMaxHeight().padding(TazSpace.sm),
            contentScale = ContentScale.Fit
        )
    }
}


// ---------------------------------------------------------------------------
// v3: premium promo banners (photography + motion)
// ---------------------------------------------------------------------------

/** Delivery promise — cinematic dark-green banner with animated speed lines. */
@Composable
fun DeliveryPromoBanner(modifier: Modifier = Modifier) {
    val anim = rememberInfiniteTransition()
    val dash by anim.animateFloat(
        0f, 1f, infiniteRepeatable(tween(900, easing = LinearEasing))
    )
    val bob by anim.animateFloat(
        0f, 1f, infiniteRepeatable(tween(450), RepeatMode.Reverse)
    )
    Box(
        modifier.fillMaxWidth().height(BannerHeight)
            .shadow(BannerElevation, TazRadius.tile, spotColor = Color.Black.copy(alpha = 0.26f))
            .clip(TazRadius.tile)
            .background(Brush.horizontalGradient(listOf(TazColors.GreenDark, TazColors.GreenMid)))
            .drawBehind {
                // decorative translucent circles (depth texture)
                drawCircle(Color.White.copy(alpha = 0.05f), radius = size.height * 0.95f,
                    center = Offset(size.width * 0.92f, size.height * 0.05f))
                drawCircle(Color.White.copy(alpha = 0.04f), radius = size.height * 0.65f,
                    center = Offset(size.width * 0.78f, size.height * 0.95f))
            }
    ) {
        Column(
            Modifier.align(Alignment.CenterStart)
                .padding(start = TazSpace.lg, end = 128.dp)
        ) {
            Text(
                DeliveryCopy.headline(AppConfig.deliveryPromise),
                fontSize = TazType.h2Size, fontWeight = TazType.h2Weight,
                lineHeight = TazType.h2Line, color = TazColors.White,
                maxLines = 2, overflow = TextOverflow.Ellipsis
            )
            Spacer(Modifier.height(TazSpace.xxs))
            Text(
                DeliveryCopy.subtitle(AppConfig.deliveryPromise),
                fontSize = TazType.captionSize, lineHeight = TazType.captionLine,
                color = TazColors.White.copy(alpha = 0.75f),
                maxLines = 2, overflow = TextOverflow.Ellipsis
            )
            DeliveryCopy.short(AppConfig.deliveryPromise)?.let {
                Spacer(Modifier.height(TazSpace.sm))
                BannerChip(it, TazColors.GreenDark, TazColors.CoinGold, TazIcons.Delivery)
            }
        }
        // delivery mark with animated speed lines
        Box(Modifier.align(Alignment.CenterEnd).padding(end = TazSpace.md)) {
            Column(
                Modifier.align(Alignment.CenterStart).padding(end = 68.dp),
                verticalArrangement = Arrangement.spacedBy(TazSpace.xs + 3.dp)
            ) {
                repeat(3) { i ->
                    val phase = ((dash + i * 0.33f) % 1f)
                    Box(
                        Modifier
                            .width((26 + i * 10).dp)
                            .height(3.dp)
                            .clip(TazRadius.pill)
                            .background(Color.White.copy(alpha = 0.55f * (1f - phase)))
                    )
                }
            }
            Box(
                Modifier.padding(start = TazSpace.huge - TazSpace.sm)
                    .offset(y = (bob * (-3)).dp)
                    .size(TazSpace.huge + TazSpace.md)
                    .clip(CircleShape)
                    .background(Color.White.copy(alpha = 0.14f)),
                contentAlignment = Alignment.Center
            ) {
                TazIcon(TazIcons.Delivery, null, size = TazSize.iconLg, tint = TazColors.White)
            }
        }
    }
}

/** "Earn Tazzzo Coins" — real gold-coin photography under a dark scrim. */
@Composable
fun CoinsPromoBanner(modifier: Modifier = Modifier) {
    Box(
        modifier.fillMaxWidth().height(BannerHeight)
            .shadow(BannerElevation, TazRadius.tile, spotColor = Color.Black.copy(alpha = 0.26f))
            .clip(TazRadius.tile)
            .background(TazColors.GreenDark)
    ) {
        Image(
            painter = painterResource(Res.drawable.banner_coins),
            contentDescription = "Tazzzo Coins",
            modifier = Modifier.fillMaxSize(),
            contentScale = ContentScale.Crop,
            alignment = Alignment.CenterEnd
        )
        // scrim: readable text left, photo showing right
        Box(
            Modifier.fillMaxSize().background(
                Brush.horizontalGradient(
                    0f to TazColors.GreenDark,
                    0.55f to TazColors.GreenDark.copy(alpha = 0.92f),
                    1f to TazColors.GreenDark.copy(alpha = 0.15f)
                )
            )
        )
        Column(
            Modifier.align(Alignment.CenterStart)
                .padding(start = TazSpace.lg, end = 118.dp)
        ) {
            Text(
                "Earn Tazzzo Coins", fontSize = TazType.h2Size, fontWeight = TazType.h2Weight,
                lineHeight = TazType.h2Line, color = TazColors.CoinGold,
                maxLines = 1, overflow = TextOverflow.Ellipsis
            )
            Spacer(Modifier.height(TazSpace.xxs))
            Text(
                AppConfig.coins.earnCopy, fontSize = TazType.captionSize,
                lineHeight = TazType.captionLine, color = TazColors.White.copy(alpha = 0.8f),
                maxLines = 2, overflow = TextOverflow.Ellipsis
            )
            Spacer(Modifier.height(TazSpace.sm))
            BannerChip(
                "${AppConfig.coins.valueCopy} at checkout",
                TazColors.GreenDark, TazColors.White, TazIcons.Coin
            )
        }
    }
}

/** Voice commerce strip v3 — pulsing mic + live equalizer bars. */
@Composable
fun VoiceCommerceBannerV3() {
    val app = LocalAppState.current
    // Six perpetual animations on a banner that sits on Home for the whole
    // session. Gated: with ambient motion off they hold a resting frame.
    val ambient = MotionSettings.ambientEnabled
    val pulse: Float
    val eq1: Float; val eq2: Float; val eq3: Float; val eq4: Float; val eq5: Float
    if (ambient) {
        val anim = rememberInfiniteTransition()
        pulse = anim.animateFloat(0.85f, 1.15f, infiniteRepeatable(tween(700), RepeatMode.Reverse)).value
        eq1 = anim.animateFloat(0.35f, 1f, infiniteRepeatable(tween(380), RepeatMode.Reverse)).value
        eq2 = anim.animateFloat(1f, 0.3f, infiniteRepeatable(tween(300), RepeatMode.Reverse)).value
        eq3 = anim.animateFloat(0.5f, 0.95f, infiniteRepeatable(tween(460), RepeatMode.Reverse)).value
        eq4 = anim.animateFloat(0.9f, 0.4f, infiniteRepeatable(tween(340), RepeatMode.Reverse)).value
        eq5 = anim.animateFloat(0.4f, 0.8f, infiniteRepeatable(tween(420), RepeatMode.Reverse)).value
    } else {
        pulse = 1f; eq1 = 0.6f; eq2 = 0.75f; eq3 = 0.5f; eq4 = 0.8f; eq5 = 0.55f
    }

    Row(
        Modifier.fillMaxWidth()
            .height(BannerHeight)
            .shadow(BannerElevation, TazRadius.tile, spotColor = Color.Black.copy(alpha = 0.30f))
            .clip(TazRadius.tile)
            .background(Brush.linearGradient(listOf(TazColors.GreenDark, Color(0xFF062313))))
            .tazPressable(onClick = { app.showVoiceSheet = true }, pressScale = TazPress.compact)
            .padding(horizontal = TazSpace.lg, vertical = TazSpace.lg),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // pulsing mic badge
        Box(contentAlignment = Alignment.Center) {
            Box(
                Modifier.size(TazSpace.huge + TazSpace.xs)
                    .scale(pulse)
                    .clip(CircleShape)
                    .background(TazColors.Leaf.copy(alpha = 0.25f))
            )
            Box(
                Modifier.size(TazSize.buttonHeightSm).clip(CircleShape).background(TazColors.White),
                contentAlignment = Alignment.Center
            ) { TazIcon(TazIcons.Mic, null, size = TazSize.iconSm, tint = TazColors.GreenDark) }
        }
        Spacer(Modifier.width(TazSpace.md))
        Column(Modifier.weight(1f)) {
            Text(
                BrandCopy.voiceTeaser, fontSize = TazType.titleSize,
                fontWeight = FontWeight.ExtraBold, lineHeight = TazType.titleLine,
                color = TazColors.White, maxLines = 2, overflow = TextOverflow.Ellipsis
            )
            Spacer(Modifier.height(TazSpace.xxs))
            Text(
                BrandCopy.voiceSub, fontSize = TazType.captionSize,
                lineHeight = TazType.captionLine, color = TazColors.White.copy(alpha = 0.72f),
                maxLines = 2, overflow = TextOverflow.Ellipsis
            )
            Spacer(Modifier.height(TazSpace.xxs))
            Text(
                BrandCopy.voiceStatus.uppercase(), fontSize = TazType.microSize,
                fontWeight = TazType.microWeight, letterSpacing = TazType.labelTracking,
                color = TazColors.CoinGold, maxLines = 1, overflow = TextOverflow.Ellipsis
            )
            Spacer(Modifier.height(TazSpace.sm))
            BannerChip("Learn more", TazColors.GreenDark, TazColors.White)
        }
        Spacer(Modifier.width(TazSpace.sm))
        // live equalizer
        Row(
            verticalAlignment = Alignment.Bottom,
            horizontalArrangement = Arrangement.spacedBy(3.dp),
            modifier = Modifier.height(TazSpace.xxxl)
        ) {
            listOf(eq1, eq2, eq3, eq4, eq5).forEachIndexed { i, f ->
                Box(
                    Modifier.width(TazSpace.xs)
                        .height((32 * f).dp)
                        .clip(TazRadius.pill)
                        .background(if (i % 2 == 0) TazColors.CoinGold else TazColors.Leaf)
                )
            }
        }
    }
}
