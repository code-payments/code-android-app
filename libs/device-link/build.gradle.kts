import org.jetbrains.kotlin.gradle.plugin.KotlinSourceSetTree

plugins {
    alias(libs.plugins.flipcash.kmp.library)
    alias(libs.plugins.flipcash.kmp.test.fixtures)
}

// Compiles `src/commonTest/resources` (device_link.json, canonical copy in the orchestrator's
// `test-vectors/`) into a generated `TestFixtures.kt` on `commonTest`, readable from every target.
testFixtures {
    packageName = "com.flipcash.app.devicelink"
}

// The link protocol, crypto and phone-side state, with no UI, so iOS can drive it through
// SharedCore. The Compose screens and the desktop seed store live in :apps:flipcash:shared:device-link.
kotlin {
    android {
        namespace = "com.flipcash.libs.devicelink"
        // No host test: libsodium's Android binding loads its .so from the APK, so it doesn't load
        // on a JVM host (chat-cipher has the same limit). commonTest runs on jvm, on the Apple
        // targets natively, and on Android as a device test.
        withDeviceTestBuilder {
            sourceSetTreeName = KotlinSourceSetTree.test.name
        }.configure {
            instrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        }
    }

    sourceSets {
        commonMain.dependencies {
            // StateFlow is part of PhoneLinkController's API.
            api(libs.kotlinx.coroutines.core)
            implementation(libs.sodium.bindings.kmp)
            implementation(libs.ktor.network)
        }
        commonTest.dependencies {
            implementation(kotlin("test"))
            implementation(libs.kotlinx.serialization.json)
        }
        getByName("androidDeviceTest") {
            dependencies {
                implementation(libs.androidx.junit)
                implementation(libs.androidx.test.runner)
            }
        }
    }
}
