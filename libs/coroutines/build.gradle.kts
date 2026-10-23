plugins {
    alias(libs.plugins.flipcash.kmp.app.library)
}

kotlin {
    android {
        namespace = "${Gradle.codeNamespace}.libs.coroutines"
    }
}
