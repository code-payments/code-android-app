plugins {
    alias(libs.plugins.flipcash.android.library)
}

android {
    namespace = "${Gradle.codeNamespace}.libs.code.detection"
}

dependencies {
    implementation(libs.bundles.hilt)

    api(libs.androidx.camerax.core)

    testImplementation(libs.junit)
}
