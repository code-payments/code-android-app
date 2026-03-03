plugins {
    alias(libs.plugins.flipcash.android.feature)
}

android {
    namespace = "${Gradle.flipcashNamespace}.features.appupdates"
}

dependencies {
    implementation(libs.compose.animation)

    implementation(project(":apps:flipcash:shared:appupdates"))
    implementation(project(":ui:biometrics"))
}
