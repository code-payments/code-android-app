plugins {
    alias(libs.plugins.flipcash.android.library)
    id("com.google.devtools.ksp")
    id("dagger.hilt.android.plugin")
}

android {
    namespace = "${Gradle.codeNamespace}.util.locale"
}

dependencies {
    implementation(project(":libs:locale:impl"))
    api(project(":libs:locale:public"))

    implementation(libs.bundles.hilt)
    ksp(libs.bundles.hilt.compiler)
}
