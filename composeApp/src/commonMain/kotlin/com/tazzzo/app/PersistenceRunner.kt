package com.tazzzo.app

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import com.tazzzo.app.analytics.Analytics
import com.tazzzo.app.analytics.AnalyticsEvents

/** Restores persisted state once at startup and reports app_open. */
@Composable
fun PersistenceRunner() {
    val app = LocalAppState.current
    LaunchedEffect(Unit) {
        Analytics.track(AnalyticsEvents.APP_OPEN)
        app.restoreFromDisk()
    }
}
