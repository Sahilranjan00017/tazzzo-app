package com.tazzzo.app.theme

import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import org.jetbrains.compose.resources.Font
import tazzzo.resources.Res
import tazzzo.resources.poppins_bold
import tazzzo.resources.poppins_medium
import tazzzo.resources.poppins_regular
import tazzzo.resources.poppins_semibold

/**
 * The Tazzzo colour system.
 *
 * Derived from the brand logo: deep forest green is IDENTITY and primary
 * action; vivid orange is ENERGY, reserved for savings/offers/urgency — never
 * decoration. Everything else is a deliberately warm neutral ramp, kept
 * desaturated so product photography and prices are the loudest things on a
 * screen. A page that is mostly green is a page where nothing stands out.
 */
object TazColors {
    // --- brand ------------------------------------------------------------
    val Green = Color(0xFF00411C)        // identity, primary actions
    val GreenDark = Color(0xFF0A2B16)    // dark surfaces (deepened for contrast)
    val GreenMid = Color(0xFF1B6B3A)     // gradients, secondary brand
    val GreenSoft = Color(0xFFE8F1EA)    // selected / tinted backgrounds
    val GreenDisabled = Color(0x2900411C)
    val Leaf = Color(0xFF6AA84F)

    // --- accent (savings & urgency only) ----------------------------------
    // Contrast-audited 2026-08-30: the previous #F04E1E measured 3.61:1 on white —
    // it FAILED AA for the savings text and discount badges it was used for.
    // #C74018 clears 4.5:1 on white, cream AND the orange chip ground, and
    // white text on it clears 5.03:1.
    val Orange = Color(0xFFC74018)
    val OrangeSoft = Color(0xFFFFF1EA)

    // --- neutral surface ramp (the missing hierarchy) ---------------------
    // Four levels, lowest (cards) to highest (tiles). The supplied mockups get
    // their depth from a four-step ramp; Tazzzo had two, which is why every
    // panel previously read as the same plane. Hues stay warm — the logo is
    // drawn in #00411C and a cool ground would fight it.
    val Cream = Color(0xFFFAF9F6)        // page background — cleaner, lets cards lift
    val Surface = Color(0xFFFFFFFF)      // L0 cards
    val SurfaceSunken = Color(0xFFF1EFE9) // L1 grouped/inset areas, image wells
    val SurfaceTile = Color(0xFFE6E2D8)  // L2 category tiles, filled thumbs — reads as a
                                         // distinct plane against a card sitting on cream
    val CardBorder = Color(0xFFEBE8E0)   // hairline
    val BorderStrong = Color(0xFFD8D4C9) // inputs, dividers that must read

    // --- ink ramp ---------------------------------------------------------
    val TextPrimary = Color(0xFF16190F)  // near-black, faint green bias
    val TextSecondary = Color(0xFF5B6157)
    // Was #8A9083 (3.28:1 — failed AA at the 11-12sp sizes it is used at).
    val TextTertiary = Color(0xFF73786E) // metadata, timestamps — 4.53:1
    val TextDisabled = Color(0xFFB2B7AA)

    // --- semantic ---------------------------------------------------------
    val Success = Color(0xFF147E3C)   // 4.58:1 on SuccessSoft, 5.15:1 on white
    val SuccessSoft = Color(0xFFE7F5EC)
    val Warning = Color(0xFFB45309)
    val WarningSoft = Color(0xFFFEF3E2)
    val Danger = Color(0xFFB3261E)
    val DangerSoft = Color(0xFFFCEBEA)

    // Bright gold reads only on the dark coin hero (7.01:1 on GreenDark).
    val CoinGold = Color(0xFFE8A200)
    /** Coin accent for LIGHT surfaces — the bright gold measured 2.19:1 on
     *  white and failed even large-text AA. 4.50:1. */
    val CoinInk = Color(0xFF9D6E00)
    val CoinSoft = Color(0xFFFFF6E0)

    val White = Color(0xFFFFFFFF)
    val Scrim = Color(0xB3000000)
}

/**
 * Poppins — the final typeface decision.
 *
 * Chosen for two functional reasons, not appearance alone:
 *  1. It is a geometric sans with rounded terminals, which is what the Tazzzo
 *     wordmark itself is drawn in — the UI and the logo now share a skeleton.
 *  2. It ships a full Devanagari cut (Indian Type Foundry), so Hindi copy —
 *     which this product will need — renders in the SAME family rather than
 *     falling back to a mismatched system face.
 * Cost: ~630 KB for four weights. Trade-off accepted; documented in the spec.
 */
@Composable
fun tazFontFamily(): FontFamily = FontFamily(
    Font(Res.font.poppins_regular, FontWeight.Normal),
    Font(Res.font.poppins_medium, FontWeight.Medium),
    Font(Res.font.poppins_semibold, FontWeight.SemiBold),
    Font(Res.font.poppins_bold, FontWeight.Bold)
)

private val LightScheme = lightColorScheme(
    primary = TazColors.Green,
    onPrimary = TazColors.White,
    primaryContainer = TazColors.GreenSoft,
    onPrimaryContainer = TazColors.Green,
    secondary = TazColors.Orange,
    onSecondary = TazColors.White,
    secondaryContainer = TazColors.OrangeSoft,
    onSecondaryContainer = TazColors.Orange,
    tertiary = TazColors.GreenMid,
    background = TazColors.Cream,
    onBackground = TazColors.TextPrimary,
    surface = TazColors.Surface,
    onSurface = TazColors.TextPrimary,
    surfaceVariant = TazColors.SurfaceSunken,
    onSurfaceVariant = TazColors.TextSecondary,
    outline = TazColors.BorderStrong,
    outlineVariant = TazColors.CardBorder,
    error = TazColors.Danger,
    onError = TazColors.White,
    errorContainer = TazColors.DangerSoft,
    scrim = TazColors.Scrim
)

@Composable
fun TazzzoTheme(content: @Composable () -> Unit) {
    val family = tazFontFamily()

    // Every Material component inherits the brand face.
    val typography = Typography().run {
        copy(
            displayLarge = displayLarge.copy(fontFamily = family),
            displayMedium = displayMedium.copy(fontFamily = family),
            displaySmall = displaySmall.copy(fontFamily = family),
            headlineLarge = headlineLarge.copy(fontFamily = family),
            headlineMedium = headlineMedium.copy(fontFamily = family),
            headlineSmall = headlineSmall.copy(fontFamily = family),
            titleLarge = titleLarge.copy(fontFamily = family),
            titleMedium = titleMedium.copy(fontFamily = family),
            titleSmall = titleSmall.copy(fontFamily = family),
            bodyLarge = bodyLarge.copy(fontFamily = family),
            bodyMedium = bodyMedium.copy(fontFamily = family),
            bodySmall = bodySmall.copy(fontFamily = family),
            labelLarge = labelLarge.copy(fontFamily = family),
            labelMedium = labelMedium.copy(fontFamily = family),
            labelSmall = labelSmall.copy(fontFamily = family)
        )
    }

    MaterialTheme(colorScheme = LightScheme, typography = typography) {
        // Bare Text() calls that set only size/weight still get Poppins.
        CompositionLocalProvider(
            LocalTextStyle provides LocalTextStyle.current.copy(fontFamily = family)
        ) { content() }
    }
}
