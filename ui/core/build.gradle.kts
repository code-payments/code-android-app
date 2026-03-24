plugins {
    alias(libs.plugins.flipcash.android.library.compose)
}

android {
    namespace = "${Gradle.codeNamespace}.ui.core"
}

dependencies {
    implementation(libs.compose.animation)
    implementation(libs.compose.activities)
    implementation(libs.compose.material)
    implementation(libs.kotlinx.serialization.core)
    api(project(":ui:resources"))
    api(project(":ui:testing"))
    implementation(project(":ui:theme"))
}
