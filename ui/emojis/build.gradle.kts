plugins {
    alias(libs.plugins.flipcash.android.library.compose)
}

android {
    namespace = "${Gradle.codeNamespace}.ui.emojis"

    buildFeatures {
        buildConfig = true
    }
}

dependencies {
    api(project(":libs:emojis"))
    implementation(project(":libs:search"))
    implementation(project(":ui:core"))
    implementation(project(":ui:theme"))
    implementation(project(":ui:resources"))
    implementation("androidx.emoji2:emoji2:1.5.0")
    // Optional: Include a bundled emoji font (offline support)
    implementation("androidx.emoji2:emoji2-bundled:1.5.0")
    api(libs.androidx.annotation)
    api(libs.kotlin.stdlib)
    api(libs.kotlinx.coroutines.core)
    implementation(libs.compose.ui.tools.preview)
    implementation(libs.compose.animation)
    implementation(libs.compose.material)
    implementation(libs.compose.material.icons.extended)
}
