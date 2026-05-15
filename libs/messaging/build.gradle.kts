plugins {
    alias(libs.plugins.flipcash.android.library.compose)
}

android {
    namespace = "${Gradle.codeNamespace}.libs.messaging"
}

dependencies {
    api(libs.kotlin.stdlib)
    api(libs.kotlinx.coroutines.core)
    api(libs.timber)
}
