plugins {
    kotlin("multiplatform")
    id("com.android.kotlin.multiplatform.library")
    alias(libs.plugins.flipcash.kmp.test.fixtures)
}

// Compiles `src/commonTest/resources` into a generated `TestFixtures.kt` on `commonTest` --
// see the `flipcash.kmp.test.fixtures` convention plugin.
testFixtures {
    packageName = "com.flipcash.libs.currency.math.curve"
}

kotlin {
    android {
        namespace = "com.flipcash.libs.currency.math.curve"
        compileSdk = 37
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
                implementation(libs.ionspin.bignum)
            }
        }
        commonTest {
            dependencies {
                implementation(kotlin("test"))
                implementation(libs.kotlinx.serialization.json)
            }
        }
    }
}
