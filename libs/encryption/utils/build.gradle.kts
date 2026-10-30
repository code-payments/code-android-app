import org.jetbrains.kotlin.gradle.plugin.KotlinPlatformType

plugins {
    alias(libs.plugins.flipcash.kmp.library)
}

@OptIn(org.jetbrains.kotlin.gradle.ExperimentalKotlinGradlePluginApi::class)
kotlin {
    // jvmShared: code for both JVM targets (Android and desktop) that needs java.* or protobuf.
    applyDefaultHierarchyTemplate {
        common {
            group("jvmShared") {
                withJvm()
                withCompilations { it.platformType == KotlinPlatformType.androidJvm }
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
                implementation(libs.protobuf.kotlin.lite)
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
