plugins {
    alias(libs.plugins.flipcash.android.library)
}

android {
    namespace = "${Gradle.codeNamespace}.vendor.kin"
}

dependencies {
    api(libs.kin.sdk)
}
