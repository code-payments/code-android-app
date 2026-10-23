plugins {
    alias(libs.plugins.flipcash.kmp.library)
}

kotlin {
    android {
        namespace = "com.getcode.encryption.utils"
        withHostTest {}
    }

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
