package com.tazzzo.app.home

import com.tazzzo.app.config.AppEnvironment
import com.tazzzo.app.config.BuildEnvironment
import com.tazzzo.app.config.BuildTarget
import com.tazzzo.app.config.UatApiBase
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

/**
 * What a binary BUILT for UAT looks like at runtime. These only assert anything when the unit tests are compiled with
 * `-Ptazzzo.env=uat` (CI job `uat-android` runs `testDebugUnitTest -Ptazzzo.env=uat --tests '*UatTargetBinaryTest'`); in an ordinary
 * run the generated target is `prod` and every test returns at once, so these never weaken the production-target suite.
 * (The unit-test variant is a debug build, so this proves the generated constants and the UAT-target gates; that the shipped
 * `uat` build type is non-debug is checked on the APK itself.)
 */
class UatTargetBinaryTest {
    private fun uatOnly(block: () -> Unit) { if (AppEnvironment.target == BuildTarget.UAT) block() }

    @Test fun theGeneratedConstantsSelectUatAndTheDefaultAddress() = uatOnly {
        assertEquals(BuildEnvironment.UAT, AppEnvironment.current)
        assertEquals(UatApiBase.DEFAULT, BuildEnvironment.UAT.gatewayBaseUrl)
        assertEquals(UatApiBase.DEFAULT, AppEnvironment.gatewayBaseUrl)
        assertNotEquals(BuildEnvironment.PROD.gatewayBaseUrl, AppEnvironment.gatewayBaseUrl)
    }

    @Test fun aUatTargetBinaryHasNoDevToolingOrDeveloperLoggingEvenWhenBuiltAsDebug() = uatOnly {
        assertFalse(AppEnvironment.allowsDevTooling)
        assertFalse(AppEnvironment.allowsDeveloperLogging)
        assertTrue("target=uat" in AppEnvironment.describe)
    }

    @Test fun theProductionEnvironmentCannotBeSelectedInAUatTargetBinary() = uatOnly {
        AppEnvironment.debugOverride = BuildEnvironment.PROD
        try { assertEquals(BuildEnvironment.UAT, AppEnvironment.current) } finally { AppEnvironment.debugOverride = null }
    }
}
