plugins {
    alias(libs.plugins.flipcash.android.library)
}

android {
    namespace = "${Gradle.codeNamespace}.libs.network"
}

dependencies {
    api(project(":libs:network:connectivity:public"))

    implementation(libs.bundles.kotlinx.serialization)

    implementation(libs.bundles.hilt)
}
