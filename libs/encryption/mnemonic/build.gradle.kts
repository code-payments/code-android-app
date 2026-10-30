import org.jetbrains.kotlin.gradle.plugin.KotlinPlatformType

plugins {
    alias(libs.plugins.flipcash.kmp.library)
    alias(libs.plugins.flipcash.kmp.test.fixtures)
}

// Compiles `src/commonTest/resources` into a generated `TestFixtures.kt` on `commonTest` --
// see the `flipcash.kmp.test.fixtures` convention plugin.
testFixtures {
    packageName = "com.getcode.crypt"
}

@OptIn(org.jetbrains.kotlin.gradle.ExperimentalKotlinGradlePluginApi::class)
kotlin {
    // jvmShared: key and phrase types for both JVM targets (Android and desktop).
    applyDefaultHierarchyTemplate {
        common {
            group("jvmShared") {
                withJvm()
                withCompilations { it.platformType == KotlinPlatformType.androidJvm }
            }
        }
    }

    android {
        namespace = "com.getcode.encryption.mnemonic"
        withHostTest {}
        withDeviceTest {}
    }

    sourceSets {
        commonMain {
            dependencies {
                implementation(project(":libs:encryption:ed25519"))
                implementation(project(":libs:encryption:hmac"))
                implementation(project(":libs:encryption:sha256"))
                implementation(project(":libs:encryption:sha512"))
                implementation(project(":libs:encryption:utils"))
            }
        }
        commonTest {
            dependencies {
                implementation(kotlin("test"))
                implementation(libs.kotlinx.serialization.json)
            }
        }
        getByName("jvmSharedMain") {
            dependencies {
                // Legacy vendored Base58 codec (fromEntropyB58/getBase58EncodedEntropy).
                implementation(project(":libs:encryption:base58"))
            }
        }
        androidMain {
            dependencies {
                // Domain.kt uses android.net.Uri.
                implementation(libs.androidx.core)
            }
        }
        getByName("androidDeviceTest") {
            dependencies {
                // Cross-platform derivation test-vector gate (instrumented: JNI ed25519 pipeline).
                implementation(libs.androidx.junit)
                implementation(libs.androidx.test.runner)
            }
        }
    }
}
