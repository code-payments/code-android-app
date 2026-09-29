import org.jetbrains.kotlin.gradle.plugin.KotlinSourceSetTree

plugins {
    kotlin("multiplatform")
    id("com.android.kotlin.multiplatform.library")
    alias(libs.plugins.flipcash.kmp.test.fixtures)
}

// Compiles `src/commonTest/resources` (chat_cipher.json, canonical copy in the orchestrator's
// `test-vectors/`) into a generated `TestFixtures.kt` on `commonTest`, readable from every target.
testFixtures {
    packageName = "com.getcode.chatcipher"
}

kotlin {
    android {
        namespace = "com.getcode.encryption.chatcipher"
        compileSdk {
            version = release(libs.versions.android.compileSdk.get().toInt()) {
                minorApiLevel = libs.versions.android.compileSdkMinor.get().toInt()
            }
        }
        minSdk = 29
        // No host test: libsodium's Android binding loads its .so from the APK and the Ed25519
        // actual is JNI, so neither loads on a JVM host. The device test runs commonTest instead,
        // and the Apple targets run it natively.
        withDeviceTestBuilder {
            sourceSetTreeName = KotlinSourceSetTree.test.name
        }.configure {
            instrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        }
    }

    iosArm64()
    iosSimulatorArm64()
    iosX64()
    macosArm64()
    macosX64()

    sourceSets {
        commonMain {
            dependencies {
                // KeyPair is part of this module's API.
                api(project(":libs:encryption:ed25519"))
                implementation(project(":libs:encryption:hmac"))
                implementation(libs.sodium.bindings.kmp)
            }
        }
        commonTest {
            dependencies {
                implementation(kotlin("test"))
                implementation(libs.kotlinx.serialization.json)
            }
        }
        getByName("androidDeviceTest") {
            dependencies {
                implementation(libs.androidx.junit)
                implementation(libs.androidx.test.runner)
            }
        }
    }
}
