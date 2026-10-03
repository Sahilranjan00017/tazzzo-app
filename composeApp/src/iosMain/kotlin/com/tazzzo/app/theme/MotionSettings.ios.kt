package com.tazzzo.app.theme

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import platform.Foundation.NSNotificationCenter
import platform.Foundation.NSOperationQueue
import platform.UIKit.UIAccessibilityIsReduceMotionEnabled
import platform.UIKit.UIAccessibilityReduceMotionStatusDidChangeNotification

@Composable
actual fun SystemReduceMotionEffect() {
    DisposableEffect(Unit) {
        MotionSettings.applySystemReduceMotion(UIAccessibilityIsReduceMotionEnabled())
        val token = NSNotificationCenter.defaultCenter.addObserverForName(
            UIAccessibilityReduceMotionStatusDidChangeNotification, null, NSOperationQueue.mainQueue
        ) { _ -> MotionSettings.applySystemReduceMotion(UIAccessibilityIsReduceMotionEnabled()) }
        onDispose { NSNotificationCenter.defaultCenter.removeObserver(token) }
    }
}
