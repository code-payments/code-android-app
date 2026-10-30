plugins {
    alias(libs.plugins.flipcash.android.library)
}

android {
    namespace = "${Gradle.codeNamespace}.util.resources.testfixtures"
}

dependencies {
    api(project(":ui:resources"))
    implementation(platform(libs.compose.bom))
    implementation(libs.compose.ui)
}
