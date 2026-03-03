plugins {
    alias(libs.plugins.flipcash.android.feature)
}

android {
    namespace = "${Gradle.flipcashNamespace}.shared.navigation.flow"
}

dependencies {
    api(libs.rinku.compose)
}
