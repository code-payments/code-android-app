plugins {
    alias(libs.plugins.flipcash.android.library.compose)
}

android {
    namespace = "${Gradle.codeNamespace}.util.datetime"
}

dependencies {
    api(libs.kotlinx.datetime)

}
