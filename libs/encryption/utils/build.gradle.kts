plugins {
    alias(libs.plugins.flipcash.android.library)
}

android {
    namespace = "${Gradle.codeNamespace}.encryption.utils"
}

dependencies {
    implementation(project(":libs:crypto:kin"))
    implementation(project(":libs:encryption:base58"))
    implementation(project(":libs:encryption:ed25519"))
    implementation(libs.kotlinx.serialization.json)
}
