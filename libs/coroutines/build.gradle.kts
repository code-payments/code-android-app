plugins {
    alias(libs.plugins.flipcash.android.library)
    id("org.jetbrains.kotlin.plugin.parcelize")
}

android {
    namespace = "${Gradle.codeNamespace}.libs.coroutines"
}
