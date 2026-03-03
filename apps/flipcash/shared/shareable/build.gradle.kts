plugins {
    alias(libs.plugins.flipcash.android.feature)
}

android {
    namespace = "${Gradle.flipcashNamespace}.shared.shareable"
}

dependencies {
    implementation(libs.kotlinx.serialization.json)

    implementation(libs.androidx.localbroadcastmanager)

    implementation(project(":libs:messaging"))
}
