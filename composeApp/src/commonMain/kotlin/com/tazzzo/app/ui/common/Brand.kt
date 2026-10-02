package com.tazzzo.app.ui.common

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowForward
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.tazzzo.app.theme.TazColors
import com.tazzzo.app.theme.TazRadius
import com.tazzzo.app.theme.TazSize
import com.tazzzo.app.theme.TazSpace
import com.tazzzo.app.theme.TazType
import com.tazzzo.app.theme.tazEditorialFamily
import com.tazzzo.app.ui.interaction.TazPress
import com.tazzzo.app.ui.interaction.tazPressable
import org.jetbrains.compose.resources.DrawableResource
import org.jetbrains.compose.resources.painterResource
import tazzzo.resources.Res
import tazzzo.resources.tazzzo_wordmark

/*
 * Brand primitives for the UI Page reference screens (2026-10): the vector wordmark, the editorial (Newsreader)
 * headline, full-bleed photo slots, the editorial pill button, pager dots and the small tertiary text action.
 * Nothing here knows about commerce state.
 */

/** The wordmark's intrinsic aspect ratio (design/brand/tazzzo_wordmark.svg: 316 × 70.4). */
const val WORDMARK_ASPECT = 316f / 70.4f

/** The Tazzzo wordmark: outlined vector paths, tinted at runtime. Sized by [width]; the height follows the aspect. */
@Composable
fun TazzzoWordmark(width: Dp, modifier: Modifier = Modifier, tint: Color = TazColors.BrandEditorial) {
    Image(
        painter = painterResource(Res.drawable.tazzzo_wordmark),
        contentDescription = "Tazzzo",
        colorFilter = ColorFilter.tint(tint),
        contentScale = ContentScale.Fit,
        modifier = modifier.width(width).aspectRatio(WORDMARK_ASPECT)
    )
}

/** One run of an editorial headline. [italic] runs are the emphasis voice ("Wholesale Prices, *Delivered.*"). */
data class EditorialRun(val text: String, val italic: Boolean = false)

fun plain(text: String) = EditorialRun(text)
fun italic(text: String) = EditorialRun(text, italic = true)

fun editorialString(runs: List<EditorialRun>): AnnotatedString = buildAnnotatedString {
    runs.forEach { r ->
        if (r.italic) withStyle(SpanStyle(fontStyle = FontStyle.Italic)) { append(r.text) } else append(r.text)
    }
}

/**
 * Newsreader display text. Regular weight only (the family ships Regular + Italic); hierarchy comes from size,
 * never from faux bold.
 */
@Composable
fun EditorialText(
    runs: List<EditorialRun>,
    size: TextUnit,
    lineHeight: TextUnit,
    color: Color,
    modifier: Modifier = Modifier,
    textAlign: TextAlign = TextAlign.Center,
    letterSpacing: TextUnit = 0.sp
) {
    Text(
        editorialString(runs),
        fontFamily = tazEditorialFamily(),
        fontWeight = FontWeight.Normal,
        fontSize = size,
        lineHeight = lineHeight,
        letterSpacing = letterSpacing,
        color = color,
        textAlign = textAlign,
        modifier = modifier
    )
}

/** Where a photo slot keeps its subject when the viewport is taller or shorter than the plate. */
enum class PhotoAnchor { Center, Bottom }

/**
 * A full-bleed photographic slot. With [photo] it crops the plate ([ContentScale.Crop]) at [anchor]; with null it
 * draws [placeholder] — the DEVELOPMENT stand-in while the clean plate (docs/design/UI_ASSET_MANIFEST.md) is
 * outstanding. The slot, crop, [scrim] and content-safe area are identical either way, so a real plate is a
 * one-resource swap. Decorative: no accessibility output.
 */
@Composable
fun PhotoBackdrop(
    photo: DrawableResource?,
    placeholder: Brush,
    modifier: Modifier = Modifier,
    anchor: PhotoAnchor = PhotoAnchor.Center,
    scrim: Brush? = null
) {
    Box(modifier.clearAndSetSemantics { }) {
        if (photo != null) {
            Image(
                painter = painterResource(photo),
                contentDescription = null,
                contentScale = ContentScale.Crop,
                alignment = if (anchor == PhotoAnchor.Bottom) Alignment.BottomCenter else Alignment.Center,
                modifier = Modifier.matchParentSize()
            )
        } else {
            Box(Modifier.matchParentSize().background(placeholder))
        }
        if (scrim != null) Box(Modifier.matchParentSize().background(scrim))
    }
}

/** Development placeholders: brand-tinted gradients that keep the slot honest until the plate arrives. */
object PhotoPlaceholders {
    /** Deep editorial green wall (Showcase). */
    val greenWall: Brush = Brush.verticalGradient(0f to Color(0xFF1C4634), 0.55f to TazColors.BrandEditorial, 1f to TazColors.BrandEditorialDeep)
    /** Warm cream plaster (Login / OTP). */
    val creamWall: Brush = Brush.verticalGradient(0f to Color(0xFFF7F2EA), 0.6f to Color(0xFFEFE5D9), 1f to Color(0xFFE6DACB))
    /** Bright studio white (Splash). */
    val studio: Brush = Brush.verticalGradient(0f to Color(0xFFFCFCFA), 0.5f to TazColors.Cream, 1f to Color(0xFFF1EFE8))
}

enum class ButtonTone { Editorial, Cream }

/**
 * The editorial pill CTA: 54dp, fully rounded, serif label, optional trailing arrow. [ButtonTone.Editorial] is the
 * deep green ("Continue"); [ButtonTone.Cream] sits on a dark photo ("Next"). Not a replacement for [PillButton] in
 * commerce flows.
 */
@Composable
fun TazzzoPrimaryButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    tone: ButtonTone = ButtonTone.Editorial,
    trailingArrow: Boolean = true,
    enabled: Boolean = true,
    loading: Boolean = false,
    italic: Boolean = false
) {
    val bg = if (tone == ButtonTone.Editorial) TazColors.BrandEditorial else TazColors.CreamStrong
    val fg = if (tone == ButtonTone.Editorial) TazColors.EditorialOnDark else TazColors.BrandEditorial
    val interactive = enabled && !loading
    // One structurally constant modifier chain whatever the state: the dimming is a graphicsLayer that is ALWAYS present
    // (alpha 1 or 0.5) and the fill is drawn inside it. A chain whose modifiers appear/disappear with `enabled`
    // produced an unfilled button on the Login screen when the field became valid under the keyboard.
    Box(
        modifier
            .height(EDITORIAL_BUTTON_HEIGHT)
            .graphicsLayer { alpha = if (enabled) 1f else DISABLED_ALPHA }
            .clip(TazRadius.pill)
            .background(bg)
            .tazPressable(onClick = onClick, enabled = interactive, pressScale = TazPress.control)
            .semantics { contentDescription = text }
            .padding(horizontal = TazSpace.xxl),
        contentAlignment = Alignment.Center
    ) {
        if (loading) {
            CircularProgressIndicator(Modifier.size(22.dp), color = fg, strokeWidth = 2.dp)
        } else {
            EditorialText(listOf(EditorialRun(text, italic)), size = 19.sp, lineHeight = 22.sp, color = fg)
            if (trailingArrow) {
                Icon(
                    Icons.AutoMirrored.Rounded.ArrowForward, contentDescription = null, tint = fg,
                    modifier = Modifier.align(Alignment.CenterEnd).size(TazSize.iconMd)
                )
            }
        }
    }
}

val EDITORIAL_BUTTON_HEIGHT: Dp = 54.dp

/** The reference shows the CTA solid even before input; disabled stays visibly green, only slightly quieter. */
private const val DISABLED_ALPHA = 0.72f

/** Pagination dots: the active one is brighter and larger. */
@Composable
fun PagerDots(count: Int, index: Int, modifier: Modifier = Modifier, activeColor: Color = TazColors.CreamStrong, inactiveColor: Color = TazColors.CreamStrong.copy(alpha = 0.45f)) {
    Row(modifier.semantics { contentDescription = "Page ${index + 1} of $count" }, horizontalArrangement = Arrangement.spacedBy(TazSpace.sm), verticalAlignment = Alignment.CenterVertically) {
        repeat(count) { i ->
            val active = i == index
            Box(Modifier.size(if (active) 9.dp else 7.dp).clip(CircleShape).background(if (active) activeColor else inactiveColor))
        }
    }
}

/** A small tertiary text action ("Skip", "Skip for now", "Change number") with a full 44dp touch target. */
@Composable
fun TextAction(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    color: Color = TazColors.TextSecondary,
    serif: Boolean = false,
    underline: Boolean = false,
    enabled: Boolean = true
) {
    Box(
        modifier
            .defaultMinSize(minHeight = TazSize.touchTarget, minWidth = TazSize.touchTarget)
            .clip(TazRadius.chip)
            .tazPressable(onClick = onClick, enabled = enabled, pressScale = TazPress.compact)
            .padding(horizontal = TazSpace.md),
        contentAlignment = Alignment.Center
    ) {
        if (serif) {
            Text(
                text, fontFamily = tazEditorialFamily(), fontStyle = FontStyle.Italic, fontSize = 16.sp, color = color,
                textDecoration = if (underline) TextDecoration.Underline else TextDecoration.None
            )
        } else {
            Text(
                text, fontSize = TazType.captionSize, fontWeight = FontWeight.Medium, color = color,
                textDecoration = if (underline) TextDecoration.Underline else TextDecoration.None
            )
        }
    }
}

/** Vertical breathing room helper used by the brand screens. */
@Composable
fun VSpace(height: Dp) = Spacer(Modifier.height(height))
