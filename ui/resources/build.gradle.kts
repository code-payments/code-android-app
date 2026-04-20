plugins {
    alias(libs.plugins.flipcash.android.library.compose)
}

android {
    namespace = "${Gradle.codeNamespace}.util.resources"
}

dependencies {
    api(libs.androidx.annotation)
    api(libs.androidx.appcompat)
    api(libs.androidx.core)
    implementation(libs.androidx.exifinterface)
    api(libs.kotlin.stdlib)
    api(libs.kotlinx.coroutines.core)
    api(libs.kotlinx.coroutines.rx3)
}
