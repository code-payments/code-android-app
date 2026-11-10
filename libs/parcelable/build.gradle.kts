plugins {
    alias(libs.plugins.flipcash.kmp.app.library)
    alias(libs.plugins.flipcash.kmp.js)
}

kotlin {
    android {
        namespace = "${Gradle.codeNamespace}.libs.parcelable"
    }
}
