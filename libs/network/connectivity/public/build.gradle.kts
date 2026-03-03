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
    implementation(libs.kotlinx.serialization.json)
    implementation(libs.javax.inject)

    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.junit)
    androidTestImplementation(libs.androidx.test.runner)
    implementation(libs.hilt.android)

    implementation(libs.bugsnag)
}
