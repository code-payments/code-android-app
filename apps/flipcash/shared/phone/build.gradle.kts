plugins {
    alias(libs.plugins.flipcash.android.feature)
}

android {
    namespace = "${Gradle.flipcashNamespace}.shared.phone"
}

dependencies {
    api(libs.lib.phone.number.port)

    api(libs.rinku.compose)
}
