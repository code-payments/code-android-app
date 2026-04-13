plugins {
    alias(libs.plugins.flipcash.android.library)
}

android {
    namespace = "${Gradle.flipcashNamespace}.shared.tokens.core"
}

dependencies {
    api(project(":services:opencode"))
}
