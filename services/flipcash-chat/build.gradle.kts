import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import org.jetbrains.kotlin.gradle.plugin.KotlinSourceSetTree

// Chat crypto and message encoding in common code: group key envelopes, DM and group content
// sealing over :libs:encryption:chat-cipher, and messaging.v1.Content <-> MessageContent over the
// flipcash2 Wire messages (com.flipcash:flipcash2-client-protocol-wire). The Wire classes stay
// internal; the public API is domain types and bytes.
//
// Not on the app's classpath: nothing in apps/ or :services:flipcash depends on this module. The
// Wire artifact `api`s protovalidate-runtime 0.2.0 while the app pins 0.1.3.
//
// Targets are the ones the Wire artifact publishes (no iosX64/macOS), so this module does not apply
// flipcash.kmp.library. It has an android target, unlike :services:opencode-transport, so its
// vectors run on a device; it must not depend on the transport for that reason.
plugins {
    alias(libs.plugins.kotlin.multiplatform)
    alias(libs.plugins.android.kotlin.multiplatform.library)
    alias(libs.plugins.flipcash.kmp.js)
    alias(libs.plugins.flipcash.kmp.test.fixtures)
}

// The tests read chat-cipher's vectors (canonical copies in the orchestrator's `test-vectors/`)
// next to this module's own, so both are staged into one directory for the fixtures generator.
val stageTestVectors = tasks.register<Sync>("stageTestVectors") {
    from(rootProject.layout.projectDirectory.dir("libs/encryption/chat-cipher/src/commonTest/resources"))
    from(layout.projectDirectory.dir("src/commonTest/resources"))
    into(layout.buildDirectory.dir("stagedTestVectors"))
}

testFixtures {
    packageName = "com.flipcash.chat"
    fixtures.set(layout.dir(stageTestVectors.map { it.destinationDir }))
}

kotlin {
    android {
        namespace = "com.flipcash.chat"
        compileSdk {
            version = release(libs.versions.android.compileSdk.get().toInt()) {
                minorApiLevel = libs.versions.android.compileSdkMinor.get().toInt()
            }
        }
        minSdk = libs.versions.android.minSdk.get().toInt()
        // No host test: libsodium's Android binding loads its .so from the APK and the Ed25519
        // actual is JNI, so commonTest runs as a device test.
        withDeviceTestBuilder {
            sourceSetTreeName = KotlinSourceSetTree.test.name
        }.configure {
            instrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        }
    }
    jvm {
        compilerOptions {
            jvmTarget.set(JvmTarget.fromTarget(libs.versions.android.java.get()))
        }
    }
    js {
        nodejs()
    }
    iosArm64()
    iosSimulatorArm64()

    sourceSets {
        commonMain.dependencies {
            // ChatCipher, KeyPair and InviteBundleResult are part of this module's API.
            api(project(":libs:encryption:chat-cipher"))
            implementation(libs.flipcash2.client.protocol.wire)
        }
        commonTest.dependencies {
            implementation(kotlin("test"))
            implementation(libs.kotlinx.serialization.json)
        }
        getByName("androidDeviceTest").dependencies {
            implementation(libs.androidx.junit)
            implementation(libs.androidx.test.runner)
        }
    }
}
