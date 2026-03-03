plugins {
    alias(libs.plugins.flipcash.android.library)
}

android {
    namespace = "${Gradle.codeNamespace}.libs.code.detection"
}

dependencies {
    implementation(libs.javax.inject)
    implementation(libs.hilt.android)

    api(libs.androidx.camerax.core)
}
