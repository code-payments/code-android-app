plugins {
    alias(libs.plugins.flipcash.android.library)
}

android {
    namespace = "${Gradle.codeNamespace}.encryption.hmac"
}

dependencies {
    implementation(libs.grpc.okhttp)
    implementation(libs.grpc.kotlin)
}
