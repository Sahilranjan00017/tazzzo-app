package com.tazzzo.app

/**
 * Android demo flags.
 *
 * The autopilot is an iOS-simulator tool, but the failure flag must be
 * testable here too so the error/retry state can be verified on a real
 * Android device via:
 *   adb shell am start -n com.tazzzo.app/.MainActivity --ez taz_fail_load true
 * Debug-only: [DemoFlags] is never set outside MainActivity's intent parsing.
 */
object DemoFlags {
    var failLoad: Boolean = false
    var startAtHome: Boolean = false
    var mockCatalog: Boolean = false
}

actual fun isDemoTourEnabled(): Boolean = false

actual fun isDemoHomeEnabled(): Boolean = DemoFlags.startAtHome

actual fun isDemoFailLoadEnabled(): Boolean = DemoFlags.failLoad

actual fun isMockCatalogRequested(): Boolean = DemoFlags.mockCatalog

/** The simulator-screenshot start surface is an iOS tool; Android QA drives the UI directly. */
actual fun demoStartSurface(): String? = null
