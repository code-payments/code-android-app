plugins {
    alias(libs.plugins.flipcash.kmp.app.library)
    alias(libs.plugins.compose.compiler)
}

kotlin {
    android {
        namespace = "${Gradle.codeNamespace}.ui.core"
        androidResources { enable = true }
    }

    sourceSets {
        commonMain.dependencies {
            implementation(libs.jetbrains.compose.runtime)
            implementation(libs.jetbrains.compose.foundation)
            implementation(libs.jetbrains.compose.ui)
            implementation(libs.jetbrains.compose.material)
            implementation(libs.kotlinx.serialization.core)
            api(project(":ui:resources"))
            implementation(project(":ui:theme"))
        }
        androidMain.dependencies {
            implementation(project.dependencies.platform(libs.compose.bom))
            implementation(libs.compose.animation)
            implementation(libs.compose.activities)
            implementation(libs.compose.material)
            api(project(":ui:testing"))
        }
    }
}
