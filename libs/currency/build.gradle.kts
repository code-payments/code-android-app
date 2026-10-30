plugins {
    alias(libs.plugins.flipcash.kmp.app.library)
    alias(libs.plugins.compose.compiler)
    id("org.jetbrains.kotlin.plugin.parcelize")
}

kotlin {
    android {
        namespace = "${Gradle.codeNamespace}.libs.currency"
        androidResources {
            enable = true
        }
    }

    sourceSets {
        commonMain.dependencies {
            api(project(":libs:parcelable"))
            implementation(project(":ui:resources"))
            implementation(libs.bundles.kotlinx.serialization)
            implementation(libs.kotlinx.datetime)
            implementation(libs.kotlinx.coroutines.core)
            implementation(libs.jetbrains.compose.runtime)
            implementation(libs.androidx.annotation)
            implementation(libs.javax.inject)
        }
    }
}
