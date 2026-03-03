plugins {
    alias(libs.plugins.flipcash.android.library)
}

android {
    namespace = "${Gradle.codeNamespace}.encryption.base58"
}

dependencies {
    implementation(libs.kin.sdk)
}
