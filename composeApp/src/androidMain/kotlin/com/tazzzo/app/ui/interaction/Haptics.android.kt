package com.tazzzo.app.ui.interaction

import android.os.Build
import android.view.HapticFeedbackConstants
import android.view.View
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalView

/**
 * Android haptics via the view's own feedback channel, so the OS "touch
 * feedback" setting is respected automatically — we never drive the vibrator
 * directly and never need the VIBRATE permission.
 *
 * CONFIRM and REJECT are API 30+; below that they fall back to the closest
 * pre-30 constant rather than doing nothing, because Limit and Success are the
 * two roles a customer most needs to feel.
 */
private class AndroidHaptics(private val view: View) : Haptics {

    override fun perform(haptic: TazHaptic) {
        val constant = when (haptic) {
            TazHaptic.Tap -> HapticFeedbackConstants.VIRTUAL_KEY
            TazHaptic.Select -> HapticFeedbackConstants.CLOCK_TICK
            TazHaptic.Add -> HapticFeedbackConstants.KEYBOARD_TAP
            TazHaptic.Limit ->
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) HapticFeedbackConstants.REJECT
                else HapticFeedbackConstants.LONG_PRESS
            TazHaptic.Success ->
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) HapticFeedbackConstants.CONFIRM
                else HapticFeedbackConstants.VIRTUAL_KEY
            TazHaptic.Error ->
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) HapticFeedbackConstants.REJECT
                else HapticFeedbackConstants.LONG_PRESS
        }
        // Never let a feedback failure surface as an app failure.
        runCatching { view.performHapticFeedback(constant) }
    }
}

@Composable
actual fun rememberHaptics(): Haptics {
    val view = LocalView.current
    return remember(view) { AndroidHaptics(view) }
}
