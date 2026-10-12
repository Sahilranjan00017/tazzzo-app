package com.tazzzo.app.config

/**
 * Which backend family a binary was BUILT for. Chosen at build time by Gradle (`-Ptazzzo.env=uat`, default `prod`), written
 * into the generated `GeneratedBuildTarget` constants, and never read from anything at runtime: no environment variable,
 * intent extra, launch argument or stored setting can change it.
 *
 * PROD is what every ordinary release build is. UAT is a release-shaped build (REMOTE catalogue, no dev tooling, no developer
 * logging) that talks to the UAT API, for internal acceptance testing. A UAT binary cannot reach the production API and a
 * PROD binary carries no UAT address (see [AppEnvironment.resolve] and [UatApiBase]).
 */
enum class BuildTarget(val code: String, val marker: String?) {
    PROD("prod", null),
    UAT("uat", "UAT");

    companion object {
        /** Unknown or blank codes mean PROD: the safe default is the strictest, production-shaped build. */
        fun fromCode(code: String): BuildTarget = entries.firstOrNull { it.code == code } ?: PROD
    }
}

/** The outcome of checking a UAT API base URL. */
sealed interface ApiBaseCheck {
    data class Ok(val base: String) : ApiBaseCheck
    data class Rejected(val reason: String) : ApiBaseCheck
}

/**
 * The only way a UAT API base reaches the HTTP client. Mirrors the validation in `composeApp/build.gradle.kts` (the build
 * refuses the same values first; this is the runtime guard for anything that bypasses the build).
 *
 * Accepted: `https://<dns-name>[:port]` with at least one dot, lowercase letters, digits and hyphens, no user info, path,
 * query or fragment, and a host different from the production API host. Everything else is rejected, never repaired.
 * Also rejected: a top-level label that does not start with a letter (so decimal, octal and `0x` hex IP spellings fail),
 * `localhost`, `*.localhost`, `*.local`, and any host equal to or under `tazzzo.com` EXCEPT exactly three labels whose first
 * label starts with `api-uat` or `uat` (`api-uat.tazzzo.com`, `uat-api.tazzzo.com`, `uat.tazzzo.com`; never `api.`, `staging-api.`,
 * `www.`, the bare domain or a deeper name). Hosts outside tazzzo.com (the final hostname) follow only the general rules.
 * The same rule is written in `composeApp/build.gradle.kts`; both are tested (`BuildTargetTest`, CI `uat-android`).
 */
object UatApiBase {
    const val DEFAULT = "https://api-uat.tazzzo.com"

    private val ORIGIN = Regex("^https://([a-z0-9]([a-z0-9-]*[a-z0-9])?(\\.[a-z0-9]([a-z0-9-]*[a-z0-9])?)+)(:([0-9]{1,5}))?/?$")
    private const val HOUSE_DOMAIN = "tazzzo.com"
    private val NUMERIC_HOST = Regex("^[0-9.]+$")

    fun check(raw: String?, productionBase: String = BuildEnvironment.PROD.gatewayBaseUrl): ApiBaseCheck {
        if (raw.isNullOrBlank()) return ApiBaseCheck.Rejected("empty")
        if (raw != raw.trim()) return ApiBaseCheck.Rejected("surrounding whitespace")
        val value = raw.lowercase()
        if (!value.startsWith("https://")) return ApiBaseCheck.Rejected("must start with https://")
        val match = ORIGIN.matchEntire(value)
            ?: return ApiBaseCheck.Rejected("must be a plain https origin (host and optional port; no user info, path, query or fragment)")
        val host = match.groupValues[1]
        if (NUMERIC_HOST.matches(host)) return ApiBaseCheck.Rejected("an IP address is not a valid host")
        val port = match.groupValues[6]
        if (port.isNotEmpty() && port.toInt() !in 1..65535) return ApiBaseCheck.Rejected("port out of range")
        if (host == hostOf(productionBase)) return ApiBaseCheck.Rejected("this is the production API host")
        if (host == "localhost" || host.endsWith(".localhost") || host.endsWith(".local")) return ApiBaseCheck.Rejected("a local host is not a UAT API")
        if (!host.substringAfterLast('.').first().isLetter()) return ApiBaseCheck.Rejected("the top-level label must start with a letter (no IP-like hosts)")
        if (host == HOUSE_DOMAIN || host.endsWith(".$HOUSE_DOMAIN")) {
            val labels = host.split('.')
            val uatName = labels.size == 3 && (labels[0].startsWith("api-uat") || labels[0].startsWith("uat"))
            if (!uatName) return ApiBaseCheck.Rejected("under $HOUSE_DOMAIN only a three-label host starting with api-uat or uat is a UAT API")
        }
        return ApiBaseCheck.Ok("https://$host" + if (port.isNotEmpty()) ":$port" else "")
    }

    /** Host part of an `https://host[:port]` base, lower-cased. */
    internal fun hostOf(base: String): String =
        base.lowercase().removePrefix("https://").substringBefore('/').substringBefore(':')
}

/** `1.0 (1)` in production, `1.0 (1) · UAT` in a UAT build: the visible marker next to the version. */
fun versionWithMarker(version: String, target: BuildTarget): String =
    target.marker?.let { "$version · $it" } ?: version
