package com.tazzzo.app.ui.interaction

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import platform.UIKit.UIImpactFeedbackGenerator
import platform.UIKit.UIImpactFeedbackStyle
import platform.UIKit.UINotificationFeedbackGenerator
import platform.UIKit.UINotificationFeedbackType
import platform.UIKit.UISelectionFeedbackGenerator

/**
 * iOS haptics through UIFeedbackGenerator.
 *
 * The generators are held for the lifetime of the composition and `prepare()`d
 * up front: an unprepared generator can take ~100 ms to spin up the Taptic
 * Engine, which is exactly long enough for the feedback to land after the
 * visual response and feel broken. Preparing costs nothing if unused.
 *
 * Impact styles are chosen to match Android's weights so the two platforms
 * feel like the same product:
 *   Tap → light · Add → medium · Select → selection click
 *   Limit/Error → notification error · Success → notification success
 *
 * All calls are wrapped: on a device without a Taptic Engine, or with haptics
 * disabled, these are silent no-ops rather than failures.
 */
private class IosHaptics : Haptics {

    private val light = UIImpactFeedbackGenerator(UIImpactFeedbackStyle.UIImpactFeedbackStyleLight)
    private val medium = UIImpactFeedbackGenerator(UIImpactFeedbackStyle.UIImpactFeedbackStyleMedium)
    private val selection = UISelectionFeedbackGenerator()
    private val notification = UINotificationFeedbackGenerator()

    init {
        runCatching {
            light.prepare()
            medium.prepare()
            selection.prepare()
            notification.prepare()
        }
    }

    override fun perform(haptic: TazHaptic) {
        runCatching {
            when (haptic) {
                TazHaptic.Tap -> light.impactOccurred()
                TazHaptic.Add -> medium.impactOccurred()
                TazHaptic.Select -> selection.selectionChanged()
                TazHaptic.Limit -> notification.notificationOccurred(
                    UINotificationFeedbackType.UINotificationFeedbackTypeWarning
                )
                TazHaptic.Success -> notification.notificationOccurred(
                    UINotificationFeedbackType.UINotificationFeedbackTypeSuccess
                )
                TazHaptic.Error -> notification.notificationOccurred(
                    UINotificationFeedbackType.UINotificationFeedbackTypeError
                )
            }
        }
    }
}

@Composable
actual fun rememberHaptics(): Haptics = remember { IosHaptics() }
