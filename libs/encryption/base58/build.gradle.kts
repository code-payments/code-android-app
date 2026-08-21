plugins {
    kotlin("multiplatform")
    id("com.android.kotlin.multiplatform.library")
    alias(libs.plugins.flipcash.kmp.test.fixtures)
}

// Compiles `src/commonTest/resources` into a generated `TestFixtures.kt` on `commonTest`, readable
// from every target -- see the `flipcash.kmp.test.fixtures` convention plugin.
testFixtures {
    packageName = "com.getcode.vendor"
}

kotlin {
    android {
        namespace = "com.getcode.encryption.base58"
        compileSdk = 37
        minSdk = 29
        withHostTest {}
    }

    iosArm64()
    iosSimulatorArm64()
    iosX64()

    sourceSets {
        commonMain {
            // Pure Kotlin -- no external dependencies needed.
        }
        androidMain {
            // MessageDigest + BigInteger -- JDK only; no extra Gradle deps.
        }
        commonTest {
            dependencies {
                implementation(kotlin("test"))
                implementation(libs.kotlinx.serialization.json)
            }
        }
    }
}
