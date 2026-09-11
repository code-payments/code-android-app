plugins {
    kotlin("multiplatform")
    id("com.android.kotlin.multiplatform.library")
}

kotlin {
    android {
        namespace = "com.getcode.encryption.sha256"
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
