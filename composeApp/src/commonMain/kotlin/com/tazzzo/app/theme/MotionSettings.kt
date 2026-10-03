package com.tazzzo.app.theme

/**
 * Process-level switch for AMBIENT motion — animation that runs forever
 * without a user action behind it: skeleton shimmer, the marquee on the login
 * wall, the voice banner's pulse and equaliser, the carousel auto-advance,
 * the coach-mark pointer bob.
 *
 * Why a switch exists at all:
 *  - Accessibility. Vestibular-sensitive customers turn on "Reduce motion";
 *    ambient animation is the category that setting is for. Wiring the OS
 *    preference to this flag is done by [SystemReduceMotionEffect]; every ambient
 *    site goes through [ambientEnabled].
 *  - Verification. On-device UI tests wait for Compose to become idle. A
 *    surface with a perpetual animation NEVER idles, so the harness cannot
 *    assert on it. Found the hard way: Home carried six simultaneous infinite
 *    float animations in the voice banner plus a self-advancing pager, and the
 *    semantics tests timed out on "pending recompositions".
 *  - Battery and attention. A header that pulses forever is the "animation
 *    competing for attention" a 5/5 experience is not allowed to have.
 *
 * Feedback motion — press scale, quantity flips, toasts, transitions — is NOT
 * gated. It answers a user action and is over in 150–300 ms.
 */
object MotionSettings {
    /** Explicit switch (tests and previews turn ambient motion off here). */
    private var explicitEnabled: Boolean = true

    /** The operating system's Reduce Motion / Remove animations preference, kept in sync by [SystemReduceMotionEffect]. */
    private val systemReduceMotion = androidx.compose.runtime.mutableStateOf(false)

    /**
     * False = ambient animations render their resting frame and stay still. False whenever the explicit switch is off OR the
     * customer asked the OS to reduce motion. Reads inside a composition are tracked, so a change in the system setting
     * restores or removes the motion without a restart.
     */
    var ambientEnabled: Boolean
        get() = ambientAllowed(explicitEnabled, systemReduceMotion.value)
        set(value) { explicitEnabled = value }

    fun applySystemReduceMotion(reduce: Boolean) { systemReduceMotion.value = reduce }

    /** Android: an animator-duration scale of 0 (Remove animations) means reduced motion. Pure, so it is tested without a device. */
    fun reduceMotionFromAnimatorScale(scale: Float): Boolean = scale == 0f

    /** The mapping, as a pure function so it is tested without a device. */
    fun ambientAllowed(explicitEnabled: Boolean, systemReduceMotion: Boolean): Boolean = explicitEnabled && !systemReduceMotion
}

/**
 * Keeps [MotionSettings] in step with the platform accessibility setting for as long as it is composed.
 * Android: the animator-duration scale (Settings > Accessibility > Remove animations). iOS: Reduce Motion.
 */
@androidx.compose.runtime.Composable
expect fun SystemReduceMotionEffect()
