package com.tazzzo.app.config

import platform.Foundation.NSBundle

actual fun appVersionLabel(): String {
    val info = NSBundle.mainBundle.infoDictionary
    val short = info?.get("CFBundleShortVersionString") as? String
    val build = info?.get("CFBundleVersion") as? String
    return when {
        short != null && build != null -> "$short ($build)"
        short != null -> short
        else -> "unknown"
    }
}
