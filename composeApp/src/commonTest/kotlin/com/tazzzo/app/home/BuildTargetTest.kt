package com.tazzzo.app.home

import com.tazzzo.app.config.ApiBaseCheck
import com.tazzzo.app.config.AppEnvironment
import com.tazzzo.app.config.BuildEnvironment
import com.tazzzo.app.config.BuildTarget
import com.tazzzo.app.config.UatApiBase
import com.tazzzo.app.config.versionWithMarker
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

/**
 * The UAT build configuration, proven through pure functions (no UAT binary is needed): resolution per build target, the UAT
 * address guard, and the visible marker. The Gradle side of the same rules is exercised by the `uat` CI job.
 *
 * Mutation notes: letting a release+UAT binary return PROD fails [aUatReleaseBuildIsAlwaysUatWhateverOverrideIsOffered]; letting the
 * production host through the guard fails [theProductionHostIsRejectedForUat]; selecting UAT in a production-target debug build
 * fails [aProductionTargetDebugBuildCannotSelectUat].
 */
class BuildTargetTest {

    private val prod = BuildEnvironment.PROD.gatewayBaseUrl

    // ---- resolution -----------------------------------------------------------------------------------------------------

    @Test fun aProductionReleaseBuildIsProdWhateverOverrideIsOffered() {
        for (o in listOf(null) + BuildEnvironment.entries)
            assertEquals(BuildEnvironment.PROD, AppEnvironment.resolve(isDebug = false, override = o, target = BuildTarget.PROD), "$o")
    }

    @Test fun aUatReleaseBuildIsAlwaysUatWhateverOverrideIsOffered() {
        for (o in listOf(null) + BuildEnvironment.entries)
            assertEquals(BuildEnvironment.UAT, AppEnvironment.resolve(isDebug = false, override = o, target = BuildTarget.UAT), "$o")
    }

    @Test fun aDebugBuildMayMoveBetweenItsOwnTargetsEnvironments() {
        assertEquals(BuildEnvironment.DEV, AppEnvironment.resolve(true, null, BuildTarget.PROD))
        assertEquals(BuildEnvironment.STAGING, AppEnvironment.resolve(true, BuildEnvironment.STAGING, BuildTarget.PROD))
        assertEquals(BuildEnvironment.PROD, AppEnvironment.resolve(true, BuildEnvironment.PROD, BuildTarget.PROD))
        assertEquals(BuildEnvironment.UAT, AppEnvironment.resolve(true, null, BuildTarget.UAT))
        assertEquals(BuildEnvironment.STAGING, AppEnvironment.resolve(true, BuildEnvironment.STAGING, BuildTarget.UAT))
    }

    @Test fun aProductionTargetDebugBuildCannotSelectUat() {
        assertEquals(BuildEnvironment.DEV, AppEnvironment.resolve(true, BuildEnvironment.UAT, BuildTarget.PROD))
    }

    @Test fun aUatTargetBuildCanNeverReachTheProductionApi() {
        for (debug in listOf(true, false))
            for (o in listOf(null) + BuildEnvironment.entries)
                assertNotEquals(BuildEnvironment.PROD, AppEnvironment.resolve(debug, o, BuildTarget.UAT), "debug=$debug $o")
    }

    @Test fun aProductionTargetBuildCanNeverReachTheUatApi() {
        for (debug in listOf(true, false))
            for (o in listOf(null) + BuildEnvironment.entries)
                assertNotEquals(BuildEnvironment.UAT, AppEnvironment.resolve(debug, o, BuildTarget.PROD), "debug=$debug $o")
    }

    @Test fun theUatEnvironmentIsNeverTheProductionOrStagingAddress() {
        assertEquals(prod, AppEnvironment.baseUrlFor(BuildEnvironment.PROD))
        // whatever this test binary was built for, the production address is never what UAT resolves to
        if (AppEnvironment.target == BuildTarget.PROD) assertEquals("", BuildEnvironment.UAT.gatewayBaseUrl, "a production binary carries no UAT address")
    }

    @Test fun theBuildTargetOfTheTestBinaryIsTheDefaultProduction() {
        // CI runs the unit tests without -Ptazzzo.env, so this fails if the default ever stops being production.
        assertEquals(BuildTarget.PROD, AppEnvironment.target)
        assertEquals(BuildTarget.PROD, BuildTarget.fromCode(""))
        assertEquals(BuildTarget.PROD, BuildTarget.fromCode("staging"))
        assertEquals(BuildTarget.UAT, BuildTarget.fromCode("uat"))
    }

    @Test fun aNonDebugBuildAlwaysUsesTheRemoteCatalogue() {
        // Only the pure selector is proven here: isDebug=false is what the `uat` build type reports (BuildConfig.DEBUG=false). That a
        // UAT-TARGET binary also has no dev tooling is asserted in UatTargetBinaryTest, which CI runs with -Ptazzzo.env=uat.
        assertEquals(com.tazzzo.app.data.catalog.CatalogMode.REMOTE,
            com.tazzzo.app.data.catalog.CatalogModeSelector.resolve(isDebug = false, debugOverride = com.tazzzo.app.data.catalog.CatalogMode.MOCK))
    }

    // ---- the UAT address guard ------------------------------------------------------------------------------------------

    private fun ok(raw: String?) = assertIs<ApiBaseCheck.Ok>(UatApiBase.check(raw)).base
    private fun rejected(raw: String?) = assertIs<ApiBaseCheck.Rejected>(UatApiBase.check(raw), "$raw")

    @Test fun theDefaultUatAddressIsAcceptedAndIsNotProduction() {
        assertEquals("https://api-uat.tazzzo.com", ok(UatApiBase.DEFAULT))
        assertNotEquals(prod, UatApiBase.DEFAULT)
    }

    @Test fun aFinalHostnameOverrideIsAcceptedAndNormalised() {
        assertEquals("https://api-uat.example.org", ok("https://api-uat.example.org/"))
        assertEquals("https://uat-api.tazzzo.in:8443", ok("https://UAT-API.Tazzzo.IN:8443"))
        for (host in listOf("api-uat.tazzzo.com", "uat.tazzzo.com", "uat-api.tazzzo.com", "api-uat2.tazzzo.com"))
            assertEquals("https://$host", ok("https://$host"), host)
    }

    @Test fun theProductionHostIsRejectedForUat() {
        for (raw in listOf(
            prod, "$prod/", "https://API.TAZZZO.COM", "https://api.tazzzo.com:443", "https://api.tazzzo.com:8443"
        )) assertTrue("production" in rejected(raw).reason, raw)
        // the guard follows the production constant it is given
        assertIs<ApiBaseCheck.Rejected>(UatApiBase.check("https://api-uat.example.org", productionBase = "https://api-uat.example.org"))
    }

    @Test fun anInvalidApiBaseIsRejected() {
        for (raw in listOf(
            null, "", "   ", " https://api-uat.example.org", "https://api-uat.example.org ",
            "http://api-uat.example.org", "ftp://api-uat.example.org", "api-uat.example.org", "//api-uat.example.org",
            "https://", "https://localhost", "https://localhost:8080", "https://10.0.2.2", "https://127.0.0.1", "https://192.168.1.5:8443",
            "https://api-uat.example.org/v1", "https://api-uat.example.org?x=1", "https://api-uat.example.org#f",
            "https://user:pw@api-uat.example.org", "https://api-uat.example.org:0", "https://api-uat.example.org:65536",
            "https://api-uat.example.org:", "https://api_uat.example.org", "https://-api.example.org", "https://api-.example.org",
            "https://api..example.org", "https://api-uat.example.org.", "https://exa mple.org", "https://api-uat.example.org\\evil"
        )) rejected(raw)
    }

    @Test fun theHouseDomainIsOnlyOpenToUatNames() {
        for (raw in listOf(
            "https://api.tazzzo.com", "https://staging-api.tazzzo.com", "https://dev-api.tazzzo.com", "https://www.tazzzo.com",
            "https://tazzzo.com", "https://cms.tazzzo.com", "https://x.api-uat.tazzzo.com", "https://x.api.tazzzo.com", "https://myuat.tazzzo.com"
        )) rejected(raw)
        // a look-alike that is not under tazzzo.com is a different domain and follows only the general rules
        assertEquals("https://api-uat.tazzzo.com.evil.example", ok("https://api-uat.tazzzo.com.evil.example"))
    }

    @Test fun localAndIpLikeHostsAreRejected() {
        for (raw in listOf(
            "https://foo.local", "https://a.localhost", "https://localhost.local", "https://1.1", "https://127.1", "https://0x7f.1",
            "https://0x7f.0x0.0x0.0x1", "https://0300.0250.0.1", "https://2130706433.1", "https://10.0.2.2:8443", "https://api-uat.example.123"
        )) rejected(raw)
    }

    @Test fun anInvalidUatAddressFailsAtStartupInsteadOfFallingBackToAnotherBackend() {
        // BuildEnvironment.UAT carries the generated address; an invalid one must not be silently replaced.
        assertIs<ApiBaseCheck.Rejected>(UatApiBase.check(""))
        if (AppEnvironment.target == BuildTarget.PROD)   // the UAT address is "" in a production-target binary
            assertFailsWith<IllegalStateException> { AppEnvironment.baseUrlFor(BuildEnvironment.UAT) }
    }

    // ---- the visible marker ---------------------------------------------------------------------------------------------

    @Test fun onlyAUatBuildShowsAnEnvironmentMarker() {
        assertEquals("1.0 (1)", versionWithMarker("1.0 (1)", BuildTarget.PROD))
        assertEquals("1.0 (1) · UAT", versionWithMarker("1.0 (1)", BuildTarget.UAT))
        assertFalse("UAT" in versionWithMarker("2.3 (45)", BuildTarget.PROD))
    }
}
