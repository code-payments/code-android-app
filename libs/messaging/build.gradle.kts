plugins {
    alias(libs.plugins.flipcash.kmp.app.library)
    alias(libs.plugins.compose.compiler)
}

kotlin {
    android {
        namespace = "${Gradle.codeNamespace}.libs.messaging"
    }

    sourceSets {
        commonMain.dependencies {
            api(libs.kotlin.stdlib)
            api(libs.kotlinx.coroutines.core)
            implementation(libs.jetbrains.compose.foundation)
        }
        androidMain.dependencies {
            api(libs.timber)
        }
    }
}
