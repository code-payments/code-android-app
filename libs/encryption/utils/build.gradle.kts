plugins {
    kotlin("multiplatform")
    id("com.android.kotlin.multiplatform.library")
}

kotlin {
    android {
        namespace = "com.getcode.encryption.utils"
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
                implementation(project(":libs:encryption:base58"))
            }
        }
        androidMain {
            dependencies {
                implementation(project(":libs:encryption:ed25519"))
                implementation(libs.protobuf.kotlin.lite)
                implementation(libs.bundles.kotlinx.serialization)
                implementation(libs.timber)
            }
        }
        commonTest {
            dependencies {
                implementation(kotlin("test"))
            }
        }
        getByName("androidHostTest") {
            dependencies {
                implementation(kotlin("test"))
                implementation(libs.robolectric)
            }
        }
    }
}
