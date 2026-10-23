plugins {
    alias(libs.plugins.flipcash.kmp.app.library)
    alias(libs.plugins.compose.compiler)
}

kotlin {
    android {
        namespace = "${Gradle.flipcashNamespace}.shared.theme"
    }

    sourceSets {
        commonMain.dependencies {
            implementation(project(":ui:theme"))
            implementation(libs.jetbrains.compose.runtime)
            implementation(libs.jetbrains.compose.foundation)
            implementation(libs.jetbrains.compose.ui)
            implementation(libs.jetbrains.compose.animation)
        }
        androidMain.dependencies {
            api(libs.androidx.ui.tooling.preview)
        }
    }
}
