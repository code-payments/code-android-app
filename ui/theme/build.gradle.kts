plugins {
    alias(libs.plugins.flipcash.kmp.app.library)
    alias(libs.plugins.compose.compiler)
}

kotlin {
    android {
        namespace = "com.getcode.theme"
        androidResources {
            enable = true
        }
    }

    sourceSets {
        commonMain.dependencies {
            implementation(project(":libs:logging"))
            api(libs.jetbrains.compose.runtime)
            api(libs.jetbrains.compose.foundation)
            api(libs.jetbrains.compose.ui)
            api(libs.jetbrains.compose.material)
            implementation(libs.jetbrains.compose.ui.tooling.preview)
        }
        androidMain.dependencies {
            implementation(project.dependencies.platform(libs.compose.bom))
            implementation(libs.compose.ui.tools.preview)
            implementation(libs.compose.material3)
            implementation(libs.compose.accompanist)
            implementation(libs.androidx.appcompat)
            implementation(libs.androidx.core)
            implementation(libs.androidx.activity)
        }
        // Desktop reads the same font files Android packages from res/font.
        jvmMain.get().resources.srcDir("src/androidMain/res")
    }
}
