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
 *
 * The one thing that can differ between two release binaries is the [BuildTarget], chosen by Gradle when the binary is
 * built (`-Ptazzzo.env=uat`; default `prod`). A PROD-target binary can only ever reach PROD (release) and never UAT; a
 * UAT-target binary can only ever reach UAT (release) and never PROD. See docs/uat/UAT_BUILD.md.
 */
enum class BuildEnvironment(val gatewayBaseUrl: String) {
    /** Local / on-device development. */
    DEV("https://dev-api.tazzzo.com"),

    /** Pre-production verification against real services. */
    STAGING("https://staging-api.tazzzo.com"),

    /** Customers. */
    PROD("https://api.tazzzo.com"),

    /**
     * Internal acceptance testing. Its address comes from the generated build constants ([GeneratedBuildTarget]) and is
     * EMPTY in every non-UAT build, so a production binary contains no UAT address and nothing can select this entry
     * there ([AppEnvironment.resolve] never returns it unless the binary was built for UAT).
     */
    UAT(GeneratedBuildTarget.UAT_API_BASE);
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

    /** What this binary was built for. A compile-time constant from Gradle; see [BuildTarget]. */
    val target: BuildTarget = BuildTarget.fromCode(GeneratedBuildTarget.TARGET)

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
        get() = resolve(isDebug, debugOverride, target)

    /**
     * Pure form of [current]. A release build is fixed by its [BuildTarget] whatever override is offered: PROD for a
     * PROD-target binary (the default), UAT for a UAT-target binary. A debug build may move between environments, but
     * never into the other target's production-facing choice: a PROD-target debug build ignores a UAT override, and a
     * UAT-target debug build ignores a PROD override and defaults to UAT.
     */
    fun resolve(isDebug: Boolean, override: BuildEnvironment?, target: BuildTarget = BuildTarget.PROD): BuildEnvironment =
        when (target) {
            BuildTarget.PROD ->
                if (isDebug) (override?.takeUnless { it == BuildEnvironment.UAT } ?: BuildEnvironment.DEV) else BuildEnvironment.PROD
            BuildTarget.UAT ->
                if (isDebug) (override?.takeUnless { it == BuildEnvironment.PROD } ?: BuildEnvironment.UAT) else BuildEnvironment.UAT
        }

    /**
     * Base URL for the API gateway in this environment. The UAT address is validated here (https only, never the
     * production host) and a value that fails is a startup error, not a silent fallback to another backend.
     */
    val gatewayBaseUrl: String get() = baseUrlFor(current)

    /** Pure form of [gatewayBaseUrl]. */
    fun baseUrlFor(environment: BuildEnvironment, productionBase: String = BuildEnvironment.PROD.gatewayBaseUrl): String =
        if (environment == BuildEnvironment.UAT) {
            when (val check = UatApiBase.check(environment.gatewayBaseUrl, productionBase)) {
                is ApiBaseCheck.Ok -> check.base
                is ApiBaseCheck.Rejected -> throw IllegalStateException("UAT API base rejected: ${check.reason}")
            }
        } else environment.gatewayBaseUrl

    /**
     * Whether QA/demo affordances may run: the demo autopilot, the
     * "fail the next catalogue load" switch, the start-at-home jump.
     *
     * False in release. This is the single gate — the platform actuals consult
     * it rather than each deciding for itself.
     */
    val allowsDevTooling: Boolean get() = isDebug && target == BuildTarget.PROD

    /**
     * Whether diagnostics may be written to the device log.
     *
     * False in release, so DevLogSink cannot ship enabled and analytics
     * payloads (which may contain a raw search query — see BLOCKERS.md)
     * cannot be printed on a customer's device.
     */
    val allowsDeveloperLogging: Boolean get() = isDebug && target == BuildTarget.PROD

    /** One line for crash reports and QA screenshots. Never shown to customers. */
    val describe: String
        get() = "env=${current.name} target=${target.code} debug=$isDebug devTooling=$allowsDevTooling"
}
