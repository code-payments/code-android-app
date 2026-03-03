plugins {
    alias(libs.plugins.flipcash.android.library)
}

android {
    namespace = "${Gradle.codeNamespace}.encryption.mnemonic"
}

dependencies {
    implementation(project(":libs:encryption:base58"))
    implementation(project(":libs:encryption:ed25519"))
    implementation(project(":libs:encryption:hmac"))
    implementation(project(":libs:encryption:sha256"))
    implementation(project(":libs:encryption:sha512"))
    implementation(project(":libs:encryption:utils"))
    implementation(libs.grpc.okhttp)
    implementation(libs.grpc.kotlin)
    implementation(libs.androidx.core)
}
