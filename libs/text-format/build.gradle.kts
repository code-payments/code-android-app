plugins {
    alias(libs.plugins.flipcash.kmp.library)
    alias(libs.plugins.flipcash.kmp.test.fixtures)
}

// Compiles `src/commonTest/resources` into a generated `TestFixtures.kt` on `commonTest`, readable
// from every target -- see the `flipcash.kmp.test.fixtures` convention plugin.
testFixtures {
    packageName = "com.flipcash.libs.textformat"
}

kotlin {
    android {
        namespace = "com.flipcash.libs.textformat"
        compileSdk {
            version = release(libs.versions.android.compileSdk.get().toInt()) {
                minorApiLevel = libs.versions.android.compileSdkMinor.get().toInt()
            }
        }
        minSdk = 29
        withHostTest {}
    }

    iosArm64()
    iosSimulatorArm64()
    iosX64()
    macosArm64()
    macosX64()

    sourceSets {
        commonMain {
            // Pure Kotlin -- no external dependencies needed.
        }
        commonTest {
            dependencies {
                implementation(kotlin("test"))
                implementation(libs.kotlinx.serialization.json)
                // Hashes the copied fixture so a stale copy fails loudly.
                implementation(libs.kotlincrypto.hash.sha2)
            }
        }
    }
}
