plugins {
    alias(libs.plugins.flipcash.android.library)
    id("com.google.devtools.ksp")
    id("dagger.hilt.android.plugin")
}

android {
    namespace = "${Gradle.codeNamespace}.libs.network"
}

dependencies {
    implementation(project(":libs:network:connectivity:impl"))
    api(project(":libs:network:connectivity:public"))

    implementation(libs.bundles.hilt)
    ksp(libs.bundles.hilt.compiler)
}
