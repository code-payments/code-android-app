plugins {
    alias(libs.plugins.flipcash.kmp.app.library)
    alias(libs.plugins.compose.compiler)
}

kotlin {
    android {
        namespace = "${Gradle.codeNamespace}.util.datetime"
    }

    sourceSets {
        commonMain.dependencies {
            api(libs.kotlinx.datetime)
            implementation(libs.jetbrains.compose.runtime)
        }
        androidMain.dependencies {
            implementation(libs.jetbrains.compose.ui)
        }
        getByName("androidHostTest").dependencies {
            implementation(kotlin("test"))
            implementation(libs.robolectric)
        }
    }
}
