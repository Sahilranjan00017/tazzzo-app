import org.jetbrains.kotlin.gradle.ExperimentalKotlinGradlePluginApi
import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    alias(libs.plugins.kotlinMultiplatform)
    alias(libs.plugins.composeMultiplatform)
    alias(libs.plugins.composeCompiler)
    alias(libs.plugins.kotlinSerialization)
    alias(libs.plugins.androidApplication)
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
        }
        androidMain.dependencies {
            implementation(libs.androidx.activity.compose)
        }
        commonTest.dependencies {
            implementation(kotlin("test"))
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

android {
    namespace = "com.tazzzo.app"
    compileSdk = libs.versions.android.compileSdk.get().toInt()
    defaultConfig {
        applicationId = "com.tazzzo.app"
        minSdk = libs.versions.android.minSdk.get().toInt()
        targetSdk = libs.versions.android.targetSdk.get().toInt()
        versionCode = 1
        versionName = "1.0"
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }
    // BuildConfig.DEBUG is how the Android actual of isDebugBuild() decides the
    // environment. Without this the flag does not exist and every build would
    // have to be treated as production.
    buildFeatures {
        buildConfig = true
    }
    buildTypes {
        debug {
            isMinifyEnabled = false
        }
        release {
            // Demo flags, the autopilot and DevLogSink are gated on
            // AppEnvironment.allowsDevTooling, which is false whenever
            // BuildConfig.DEBUG is false — i.e. in every release build.
            isMinifyEnabled = false
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}
