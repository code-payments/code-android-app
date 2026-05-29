plugins {
    alias(libs.plugins.flipcash.android.library)
    id("com.google.devtools.ksp")
    id("dagger.hilt.android.plugin")
}

android {
    namespace = "${Gradle.codeNamespace}.libs.permissions"
}

dependencies {
    implementation(project(":libs:permissions:impl"))
    api(project(":libs:permissions:public"))

    implementation(libs.bundles.hilt)
    ksp(libs.bundles.hilt.compiler)
}
