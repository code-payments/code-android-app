plugins {
    alias(libs.plugins.flipcash.kmp.library)
    alias(libs.plugins.flipcash.kmp.test.fixtures)
}

// Compiles `src/commonTest/resources` into a generated `TestFixtures.kt` on `commonTest`, readable
// from every target -- see the `flipcash.kmp.test.fixtures` convention plugin.
testFixtures {
    packageName = "com.getcode.vendor"
}

kotlin {
    android {
        namespace = "com.getcode.encryption.base58"
        withHostTest {}
    }

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
