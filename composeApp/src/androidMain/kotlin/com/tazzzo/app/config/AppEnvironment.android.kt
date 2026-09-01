package com.tazzzo.app.config

import com.tazzzo.app.BuildConfig

/**
 * Decided by AGP at compile time from the build type. A release APK reports
 * false and there is no runtime path to change it.
 */
actual fun isDebugBuild(): Boolean = BuildConfig.DEBUG
