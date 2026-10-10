package com.tazzzo.app

import androidx.compose.runtime.Composable

/**
 * System back integration: Android's system back (button/gesture) and iOS's edge-swipe back both route through the same
 * navigation logic the on-screen back arrows use — one behaviour, several triggers. The innermost enabled handler wins, so a
 * screen with its own step (the login OTP step) handles back before the app-level stack does.
 */
@Composable
expect fun PlatformBackHandler(enabled: Boolean, onBack: () -> Unit)
