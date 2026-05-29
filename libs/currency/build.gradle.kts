plugins {
    alias(libs.plugins.flipcash.android.library.compose)
    id("org.jetbrains.kotlin.plugin.parcelize")
}

android {
    namespace = "${Gradle.codeNamespace}.libs.currency"
}

dependencies {
    implementation(project(":ui:resources"))

    implementation(libs.bundles.kotlinx.serialization)
    implementation(libs.kotlinx.datetime)
    implementation(libs.bundles.hilt)

    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.junit)
    androidTestImplementation(libs.androidx.test.runner)
}
