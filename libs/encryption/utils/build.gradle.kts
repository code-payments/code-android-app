import org.jetbrains.kotlin.gradle.plugin.KotlinPlatformType

plugins {
    alias(libs.plugins.flipcash.kmp.library)
    alias(libs.plugins.flipcash.kmp.js)
}

@OptIn(org.jetbrains.kotlin.gradle.ExperimentalKotlinGradlePluginApi::class)
kotlin {
    // jvmShared: code for both JVM targets (Android and desktop) that needs java.*.
    // jvmAndJs: jvmShared plus JS, for the base64 helpers :libs:encryption:keys needs on Node.
    applyDefaultHierarchyTemplate {
        common {
            group("jvmAndJs") {
                group("jvmShared") {
                    withJvm()
                    withCompilations { it.platformType == KotlinPlatformType.androidJvm }
                }
                withJs()
            }
        }
    }

    android {
        namespace = "com.getcode.encryption.utils"
        withHostTest {}
    }

    sourceSets {
        commonMain {
            dependencies {
                implementation(project(":libs:encryption:base58"))
            }
        }
        getByName("jvmSharedMain") {
            dependencies {
                implementation(project(":libs:encryption:ed25519"))
            }
        }
        androidMain {
            dependencies {
                implementation(libs.bundles.kotlinx.serialization)
                implementation(libs.timber)
            }
        }
        commonTest {
            dependencies {
                implementation(kotlin("test"))
            }
        }
        getByName("androidHostTest") {
            dependencies {
                implementation(kotlin("test"))
                implementation(libs.robolectric)
            }
        }
    }
}
