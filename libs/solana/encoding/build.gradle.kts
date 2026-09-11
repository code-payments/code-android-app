plugins {
    kotlin("multiplatform")
    id("com.android.kotlin.multiplatform.library")
}

kotlin {
    android {
        namespace = "${Gradle.codeNamespace}.opencode.solana.encoding"
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
                implementation(project(":libs:encryption:keys"))
                implementation(project(":libs:encryption:utils"))
                implementation(project(":libs:encryption:ed25519"))
                implementation(project(":libs:encryption:sha256"))
            }
        }
        commonTest {
            dependencies {
                implementation(kotlin("test"))
            }
        }
    }
}
