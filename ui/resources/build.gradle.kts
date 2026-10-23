plugins {
    alias(libs.plugins.flipcash.kmp.app.library)
    alias(libs.plugins.compose.compiler)
}

kotlin {
    android {
        namespace = "${Gradle.codeNamespace}.util.resources"
        androidResources {
            enable = true
        }
    }

    sourceSets {
        commonMain.dependencies {
            api(libs.androidx.annotation)
            api(libs.kotlin.stdlib)
            api(libs.kotlinx.coroutines.core)
            api(libs.jetbrains.compose.runtime)
            api(libs.jetbrains.compose.ui)
        }
        androidMain.dependencies {
            api(libs.androidx.appcompat)
            api(libs.androidx.core)
            implementation(libs.androidx.exifinterface)
            implementation(libs.timber)
        }
    }
}
