package com.tazzzo.app

import androidx.compose.runtime.Composable
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.backhandler.BackHandler

/**
 * iOS edge-swipe back. Compose Multiplatform (1.8+) dispatches the UIKit back gesture of a `ComposeUIViewController` to the
 * innermost enabled `BackHandler` (`org.jetbrains.compose.ui:ui-backhandler`, an API dependency of compose-ui 1.9.0). The same
 * shared navigation logic as Android's system back runs; where no gesture dispatcher is present this is a no-op. Every pushed
 * screen also keeps its visible back control.
 */
@OptIn(ExperimentalComposeUiApi::class)
@Composable
actual fun PlatformBackHandler(enabled: Boolean, onBack: () -> Unit) {
    BackHandler(enabled = enabled, onBack = onBack)
}
