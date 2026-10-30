plugins {
    alias(libs.plugins.flipcash.kmp.app.library)
    alias(libs.plugins.compose.compiler)
}

kotlin {
    android {
        namespace = "${Gradle.codeNamespace}.services.opencode.compose"
    }

    sourceSets {
        commonMain.dependencies {
            api(project(":services:opencode"))
            implementation(libs.jetbrains.compose.runtime)
        }
    }
}
