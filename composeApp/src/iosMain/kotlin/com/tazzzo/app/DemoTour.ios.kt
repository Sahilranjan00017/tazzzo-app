package com.tazzzo.app

import platform.Foundation.NSProcessInfo

// Demo flags must be explicitly "1"/"true"/"yes". A stale or empty value left
// over in the simulator's launch environment must never put a normal launch
// into demo mode.
private fun demoFlag(name: String): Boolean {
    val raw = NSProcessInfo.processInfo.environment[name] as? String ?: return false
    return raw.trim().lowercase() in setOf("1", "true", "yes", "on")
}

actual fun isDemoTourEnabled(): Boolean = demoFlag("TAZZZO_DEMO_TOUR")

actual fun isDemoHomeEnabled(): Boolean = demoFlag("TAZZZO_DEMO_HOME")

actual fun isDemoFailLoadEnabled(): Boolean = demoFlag("TAZZZO_DEMO_FAIL_LOAD")

actual fun isMockCatalogRequested(): Boolean = demoFlag("TAZZZO_MOCK_CATALOG")

actual fun demoStartSurface(): String? =
    (NSProcessInfo.processInfo.environment["TAZZZO_DEMO_START"] as? String)?.trim()?.lowercase()?.takeIf { it in setOf("shop", "search", "pdp", "cart", "addresses", "checkout", "orders", "orderdetail", "profile", "help", "about", "coins") }
