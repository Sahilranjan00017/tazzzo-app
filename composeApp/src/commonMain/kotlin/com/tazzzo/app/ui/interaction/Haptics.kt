package com.tazzzo.app.ui.interaction

import androidx.compose.runtime.Composable

/**
 * Tazzzo's haptic vocabulary.
 *
 * Deliberately small. A phone that buzzes at everything reads as a toy, so
 * each role below has to earn its place by marking a moment the customer would
 * otherwise have to *look* to confirm. Anything that is already obvious on
 * screen does not get a haptic.
 *
 * Roles, and the only places they are allowed:
 *  - [Tap]     the lightest tick. Primary actions whose result is a navigation
 *              or a commitment. NOT ordinary taps — those have a press state.
 *  - [Select]  a selection genuinely changed: slot, payment, filter, sort, tab.
 *  - [Add]     an item entered the cart or its quantity moved. The single most
 *              repeated meaningful action in a grocery app.
 *  - [Limit]   the app refused: stock cap or per-order cap reached. This is the
 *              canonical haptic moment — the screen barely changes, so the
 *              hand has to be told.
 *  - [Success] an order was placed. Used once per journey, on purpose.
 *  - [Error]   a real failure the customer must act on.
 */
enum class TazHaptic { Tap, Select, Add, Limit, Success, Error }

/**
 * Platform haptic engine.
 *
 * Every implementation must be a SAFE NO-OP when haptics are unavailable —
 * disabled in system settings, unsupported hardware, or a platform with no
 * engine at all. Feedback is an enhancement; nothing in the app may depend on
 * it firing, and nothing may crash because it did not.
 */
interface Haptics {
    fun perform(haptic: TazHaptic)
}

/** No-op engine. Used by tests, previews and any platform without support. */
object NoHaptics : Haptics {
    override fun perform(haptic: TazHaptic) {}
}

/**
 * Remembers the platform engine for the current composition.
 *
 * Cheap to call; implementations pre-warm their generators so the first
 * feedback of a session is not late.
 */
@Composable
expect fun rememberHaptics(): Haptics
