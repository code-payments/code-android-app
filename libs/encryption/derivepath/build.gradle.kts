plugins {
    kotlin("multiplatform")
    id("com.android.kotlin.multiplatform.library")
}

kotlin {
    android {
        namespace = "com.getcode.encryption.derivepath"
        compileSdk = 37
        minSdk = 29
        withHostTest {}
    }

    iosArm64()
    iosSimulatorArm64()
    iosX64()

    sourceSets {
        commonMain {
            // Pure Kotlin — no external dependencies needed.
        }
        commonTest {
            dependencies {
                implementation(kotlin("test"))
            }
        }
    }
}
