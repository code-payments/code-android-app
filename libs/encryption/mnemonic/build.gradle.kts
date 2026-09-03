plugins {
    kotlin("multiplatform")
    id("com.android.kotlin.multiplatform.library")
    alias(libs.plugins.flipcash.kmp.test.fixtures)
}

// Compiles `src/commonTest/resources` into a generated `TestFixtures.kt` on `commonTest` --
// see the `flipcash.kmp.test.fixtures` convention plugin.
testFixtures {
    packageName = "com.getcode.crypt"
}

kotlin {
    android {
        namespace = "com.getcode.encryption.mnemonic"
        compileSdk = 37
        minSdk = 29
        withHostTest {}
        withDeviceTest {}
    }

    iosArm64()
    iosSimulatorArm64()
    iosX64()

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
        androidMain {
            dependencies {
                // Legacy vendored Base58 codec (fromEntropyB58/getBase58EncodedEntropy).
                implementation(project(":libs:encryption:base58"))
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
