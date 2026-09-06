package com.tazzzo.app.theme

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * Design tokens — the single source of truth for every measurement in the app.
 *
 * Rule: screens must not hard-code sp/dp values. They compose from these scales.
 * Before this file existed the app carried 25 distinct font sizes and 46 distinct
 * spacing values; that is what makes 20 screens look like 20 products.
 */

// ---------------------------------------------------------------------------
// Typography
// ---------------------------------------------------------------------------

/**
 * Font family token. The brand reference specifies Poppins; v1 ships on the
 * platform font (no bundle cost, no metric risk). To adopt Poppins later,
 * change ONLY this value — every text style inherits it.
 */
object TazType {
    /** Deprecated: the family now comes from TazzzoTheme (Poppins). */
    val brandFamily: FontFamily = FontFamily.Default

    // Scale — 7 steps. Each role has a fixed size AND weight so usage is unambiguous.
    val displaySize: TextUnit = 30.sp;  val displayWeight = FontWeight.ExtraBold
    val h1Size: TextUnit      = 24.sp;  val h1Weight      = FontWeight.ExtraBold
    val h2Size: TextUnit      = 19.sp;  val h2Weight      = FontWeight.Bold
    val titleSize: TextUnit   = 16.sp;  val titleWeight   = FontWeight.SemiBold
    val bodySize: TextUnit    = 14.sp;  val bodyWeight    = FontWeight.Normal
    val captionSize: TextUnit = 12.sp;  val captionWeight = FontWeight.Normal
    val microSize: TextUnit   = 10.sp;  val microWeight   = FontWeight.SemiBold

    // Line heights — paired with the sizes above.
    val displayLine: TextUnit = 36.sp
    val h1Line: TextUnit      = 30.sp
    val h2Line: TextUnit      = 24.sp
    val titleLine: TextUnit   = 21.sp
    val bodyLine: TextUnit    = 20.sp
    val captionLine: TextUnit = 16.sp
    val microLine: TextUnit   = 13.sp

    /** Letter spacing for uppercase eyebrow/label text. */
    val labelTracking: TextUnit = 0.8.sp

    // ---- commerce-specific ------------------------------------------------
    // Prices and product names are the two things a grocery customer scans
    // fastest, so they get dedicated tokens rather than borrowing heading sizes.
    val priceHeroSize: TextUnit = 22.sp     // PDP
    val priceSize: TextUnit = 15.sp         // cards, cart rows
    val priceWeight = FontWeight.Bold
    val mrpSize: TextUnit = 12.sp           // struck-through original
    val savingsSize: TextUnit = 12.sp
    val savingsWeight = FontWeight.SemiBold

    val productNameSize: TextUnit = 13.sp   // card
    val productNameWeight = FontWeight.Medium
    val productNameLine: TextUnit = 17.sp
    val unitSize: TextUnit = 12.sp          // "500 g"

    val buttonSize: TextUnit = 15.sp
    val buttonWeight = FontWeight.SemiBold
    val navLabelSize: TextUnit = 11.sp

    /** The number inside a quantity stepper. Heavier than body at the same size
     *  so the count reads at a glance while the − and + stay quiet. */
    val stepperCountSize: TextUnit = 14.sp
    val stepperCountWeight = FontWeight.Bold
}

// ---------------------------------------------------------------------------
// Spacing — a 4dp base scale. Never invent a value between these.
// ---------------------------------------------------------------------------

object TazSpace {
    val xxs: Dp = 2.dp
    val xs: Dp  = 4.dp
    val sm: Dp  = 8.dp
    val md: Dp  = 12.dp
    val lg: Dp  = 16.dp
    val xl: Dp  = 20.dp
    val xxl: Dp = 24.dp
    val xxxl: Dp = 32.dp
    val huge: Dp = 48.dp

    /** Standard horizontal page gutter. */
    val gutter: Dp = 16.dp

    /**
     * Tighter page gutter for DENSE merchandising surfaces — the 4-up category
     * grid, the 4-up deals grid, the 2-up product grid.
     *
     * 12dp, not [gutter]. At four columns a 16dp gutter costs 8dp of cell width
     * on each side, which is the difference between a product name wrapping to
     * two lines and fitting on one. Reserved for grids; text screens keep 16dp
     * so body copy never runs to the screen edge.
     */
    val screenEdge: Dp = 12.dp

    /** Bottom padding on scrollable content so the floating cart bar never covers it. */
    val cartBarClearance: Dp = 96.dp

    /** Screen content padding used by most simple screens. */
    val screen = PaddingValues(horizontal = 16.dp, vertical = 12.dp)
}

// ---------------------------------------------------------------------------
// Radius
// ---------------------------------------------------------------------------

object TazRadius {
    val chip = RoundedCornerShape(8.dp)
    val card = RoundedCornerShape(14.dp)
    val tile = RoundedCornerShape(18.dp)
    /** Bottom-sheet: top corners only (it is anchored to the screen edge). */
    val sheet = RoundedCornerShape(topStart = 22.dp, topEnd = 22.dp)
    /** Same radius, all four corners — for FLOATING panels (coach-mark card,
     *  dialogs) where `sheet` would leave square bottoms. */
    val sheetAll = RoundedCornerShape(22.dp)
    val pill = RoundedCornerShape(999.dp)

    val chipDp: Dp = 8.dp
    val cardDp: Dp = 14.dp
    val tileDp: Dp = 18.dp
}

// ---------------------------------------------------------------------------
// Elevation — three levels only. Restrained, per the brand direction.
// ---------------------------------------------------------------------------

object TazElevation {
    val flat: Dp = 0.dp
    val raised: Dp = 3.dp     // cards at rest
    val floating: Dp = 8.dp   // banners, cart bar
    val overlay: Dp = 14.dp   // sheets, dialogs
}

// ---------------------------------------------------------------------------
// Motion — two durations, honoured everywhere.
// ---------------------------------------------------------------------------

object TazMotion {
    const val fast = 150      // taps, state flips
    const val normal = 300    // transitions, crossfades
    const val ambient = 700   // pulses, marquees

    /**
     * Screen-to-screen navigation.
     *
     * 240ms, not [normal]. A full-screen slide reads as slower than a fade of
     * the same duration because the eye tracks the moving edge, so matching the
     * crossfade's 300ms would have made navigation feel heavier than the screen
     * it replaced. Long enough to establish direction, short enough that a
     * customer tapping through four aisles never waits on the app.
     */
    const val nav = 240

    /** Sheets travel further than screens, so they get slightly longer. */
    const val sheet = 280
}

// ---------------------------------------------------------------------------
// Component dimensions
// ---------------------------------------------------------------------------

object TazSize {
    // icons — one ramp, so nothing is arbitrarily sized
    val iconXs: Dp = 14.dp
    val iconSm: Dp = 18.dp
    val iconMd: Dp = 22.dp
    val iconLg: Dp = 28.dp

    // controls
    val buttonHeight: Dp = 50.dp
    val buttonHeightSm: Dp = 40.dp
    val inputHeight: Dp = 54.dp
    val chipHeight: Dp = 36.dp
    val navBarHeight: Dp = 62.dp

    /**
     * Width of the quantity stepper when it sits BESIDE content rather than
     * spanning it (cart rows, list rows).
     *
     * Needed because the stepper's interior fills its container so the − and +
     * sit at the pill's edges. As an unweighted child of a Row that makes it
     * claim all remaining width, which starved the cart row's name/price
     * column to zero — the row rendered as an image and a stepper with no
     * product on it. Callers that place the stepper alongside text pass this.
     */
    val stepperInlineWidth: Dp = 116.dp

    val productCardWidth: Dp = 150.dp
    val productImageHeight: Dp = 96.dp
    val categoryTile: Dp = 72.dp
    val touchTarget: Dp = 44.dp        // accessibility minimum
    val topBarHeight: Dp = 56.dp
    val avatar: Dp = 36.dp
    val micButton: Dp = 36.dp
}
