plugins {
    alias(libs.plugins.flipcash.kmp.app.library)
}

kotlin {
    android {
        namespace = "${Gradle.flipcashNamespace}.shared.tokens.core"
    }

    sourceSets {
        commonMain.dependencies {
            api(project(":services:opencode"))
        }
    }
}
