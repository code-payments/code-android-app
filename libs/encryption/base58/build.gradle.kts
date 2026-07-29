plugins {
    alias(libs.plugins.flipcash.android.library)
}

android {
    namespace = "${Gradle.codeNamespace}.encryption.base58"
}

dependencies {
    testImplementation(kotlin("test"))
    testImplementation(libs.kotlinx.serialization.json) // parse cross-platform test-vector fixtures
}
