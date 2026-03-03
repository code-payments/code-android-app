plugins {
    alias(libs.plugins.flipcash.android.library)
}

android {
    namespace = "${Gradle.codeNamespace}.libs.biometrics"
}

dependencies {
    implementation(libs.javax.inject)
    implementation(libs.hilt.android)
    implementation(platform(libs.compose.bom))
    implementation(libs.compose.ui)

    api(libs.androidx.biometrics)
}
