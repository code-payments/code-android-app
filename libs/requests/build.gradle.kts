plugins {
    alias(libs.plugins.flipcash.android.library.compose)
}

android {
    namespace = "${Gradle.codeNamespace}.libs.requests"
}

dependencies {
    implementation(project(":services:legacy-shared"))
    implementation(project(":libs:currency"))
    implementation(project(":libs:logging"))
    implementation(project(":libs:models"))
    implementation(project(":libs:encryption:base58"))
    implementation(project(":libs:encryption:keys"))
    implementation(project(":libs:encryption:mnemonic"))
    implementation(project(":libs:encryption:utils"))
    implementation(project(":ui:resources"))

    implementation(libs.bundles.kotlinx.serialization)
    implementation(libs.kotlinx.datetime)
    implementation(libs.bundles.hilt)

    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.junit)
    androidTestImplementation(libs.androidx.test.runner)
}
