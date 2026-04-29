plugins {
    alias(libs.plugins.flipcash.android.library.compose)
}

android {
    namespace = "${Gradle.flipcashNamespace}.shared.theme"
}

dependencies {
    implementation(project(":ui:theme"))
    api(libs.androidx.ui.tooling.preview)
    debugApi(libs.androidx.ui.tooling)
}
