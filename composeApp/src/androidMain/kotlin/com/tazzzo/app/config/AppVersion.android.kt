package com.tazzzo.app.config

import com.tazzzo.app.BuildConfig

actual fun appVersionLabel(): String = "${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE})"
