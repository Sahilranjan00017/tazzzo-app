plugins {
    // Android plugins are added here (apply false) once the Android SDK is
    // installed — see README "Enabling Android".
    alias(libs.plugins.androidApplication) apply false
    alias(libs.plugins.kotlinMultiplatform) apply false
    alias(libs.plugins.composeMultiplatform) apply false
    alias(libs.plugins.composeCompiler) apply false
}
