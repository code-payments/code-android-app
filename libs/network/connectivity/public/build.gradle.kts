plugins {
    alias(libs.plugins.flipcash.android.library.compose)
}

android {
    namespace = "${Gradle.codeNamespace}.libs.network"
}

dependencies {
    implementation(project(":libs:datetime"))
    implementation(project(":libs:logging"))
    debugImplementation(libs.compose.ui.tools)
    implementation(libs.compose.ui.tools.preview)
    implementation(libs.bundles.kotlinx.serialization)
    implementation(libs.bundles.hilt)

    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.junit)
    androidTestImplementation(libs.androidx.test.runner)
}
