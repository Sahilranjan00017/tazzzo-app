package com.tazzzo.app.config

/**
 * The INSTALLED app version as the platform reports it (Android `versionName (versionCode)`, iOS
 * `CFBundleShortVersionString (CFBundleVersion)`). Never a hard-coded marketing string.
 */
expect fun appVersionLabel(): String
