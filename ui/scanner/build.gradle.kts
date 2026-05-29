plugins {
    alias(libs.plugins.flipcash.android.library.compose)
}

android {
    namespace = "${Gradle.codeNamespace}.ui.scanner"

    buildFeatures {
        buildConfig = true
    }
}

dependencies {
    implementation(project(":libs:code-detection"))
    implementation(project(":libs:logging"))
    implementation(project(":libs:quickresponse"))
    implementation(project(":ui:biometrics"))
    implementation(project(":ui:components"))
    implementation(project(":ui:theme"))
    api(project(":vendor:kik:scanner"))
    implementation("com.google.guava:guava:33.6.0-android")

    // cameraX
    implementation(libs.androidx.camerax.core)
    implementation(libs.androidx.camerax.camera2)
    implementation(libs.androidx.camerax.lifecycle)
    implementation(libs.androidx.camerax.view)
    implementation(libs.androidx.annotation)
    implementation(libs.kotlin.stdlib)
    implementation(libs.compose.animation)
    implementation(libs.compose.material)

}
