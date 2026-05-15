plugins {
    alias(libs.plugins.flipcash.android.library)
}

android {
    namespace = "${Gradle.codeNamespace}.libs.analytics"
}

dependencies {
    implementation(libs.bundles.hilt)
    implementation(platform(libs.compose.bom))
    implementation(libs.compose.ui)
}
