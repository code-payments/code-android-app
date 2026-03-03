plugins {
    alias(libs.plugins.flipcash.android.library.compose)
}

android {
    namespace = "${Gradle.codeNamespace}.ui.analytics"

    buildFeatures {
        buildConfig = true
    }
}

dependencies {
    api(project(":libs:analytics"))
    implementation(project(":ui:components"))
    implementation(project(":ui:navigation"))

    api(libs.androidx.annotation)
    api(libs.kotlin.stdlib)
    api(libs.kotlinx.coroutines.core)
    implementation(libs.compose.animation)
    implementation(libs.compose.material)
}
