package com.tazzzo.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        // Debug-only QA switches, read before any composition runs. MainActivity is exported, so in a release build
        // the extras are never even parsed: another app cannot skip onboarding or open a demo path.
        if (com.tazzzo.app.config.AppEnvironment.allowsDevTooling) {
            DemoFlags.failLoad = intent?.getBooleanExtra("taz_fail_load", false) == true
            DemoFlags.startAtHome = intent?.getBooleanExtra("taz_start_home", false) == true
            DemoFlags.mockCatalog = intent?.getBooleanExtra("taz_mock_catalog", false) == true
        }

        com.tazzzo.app.data.auth.AndroidAppContext.init(this)
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        setContent { App() }
    }
}
