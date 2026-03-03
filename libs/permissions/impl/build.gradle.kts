plugins {
    alias(libs.plugins.flipcash.android.library)
}

android {
    namespace = "${Gradle.codeNamespace}.libs.permissions"
}

dependencies {
    api(project(":libs:permissions:public"))

    implementation(libs.javax.inject)
    implementation(libs.hilt.android)
}
