package com.tazzzo.app

import android.content.Context
import androidx.preference.PreferenceManager
import androidx.test.core.app.ApplicationProvider

/**
 * Resets the app's PERSISTED state before a test's activity launches.
 *
 * Why every instrumented class calls this from `init`: the cart, session,
 * membership, tour flag and coupon all persist through `PersistentStore`
 * (multiplatform-settings → default SharedPreferences), and `pm clear` runs
 * once per suite, not per test. Without this, a heavy test that becomes a
 * member and fills the cart (CartPromotionJourneyTest) silently changes the
 * starting state of every class that runs after it — and "Add … to cart"
 * never appears because the product is already in the cart. That is a
 * test-state leak: it passes alone, fails deterministically in the suite, and
 * is easy to misread as an environment failure. Fix the assumption, not the
 * timeout.
 *
 * `init` is used rather than `@Before` because ActivityScenarioRule launches
 * the activity when the rule is applied, which is before `@Before` runs.
 */
object TestState {
    /** Clears persisted state but leaves the catalogue mode as the build decides (REMOTE), for brand-screen evidence. */
    fun resetKeepRemote() {
        val ctx = ApplicationProvider.getApplicationContext<Context>()
        PreferenceManager.getDefaultSharedPreferences(ctx).edit().clear().commit()
    }

    fun reset() {
        // These suites assert on the mock catalogue's content; REMOTE is the default, so ask for MOCK explicitly.
        com.tazzzo.app.data.catalog.CatalogSource.debugOverride = com.tazzzo.app.data.catalog.CatalogMode.MOCK
        val ctx = ApplicationProvider.getApplicationContext<Context>()
        PreferenceManager.getDefaultSharedPreferences(ctx).edit().clear().commit()
    }
}
