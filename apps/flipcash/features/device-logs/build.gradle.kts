plugins {
    alias(libs.plugins.flipcash.android.feature)
}

android {
    namespace = "${Gradle.flipcashNamespace}.features.devicelogs"
}

dependencies {
    implementation(project(":libs:logging"))
}
