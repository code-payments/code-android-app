plugins {
    alias(libs.plugins.flipcash.android.feature)
}

android {
    namespace = "${Gradle.flipcashNamespace}.shared.web"
}

dependencies {
    implementation(libs.compose.webview)

}
