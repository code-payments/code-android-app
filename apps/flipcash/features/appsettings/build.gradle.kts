plugins {
    alias(libs.plugins.flipcash.android.feature)
}

android {
    namespace = "${Gradle.flipcashNamespace}.features.appsettings"
}

dependencies {
    implementation(project(":apps:flipcash:shared:appsettings"))
    implementation(project(":ui:biometrics"))
}
