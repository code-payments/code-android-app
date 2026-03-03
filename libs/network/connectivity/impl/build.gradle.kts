plugins {
    alias(libs.plugins.flipcash.android.library)
}

android {
    namespace = "${Gradle.codeNamespace}.libs.network"
}

dependencies {
    api(project(":libs:network:connectivity:public"))

    implementation(libs.kotlinx.serialization.json)

    implementation(libs.javax.inject)
    implementation(libs.hilt.android)
}
