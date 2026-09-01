package com.tazzzo.app.config

import kotlin.experimental.ExperimentalNativeApi
import kotlin.native.Platform

/**
 * Kotlin/Native records the binary's build mode at compile time. A release
 * framework reports false and there is no runtime path to change it.
 */
@OptIn(ExperimentalNativeApi::class)
actual fun isDebugBuild(): Boolean = Platform.isDebugBinary
