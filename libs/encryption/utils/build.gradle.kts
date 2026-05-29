plugins {
    alias(libs.plugins.flipcash.android.library)
}

android {
    namespace = "${Gradle.codeNamespace}.encryption.utils"
}

dependencies {
    implementation(project(":libs:encryption:base58"))
    implementation(libs.protobuf.kotlin.lite)
    implementation(project(":libs:encryption:ed25519"))
    implementation(libs.bundles.kotlinx.serialization)

    testImplementation(kotlin("test"))
    testImplementation(libs.robolectric)
}
