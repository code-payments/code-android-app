plugins {
    kotlin("multiplatform")
    id("com.android.kotlin.multiplatform.library")
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
