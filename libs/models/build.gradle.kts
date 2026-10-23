plugins {
    alias(libs.plugins.flipcash.kmp.app.library)
    id("org.jetbrains.kotlin.plugin.parcelize")
}

kotlin {
    android {
        namespace = "${Gradle.codeNamespace}.libs.models"
    }

    sourceSets {
        commonMain.dependencies {
            api(project(":libs:parcelable"))
            implementation(project(":libs:encryption:base58"))
            implementation(project(":libs:encryption:ed25519"))
            implementation(project(":libs:encryption:keys"))
            implementation(project(":libs:encryption:utils"))
            implementation(project(":libs:currency"))
            implementation(libs.bundles.kotlinx.serialization)
        }
    }
}
