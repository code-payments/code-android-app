plugins {
    alias(libs.plugins.flipcash.android.library)
    id("com.google.devtools.ksp")
    id("dagger.hilt.android.plugin")
}

android {
    namespace = "${Gradle.codeNamespace}.util.vibration"
}

dependencies {
    implementation(project(":libs:vibrator:impl"))
    api(project(":libs:vibrator:public"))

    implementation(libs.bundles.hilt)
    ksp(libs.bundles.hilt.compiler)
}
