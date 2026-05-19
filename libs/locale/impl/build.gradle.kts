plugins {
    alias(libs.plugins.flipcash.android.library)
    id("com.google.devtools.ksp")
}

android {
    namespace = "${Gradle.codeNamespace}.util.locale"
}

dependencies {
    api(project(":libs:locale:public"))
    implementation(project(":libs:datetime"))
    implementation(project(":libs:currency"))
    api(libs.androidx.annotation)
    api(libs.kotlin.stdlib)
    api(libs.kotlinx.coroutines.core)

    implementation(libs.bundles.hilt)
    ksp(libs.bundles.hilt.compiler)
}
