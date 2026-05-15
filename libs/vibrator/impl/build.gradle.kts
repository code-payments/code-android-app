plugins {
    alias(libs.plugins.flipcash.android.library)
}

android {
    namespace = "${Gradle.codeNamespace}.util.vibration"
}

dependencies {
    api(project(":libs:vibrator:public"))

    implementation(libs.bundles.hilt)
}
