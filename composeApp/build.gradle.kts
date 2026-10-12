import org.jetbrains.kotlin.gradle.ExperimentalKotlinGradlePluginApi
import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    alias(libs.plugins.kotlinMultiplatform)
    alias(libs.plugins.composeMultiplatform)
    alias(libs.plugins.composeCompiler)
    alias(libs.plugins.kotlinSerialization)
    alias(libs.plugins.androidApplication)
}

// ---- Build target: prod (default) | uat --------------------------------------------------------------------------------------
// One build-time selector for BOTH platforms, written to a generated commonMain constant (GeneratedBuildTarget) so neither the
// Xcode project nor the Android manifest holds an environment switch. Android: `-Ptazzzo.env=uat` and the `uat` build type.
// iOS: the Xcode "Compile Kotlin Framework" script runs this same Gradle build, and Gradle also reads the TAZZZO_ENV /
// TAZZZO_API_BASE environment variables, which `xcodebuild ... TAZZZO_ENV=uat` exports to that script. Nothing here is read at
// runtime by the app. Default (nothing set) is prod. See docs/uat/UAT_BUILD.md.
val tazzzoProductionApiBase = "https://api.tazzzo.com"   // keep equal to BuildEnvironment.PROD (the guard below rejects it for uat)
val tazzzoDefaultUatApiBase = "https://api-uat.tazzzo.com"   // keep equal to UatApiBase.DEFAULT

fun tazzzoSetting(property: String, envVar: String): String? =
    (providers.gradleProperty(property).orNull ?: providers.environmentVariable(envVar).orNull)?.takeIf { it.isNotBlank() }

/** Same rules as UatApiBase.check in commonMain (which also guards at runtime). Returns the normalised origin or fails the build. */
fun tazzzoValidateUatApiBase(raw: String): String {
    fun refuse(reason: String): Nothing = throw GradleException("tazzzo.apiBase rejected: $reason")
    if (raw != raw.trim()) refuse("surrounding whitespace")
    val value = raw.lowercase()
    if (!value.startsWith("https://")) refuse("must start with https://")
    val match = Regex("^https://([a-z0-9]([a-z0-9-]*[a-z0-9])?(\\.[a-z0-9]([a-z0-9-]*[a-z0-9])?)+)(:([0-9]{1,5}))?/?$").matchEntire(value)
        ?: refuse("must be a plain https origin (host and optional port; no user info, path, query or fragment)")
    val host = match.groupValues[1]
    if (Regex("^[0-9.]+$").matches(host)) refuse("an IP address is not a valid host")
    val port = match.groupValues[6]
    if (port.isNotEmpty() && port.toInt() !in 1..65535) refuse("port out of range")
    if (host == tazzzoProductionApiBase.removePrefix("https://")) refuse("this is the production API host")
    if (host == "localhost" || host.endsWith(".localhost") || host.endsWith(".local")) refuse("a local host is not a UAT API")
    if (!host.substringAfterLast('.').first().isLetter()) refuse("the top-level label must start with a letter (no IP-like hosts)")
    if (host == "tazzzo.com" || host.endsWith(".tazzzo.com")) {
        val labels = host.split('.')
        if (!(labels.size == 3 && (labels[0].startsWith("api-uat") || labels[0].startsWith("uat"))))
            refuse("under tazzzo.com only a three-label host starting with api-uat or uat is a UAT API")
    }
    return "https://$host" + if (port.isNotEmpty()) ":$port" else ""
}

val tazzzoTarget: String = (tazzzoSetting("tazzzo.env", "TAZZZO_ENV") ?: "prod").lowercase().also {
    if (it != "prod" && it != "uat") throw GradleException("tazzzo.env must be 'prod' or 'uat' (got a different value)")
}
val tazzzoUatApiBase: String = run {
    val requested = tazzzoSetting("tazzzo.apiBase", "TAZZZO_API_BASE")
    when {
        tazzzoTarget == "prod" && requested != null ->
            throw GradleException("tazzzo.apiBase is only valid together with tazzzo.env=uat; a production build cannot be pointed anywhere else")
        tazzzoTarget == "prod" -> ""
        else -> tazzzoValidateUatApiBase(requested ?: tazzzoDefaultUatApiBase)
    }
}
if (tazzzoTarget == "uat") logger.lifecycle("tazzzo build target: uat (api base $tazzzoUatApiBase)")

// ---- Guards ------------------------------------------------------------------------------------------------------------------
// Android: a UAT binary must come from the `uat` build type and a production binary from `release`. Keyed on the VARIANT, not on
// a few task names: any requested task that packages/signs/publishes/installs/assembles/minifies/merges (or lint-vitals) a
// Release variant is refused when tazzzo.env=uat, and the same for a Uat variant when it is not uat. Debug, unit-test and ordinary
// lint tasks are not matched. Usability note: plain `assemble`, `build` and `bundle` pull in BOTH variants, so they fail in every
// configuration; name the variant (assembleDebug, assembleUat, assembleRelease).
val variantTask = Regex("^(bundle|package|sign|publish|install|assemble|minify|merge|lintVital)\\w*?(Release|Uat)")
// iOS: the Xcode script phase exports build settings, so the bundle id says what the archive is meant to be. UAT <=> id ends in
// ".uat"; a stray TAZZZO_ENV in the shell (or tazzzo.env in ~/.gradle/gradle.properties) can then not turn the production archive
// into a UAT-addressed one, and a UAT build can not carry the production bundle id.
val xcodeBundleId: String? = providers.environmentVariable("PRODUCT_BUNDLE_IDENTIFIER").orNull?.takeIf { it.isNotBlank() }
val xcodeConfiguration: String? = providers.environmentVariable("CONFIGURATION").orNull
gradle.taskGraph.whenReady {
    val names = allTasks.map { it.name }.toSet()
    val variants = names.mapNotNull { variantTask.find(it)?.groupValues?.get(2) }.toSet()
    if (tazzzoTarget == "uat" && "Release" in variants)
        throw GradleException("tazzzo.env=uat must use the uat build type (assembleUat), not release (${names.filter { variantTask.find(it)?.groupValues?.get(2) == "Release" }.sorted().first()})")
    if (tazzzoTarget != "uat" && "Uat" in variants)
        throw GradleException("the uat build type needs -Ptazzzo.env=uat (otherwise it would be a production-addressed app)")
    if ("embedAndSignAppleFrameworkForXcode" in names) {
        val bundleId = xcodeBundleId
        if (bundleId == null) {
            if (xcodeConfiguration.equals("Release", ignoreCase = true))
                throw GradleException("Xcode did not export PRODUCT_BUNDLE_IDENTIFIER to the script phase; refusing a Release archive whose bundle id cannot be checked against tazzzo.env")
        } else if ((tazzzoTarget == "uat") != bundleId.endsWith(".uat")) {
            throw GradleException(
                if (tazzzoTarget == "uat") "tazzzo.env=uat needs PRODUCT_BUNDLE_IDENTIFIER ending in .uat (e.g. com.tazzzo.app.uat); the production bundle id must not carry a UAT build"
                else "PRODUCT_BUNDLE_IDENTIFIER ends in .uat but tazzzo.env is not uat; a UAT bundle id must not carry a production-addressed build"
            )
        }
    }
}

val generatedBuildTargetDir = layout.buildDirectory.dir("generated/tazzzo/commonMain/kotlin")
val generateBuildTarget = tasks.register("generateBuildTarget") {
    val outDir = generatedBuildTargetDir
    val target = tazzzoTarget
    val uatBase = tazzzoUatApiBase
    inputs.property("target", target)
    inputs.property("uatApiBase", uatBase)
    outputs.dir(outDir)
    doLast {
        val dir = outDir.get().asFile.resolve("com/tazzzo/app/config")
        dir.mkdirs()
        dir.resolve("GeneratedBuildTarget.kt").writeText(
            """
            package com.tazzzo.app.config

            // GENERATED by composeApp/build.gradle.kts (generateBuildTarget). Not committed; do not edit.
            internal object GeneratedBuildTarget {
                const val TARGET: String = "$target"
                const val UAT_API_BASE: String = "$uatBase"
            }
            """.trimIndent() + "\n"
        )
    }
}

kotlin {
    listOf(
        iosArm64(),
        iosSimulatorArm64()
    ).forEach { iosTarget ->
        iosTarget.binaries.framework {
            baseName = "ComposeApp"
            isStatic = true
        }
    }

    androidTarget {
        // Must match the Java target in android.compileOptions below. Enabling
        // BuildConfig introduced a Java compile task and surfaced a pre-existing
        // mismatch (Kotlin defaulted to 21 against Java 17); pinned rather than
        // moving the project's Java level as a side effect.
        compilerOptions {
            jvmTarget.set(JvmTarget.JVM_17)
        }
    }

    sourceSets {
        commonMain {
            kotlin.srcDir(generateBuildTarget)   // GeneratedBuildTarget (build target constants)
        }
        commonMain.dependencies {
            implementation(compose.runtime)
            implementation(compose.foundation)
            implementation(compose.material3)
            implementation(compose.materialIconsExtended)
            implementation(compose.ui)
            implementation(compose.components.resources)
            implementation(compose.components.uiToolingPreview)
            implementation(libs.multiplatform.settings)
            implementation(libs.kotlinx.serialization.json)
            implementation(libs.ktor.client.core)
            implementation(libs.ktor.client.content.negotiation)
            implementation(libs.ktor.serialization.kotlinx.json)
        }
        androidMain.dependencies {
            implementation(libs.androidx.activity.compose)
            implementation(libs.ktor.client.okhttp)
        }
        iosMain.dependencies {
            implementation(libs.ktor.client.darwin)
            // iOS edge-swipe back (PlatformBack.ios.kt). compose-ui 1.9.0 already lists it in its uikit API elements; declared
            // explicitly so the iOS actual never depends on that transitive detail.
            implementation("org.jetbrains.compose.ui:ui-backhandler:1.9.0")
        }
        commonTest.dependencies {
            implementation(kotlin("test"))
            implementation(libs.ktor.client.mock)
            implementation("com.russhwolf:multiplatform-settings-test:1.3.0")
            implementation("org.jetbrains.kotlinx:kotlinx-coroutines-test:1.10.1")
        }
        // On-device UI/journey harness. Runs against the real activity on the
        // emulator and asserts on the SEMANTICS tree — the same tree TalkBack
        // reads — so accessibility claims are verified rather than assumed.
        androidInstrumentedTest.dependencies {
            implementation("androidx.compose.ui:ui-test-junit4:1.9.0")
            implementation("androidx.test.ext:junit:1.2.1")
            implementation("androidx.test:runner:1.6.2")
            implementation("androidx.test:core:1.6.1")
            // For TestState.reset(): clears the default SharedPreferences the app persists into.
            implementation("androidx.preference:preference-ktx:1.2.1")
        }
    }
}

compose.resources {
    packageOfResClass = "tazzzo.resources"
}

dependencies {
    // Registers the test activity the Compose test rule needs. Debug only.
    debugImplementation("androidx.compose.ui:ui-test-manifest:1.9.0")
}

// MOCK-only photography (legacy MOCK catalogue art: prod_*, cat_*.jpg, the old hero and coins banners, 2.3 MB) is referenced only
// behind AppEnvironment.allowsDevTooling, so a release build never loads it. Keep it out of the release package.
// The assets are removed from the merged release assets (not via ignore patterns, which do not see generated Compose resources).
val mockOnlyAssetPattern = Regex("(prod_p[0-9]+\\.jpg|cat_[a-z_]+\\.jpg|hero_basket\\.jpg|banner_coins\\.jpg)")
tasks.matching { it.name == "mergeReleaseAssets" || it.name == "mergeUatAssets" }.configureEach {
    doLast {
        val out = (this as com.android.build.gradle.tasks.MergeSourceSetFolders).outputDir.get().asFile
        out.walkTopDown().filter { it.isFile && mockOnlyAssetPattern.matches(it.name) }.forEach { it.delete() }
    }
}

// iOS equivalent. Compose resources are copied into the .app by SyncComposeResourcesForIosTask; in an Xcode Release build
// (CONFIGURATION=Release) the same MOCK-only photography is dropped from that task's own output directory. A source-set split is
// not possible because the Res accessors that MOCK code compiles against are generated from the same directory. The
// assets are only ever requested behind AppEnvironment.allowsDevTooling (false in a release binary).
// (The task class is internal to the Compose plugin, so it is selected by name and its declared output directory is used.)
tasks.matching { it.name == "syncComposeResourcesForIos" }.configureEach {
    val releaseConfiguration = providers.environmentVariable("CONFIGURATION").map { it.equals("Release", ignoreCase = true) }.orElse(false)
    inputs.property("stripMockOnlyAssets", releaseConfiguration)
    doLast {
        if (releaseConfiguration.get()) {
            outputs.files.forEach { dir ->
                dir.walkTopDown().filter { it.isFile && mockOnlyAssetPattern.matches(it.name) }.forEach { it.delete() }
            }
        }
    }
}

android {
    namespace = "com.tazzzo.app"
    compileSdk = libs.versions.android.compileSdk.get().toInt()
    defaultConfig {
        applicationId = "com.tazzzo.app"
        minSdk = libs.versions.android.minSdk.get().toInt()
        targetSdk = libs.versions.android.targetSdk.get().toInt()
        versionCode = 1
        versionName = "1.0"
        manifestPlaceholders["appLabel"] = "Tazzzo"
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }
    // BuildConfig.DEBUG is how the Android actual of isDebugBuild() decides the
    // environment. Without this the flag does not exist and every build would
    // have to be treated as production.
    buildFeatures {
        buildConfig = true
    }
    // The settings `release` and `uat` share, applied by both so they cannot drift apart (minify/shrink changes go here).
    val releaseLike: com.android.build.api.dsl.ApplicationBuildType.() -> Unit = {
        isMinifyEnabled = false
    }
    buildTypes {
        debug {
            isMinifyEnabled = false
        }
        // Internal acceptance-test build: release-shaped (not debuggable, so BuildConfig.DEBUG is false and dev tooling, MOCK
        // catalogue and developer logging are unreachable) and addressed to the UAT API (-Ptazzzo.env=uat is required, see the
        // task-graph guard above). A different application id and label so it installs next to the production app. SIGNING:
        // the machine's DEBUG keystore, for internal UAT installs only; the production `release` build type is untouched
        // (it has no signing config here) and no key or password exists in this repository.
        create("uat") {
            releaseLike()
            matchingFallbacks += listOf("release")
            isDebuggable = false
            applicationIdSuffix = ".uat"
            manifestPlaceholders["appLabel"] = "Tazzzo UAT"
            signingConfig = signingConfigs.getByName("debug")
        }
        release {
            // Demo flags, the autopilot and DevLogSink are gated on
            // AppEnvironment.allowsDevTooling, which is false whenever
            // BuildConfig.DEBUG is false — i.e. in every release build.
            releaseLike()
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}
