plugins {
    alias(libs.plugins.flipcash.android.library.compose)
}

android {
    namespace = "${Gradle.codeNamespace}.ui.biometrics"

    buildFeatures {
        buildConfig = true
    }
}

dependencies {
    api(project(":libs:biometrics"))
    implementation(project(":ui:components"))
    implementation(project(":ui:theme"))

    api(libs.androidx.annotation)
    api(libs.kotlin.stdlib)
    api(libs.kotlinx.coroutines.core)
    implementation(libs.compose.animation)
    implementation(libs.compose.material)
}
