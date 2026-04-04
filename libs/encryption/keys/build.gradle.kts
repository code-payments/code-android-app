plugins {
    alias(libs.plugins.flipcash.android.library)
}

android {
    namespace = "${Gradle.codeNamespace}.encryption.keys"
}

dependencies {
    implementation(project(":libs:encryption:base58"))
    implementation(project(":libs:encryption:sha256"))
    implementation(project(":libs:encryption:utils"))

    implementation(libs.protobuf.kotlin.lite)
    implementation(libs.grpc.okhttp)
    implementation(libs.grpc.kotlin)
    implementation(libs.kotlinx.serialization.json)

    testImplementation(kotlin("test"))
}
