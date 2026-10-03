package com.tazzzo.app

/**
 * Android demo flags.
 *
 * The autopilot is an iOS-simulator tool, but the failure flag must be
 * testable here too so the error/retry state can be verified on a real
 * Android device via:
 *   adb shell am start -n com.tazzzo.app/.MainActivity --ez taz_fail_load true
 * Debug-only: MainActivity parses the intent extras only in a debug build, and every read goes through devToolingOr.
 */
object DemoFlags {
    var failLoad: Boolean = false
    var startAtHome: Boolean = false
    var mockCatalog: Boolean = false
}

actual fun isDemoTourEnabled(): Boolean = false

actual fun isDemoHomeEnabled(): Boolean = devToolingOr(false) { DemoFlags.startAtHome }

actual fun isDemoFailLoadEnabled(): Boolean = devToolingOr(false) { DemoFlags.failLoad }

actual fun isMockCatalogRequested(): Boolean = devToolingOr(false) { DemoFlags.mockCatalog }

/** The simulator-screenshot start surface is an iOS tool; Android QA drives the UI directly. */
actual fun demoStartSurface(): String? = null
