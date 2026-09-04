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
 *    preference to this flag is E6 work; the gate is here now so every ambient
 *    site already goes through one place.
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
    /** False = ambient animations render their resting frame and stay still. */
    var ambientEnabled: Boolean = true
}
