import org.jetbrains.kotlin.gradle.plugin.KotlinPlatformType

plugins {
    alias(libs.plugins.flipcash.kmp.library)
    alias(libs.plugins.flipcash.kmp.test.fixtures)
    alias(libs.plugins.flipcash.kmp.js)
}

// Compiles `src/commonTest/resources` into a generated `TestFixtures.kt` on `commonTest` --
// see the `flipcash.kmp.test.fixtures` convention plugin.
testFixtures {
    packageName = "com.getcode.crypt"
}

@OptIn(org.jetbrains.kotlin.gradle.ExperimentalKotlinGradlePluginApi::class)
kotlin {
    // jvmAndJs: key and phrase types for Android, the desktop JVM and Node. They need base64 and the
    // string-keyed Ed25519 API, which have no iOS actuals yet.
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
        namespace = "com.getcode.encryption.mnemonic"
        withHostTest {}
        // Packages src/androidDeviceTest/assets (slip10.json) into the device-test APK. The module
        // has no res/, so the library itself gains nothing but an empty R class.
        androidResources { enable = true }
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
        getByName("jvmAndJsMain") {
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
        // MnemonicPhraseVectorTest: the full phrase -> keypair path on the JVM and Node. Android runs
        // the same check as a device test, since the host can't run android.util.Base64 or JNI Ed25519.
        jvmTest { kotlin.srcDir("src/phraseVectorTest/kotlin") }
        jsTest { kotlin.srcDir("src/phraseVectorTest/kotlin") }
        getByName("androidDeviceTest") {
            dependencies {
                // Cross-platform derivation test-vector gate (instrumented: JNI ed25519 pipeline).
                implementation(libs.androidx.junit)
                implementation(libs.androidx.test.runner)
            }
        }
    }
}
