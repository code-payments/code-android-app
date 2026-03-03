plugins {
    alias(libs.plugins.flipcash.android.library)
}

android {
    namespace = "${Gradle.codeNamespace}.util.locale"
}

dependencies {
    implementation(project(":libs:datetime"))
    implementation(project(":libs:currency"))
    implementation(libs.okhttp)
    api(libs.androidx.annotation)
    api(libs.kotlin.stdlib)
    api(libs.kotlinx.coroutines.core)
    api(libs.kotlinx.coroutines.rx3)
}
