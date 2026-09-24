plugins {
    kotlin("multiplatform")
    id("com.android.kotlin.multiplatform.library")
    // Generates the builders and value enums from events.toml into commonMain.
    alias(libs.plugins.flipcash.analytics.catalogue)
}

kotlin {
    android {
        namespace = "com.flipcash.analytics"
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
            }
        }
    }
}
