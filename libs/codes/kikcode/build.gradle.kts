plugins {
    alias(libs.plugins.flipcash.kmp.library)
    alias(libs.plugins.flipcash.kmp.test.fixtures)
}

// Compiles `src/commonTest/resources` into a generated `TestFixtures.kt` on `commonTest`, readable
// from every target -- see the `flipcash.kmp.test.fixtures` convention plugin.
testFixtures {
    packageName = "com.getcode.codes.kikcode"
}

kotlin {
    android {
        namespace = "com.getcode.codes.kikcode"
        withHostTest {}
    }

    sourceSets {
        commonMain {
            // Pure Kotlin -- geometry + string building, no platform APIs.
        }
        commonTest {
            dependencies {
                implementation(kotlin("test"))
                implementation(libs.kotlinx.serialization.json)
            }
        }
    }
}
