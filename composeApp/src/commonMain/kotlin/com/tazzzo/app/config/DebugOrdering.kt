package com.tazzzo.app.config

/**
 * The developer's explicit, BUILD-TIME opt-in to submitting REAL COD orders from a debug build (to exercise the real order
 * path on a device/emulator before production `orderIntegration` is launched). Default false.
 *
 * Android: `./gradlew :composeApp:assembleDebug -Ptazzzo.debugRealOrdering=true` sets `BuildConfig.DEBUG_REAL_ORDERING` for
 * the DEBUG build type only; the release build type hard-codes false whatever is passed. iOS: always false (no mechanism).
 * Never persisted, never a runtime setting, no UI. It is honoured only together with a debug binary
 * ([com.tazzzo.app.data.order.OrderLaunchGate]).
 */
expect fun debugRealOrderingRequested(): Boolean
