plugins {
    alias(libs.plugins.flipcash.android.library)
}

android {
    namespace = "${Gradle.codeNamespace}.encryption.sha256"
}

dependencies {
    implementation(libs.grpc.okhttp)
    implementation(libs.grpc.kotlin)
}
