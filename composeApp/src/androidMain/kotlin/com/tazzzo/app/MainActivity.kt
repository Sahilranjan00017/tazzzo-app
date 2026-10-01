package com.tazzzo.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        // Debug-only QA switches, read before any composition runs.
        DemoFlags.failLoad = intent?.getBooleanExtra("taz_fail_load", false) == true
        DemoFlags.startAtHome = intent?.getBooleanExtra("taz_start_home", false) == true

        com.tazzzo.app.data.auth.AndroidAppContext.init(this)
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        setContent { App() }
    }
}
