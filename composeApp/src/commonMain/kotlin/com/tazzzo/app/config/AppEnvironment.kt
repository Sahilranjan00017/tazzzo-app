package com.tazzzo.app.config

/**
 * Which backend and which development affordances this build is allowed to use.
 *
 * Before this existed there was exactly one hard-coded gateway constant and no
 * notion of a release build at all: the demo failure flag, the demo autopilot
 * and the console analytics sink were reachable in any binary that shipped.
 *
 * The safety property, and the reason the environment is chosen by BUILD TYPE
 * rather than by configuration: **a release build cannot be pointed at a
 * non-production backend, and cannot have development tooling switched on, by
 * anything at runtime.** No environment variable, no intent extra, no launch
 * argument. Debug builds can move between environments freely; release builds
 * cannot move at all.
 */
enum class BuildEnvironment(val gatewayBaseUrl: String) {
    /** Local / on-device development. */
    DEV("https://dev-api.tazzzo.in"),

    /** Pre-production verification against real services. */
    STAGING("https://staging-api.tazzzo.in"),

    /** Customers. */
    PROD("https://api.tazzzo.in");
}

/**
 * True when this binary was built as a debug binary.
 *
 * Android: `BuildConfig.DEBUG`. iOS: `Platform.isDebugBinary`.
 * Both are decided at compile time and cannot be altered by a running process.
 */
expect fun isDebugBuild(): Boolean

object AppEnvironment {

    val isDebug: Boolean = isDebugBuild()

    /**
     * Debug-build override, for pointing a development build at staging.
     * Ignored entirely in release: [current] never reads it there.
     */
    var debugOverride: BuildEnvironment? = null
        set(value) {
            if (!isDebug) return      // hard no-op in release, not an exception
            field = value
        }

    val current: BuildEnvironment
        get() = if (isDebug) (debugOverride ?: BuildEnvironment.DEV) else BuildEnvironment.PROD

    /** Base URL for the API gateway in this environment. */
    val gatewayBaseUrl: String get() = current.gatewayBaseUrl

    /**
     * Whether QA/demo affordances may run: the demo autopilot, the
     * "fail the next catalogue load" switch, the start-at-home jump.
     *
     * False in release. This is the single gate — the platform actuals consult
     * it rather than each deciding for itself.
     */
    val allowsDevTooling: Boolean get() = isDebug

    /**
     * Whether diagnostics may be written to the device log.
     *
     * False in release, so DevLogSink cannot ship enabled and analytics
     * payloads (which may contain a raw search query — see BLOCKERS.md)
     * cannot be printed on a customer's device.
     */
    val allowsDeveloperLogging: Boolean get() = isDebug

    /** One line for crash reports and QA screenshots. Never shown to customers. */
    val describe: String
        get() = "env=${current.name} debug=$isDebug devTooling=$allowsDevTooling"
}
