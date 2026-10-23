plugins {
    alias(libs.plugins.flipcash.kmp.app.library)
    alias(libs.plugins.compose.compiler)
}

kotlin {
    android {
        namespace = "${Gradle.codeNamespace}.libs.network"
    }

    sourceSets {
        commonMain.dependencies {
            implementation(project(":libs:datetime"))
            implementation(project(":libs:logging"))
            implementation(libs.bundles.kotlinx.serialization)
            implementation(libs.jetbrains.compose.runtime)
            implementation(libs.javax.inject)
        }
        androidMain.dependencies {
            implementation(project.dependencies.platform(libs.compose.bom))
            implementation(libs.compose.ui.tools.preview)
        }
        getByName("androidHostTest").dependencies {
            implementation(kotlin("test"))
            implementation(libs.kotlinx.coroutines.test)
        }
    }
}
