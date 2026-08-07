plugins {
    kotlin("multiplatform")
    id("com.android.kotlin.multiplatform.library")
}

kotlin {
    android {
        namespace = "com.getcode.encryption.sha256"
        compileSdk = 37
        minSdk = 29
        withHostTest {}
    }

    iosArm64()
    iosSimulatorArm64()
    iosX64()

    sourceSets {
        commonMain {
            dependencies {
                implementation(libs.kotlincrypto.hash.sha2)
            }
        }
        androidMain {
            // MessageDigest + BigInteger + File — JDK only; no extra Gradle deps.
        }
        commonTest {
            dependencies {
                implementation(kotlin("test"))
            }
        }
    }
}
