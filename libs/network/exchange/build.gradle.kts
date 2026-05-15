plugins {
    alias(libs.plugins.flipcash.android.library.compose)
}

android {
    namespace = "${Gradle.codeNamespace}.libs.exchange"
}

dependencies {
    implementation(project(":libs:currency"))

    implementation(libs.bundles.kotlinx.serialization)
    implementation(libs.kotlinx.datetime)
    implementation(libs.bundles.hilt)

    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.junit)
    androidTestImplementation(libs.androidx.test.runner)
}
