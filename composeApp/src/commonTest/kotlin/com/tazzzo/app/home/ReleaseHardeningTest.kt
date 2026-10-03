package com.tazzzo.app.home

import com.tazzzo.app.analytics.AnalyticsPolicy
import com.tazzzo.app.analytics.DevLogSink
import com.tazzzo.app.analytics.NoopSink
import com.tazzzo.app.analytics.defaultAnalyticsSink
import com.tazzzo.app.config.AppEnvironment
import com.tazzzo.app.config.BuildEnvironment
import com.tazzzo.app.data.catalog.CatalogCapabilities
import com.tazzzo.app.data.catalog.CatalogMode
import com.tazzzo.app.data.catalog.CatalogModeSelector
import com.tazzzo.app.devToolingOr
import com.tazzzo.app.theme.MotionSettings
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotEquals
import kotlin.test.assertNull
import kotlin.test.assertSame
import kotlin.test.assertTrue

/**
 * App hardening PR-1: the release-mode gates, proven through their pure forms so no release binary is needed.
 * Each platform's entry points (Android intent extras, iOS environment variables) route through these same functions.
 */
class ReleaseHardeningTest {

    // ---- 1. demo / debug entry points ---------------------------------------------------------------------------

    @Test fun releaseNeverReadsADemoEntryPoint() {
        var read = false
        val v = devToolingOr(default = false, allowed = false) { read = true; true }
        assertFalse(v); assertFalse(read, "the value producer must not even run in release")
    }

    @Test fun debugMayReadADemoEntryPoint() {
        assertTrue(devToolingOr(default = false, allowed = true) { true })
        assertEquals("shop", devToolingOr<String?>(default = null, allowed = true) { "shop" })
        assertNull(devToolingOr<String?>(default = null, allowed = false) { "shop" })
    }

    @Test fun aReleaseBuildIsAlwaysProductionWhateverOverrideIsOffered() {
        for (o in listOf(null, BuildEnvironment.DEV, BuildEnvironment.STAGING, BuildEnvironment.PROD))
            assertEquals(BuildEnvironment.PROD, AppEnvironment.resolve(isDebug = false, override = o))
        assertEquals(BuildEnvironment.STAGING, AppEnvironment.resolve(isDebug = true, override = BuildEnvironment.STAGING))
    }

    @Test fun aReleaseBuildAlwaysUsesTheRemoteCatalogue() {
        assertEquals(CatalogMode.REMOTE, CatalogModeSelector.resolve(isDebug = false, debugOverride = CatalogMode.MOCK))
    }

    // ---- 2. release logging -------------------------------------------------------------------------------------

    @Test fun releaseStartsWithASilentSink() {
        assertSame(NoopSink, defaultAnalyticsSink(developerLogging = false))
        assertTrue(defaultAnalyticsSink(developerLogging = true) is DevLogSink)
    }

    @Test fun theDeveloperLogSinkPrintsNothingWhenLoggingIsNotAllowed() {
        val lines = mutableListOf<String>()
        DevLogSink(allowed = { false }, out = { lines += it }).track("order_success", mapOf("order_id" to "ORD_1"))
        assertTrue(lines.isEmpty())
        DevLogSink(allowed = { true }, out = { lines += it }).track("app_open", emptyMap())
        assertEquals(1, lines.size)
    }

    @Test fun releasePayloadsCarryNoCustomerOrActivityIdentifiers() {
        val raw = mapOf(
            "query" to "atta 5kg", "order_id" to "ORD_1", "quote_id" to "Q1", "product_id" to "TZP-1", "phone" to "+919999999999",
            "email" to "a@b.c", "address" to "12 Street", "token" to "t", "otp" to "123456", "amount" to "12000",
            "stage" to "checkout", "retryable" to "true", "step" to "REVIEW"
        )
        val safe = AnalyticsPolicy.apply(raw, developerLogging = false)
        assertEquals(setOf("stage", "retryable", "step"), safe.keys)
        assertEquals(raw, AnalyticsPolicy.apply(raw, developerLogging = true), "debug keeps the full payload for developers")
    }

    // ---- 6. reduced motion --------------------------------------------------------------------------------------

    @Test fun ambientMotionStopsWhenTheSystemAsksForReducedMotion() {
        assertTrue(MotionSettings.ambientAllowed(explicitEnabled = true, systemReduceMotion = false))
        assertFalse(MotionSettings.ambientAllowed(explicitEnabled = true, systemReduceMotion = true))
        assertFalse(MotionSettings.ambientAllowed(explicitEnabled = false, systemReduceMotion = false))
        assertFalse(MotionSettings.ambientAllowed(explicitEnabled = false, systemReduceMotion = true))
    }

    @Test fun androidAnimatorScaleMapsToReducedMotion() {
        assertTrue(MotionSettings.reduceMotionFromAnimatorScale(0f))
        assertFalse(MotionSettings.reduceMotionFromAnimatorScale(1f))
        assertFalse(MotionSettings.reduceMotionFromAnimatorScale(0.5f))
        assertFalse(MotionSettings.reduceMotionFromAnimatorScale(2f))
    }

    @Test fun theSystemSettingDrivesTheLiveFlag() {
        val before = MotionSettings.ambientEnabled
        try {
            MotionSettings.ambientEnabled = true
            MotionSettings.applySystemReduceMotion(true); assertFalse(MotionSettings.ambientEnabled)
            MotionSettings.applySystemReduceMotion(false); assertTrue(MotionSettings.ambientEnabled)
        } finally { MotionSettings.applySystemReduceMotion(false); MotionSettings.ambientEnabled = before }
    }

    // ---- 8. network ---------------------------------------------------------------------------------------------

    @Test fun everyConfiguredEnvironmentIsHttpsAndNeverLocal() {
        for (e in BuildEnvironment.entries) {
            assertTrue(e.gatewayBaseUrl.startsWith("https://"), e.name)
            for (local in listOf("localhost", "127.0.0.1", "10.0.2.2", "192.168.", ".local"))
                assertFalse(local in e.gatewayBaseUrl, "${e.name} $local")
        }
        assertEquals(3, BuildEnvironment.entries.map { it.gatewayBaseUrl }.toSet().size)
        assertNotEquals(BuildEnvironment.PROD.gatewayBaseUrl, BuildEnvironment.STAGING.gatewayBaseUrl)
    }

    // ---- 13. capability locks -----------------------------------------------------------------------------------

    @Test fun productionOrderingAndHistoryStayOffAndNothingElseIsActivated() {
        val c = CatalogCapabilities.REMOTE
        assertFalse(c.orderIntegration); assertFalse(c.orderHistoryIntegration)
        assertFalse(c.search); assertFalse(c.deals); assertFalse(c.banners)
    }

    // ---- 9. production REMOTE mode exposes no mock or demo content --------------------------------------------

    @Test fun remoteModeHasNoFakeCustomerPhoneOrCoins() {
        val id = com.tazzzo.app.ui.profile.remoteIdentity()
        assertNull(id.name); assertNull(id.phone); assertNull(id.email)
        assertFalse(com.tazzzo.app.ui.profile.coinsAvailable(remoteMode = true))
        assertFalse(com.tazzzo.app.ui.profile.configuredSupportChannels().any)
    }

    @Test fun remoteCopyCarriesNoDemoContact_VoiceOrDeliveryPromise() {
        val c = com.tazzzo.app.config.BrandCopy
        val remote = listOf(
            com.tazzzo.app.ui.voice.GenieCopy.BODY, com.tazzzo.app.ui.voice.GenieCopy.STATUS,
            com.tazzzo.app.ui.profile.ProfileCopy.SIGNED_OUT_BODY, com.tazzzo.app.ui.profile.ProfileCopy.HELP_UNAVAILABLE_BODY,
            com.tazzzo.app.ui.order.OrderCopy.HISTORY_UNAVAILABLE_BODY, com.tazzzo.app.ui.home.HomeCopy.ORDERS_UNAVAILABLE_BODY
        ).joinToString(" | ").lowercase()
        assertFalse(c.whatsappNumber.lowercase() in remote)
        for (leak in listOf("whatsapp", "say hi", "minutes", "delivered in", "tz-", "ord_", "asha", "demo", "sample"))
            assertFalse(leak in remote, leak)
        assertTrue("coming soon" in com.tazzzo.app.ui.voice.GenieCopy.STATUS.lowercase())
    }
}
