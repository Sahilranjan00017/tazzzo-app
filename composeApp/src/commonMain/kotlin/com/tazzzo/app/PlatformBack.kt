package com.tazzzo.app

import androidx.compose.runtime.Composable

/**
 * System back integration. iOS has no hardware back (swipe/back arrows are
 * in-UI), so its actual is a no-op; Android routes the system back gesture
 * through the same navigation logic the on-screen back arrows use — one
 * behaviour, two triggers.
 */
@Composable
expect fun PlatformBackHandler(enabled: Boolean, onBack: () -> Unit)
