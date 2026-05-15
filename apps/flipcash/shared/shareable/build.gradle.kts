plugins {
    alias(libs.plugins.flipcash.android.feature)
}

android {
    namespace = "${Gradle.flipcashNamespace}.shared.shareable"
}

dependencies {
    implementation(libs.bundles.kotlinx.serialization)

    implementation(libs.androidx.localbroadcastmanager)

    implementation(project(":libs:messaging"))
}
