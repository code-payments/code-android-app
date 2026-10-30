plugins {
    alias(libs.plugins.flipcash.kmp.library)
}

kotlin {
    android {
        namespace = "com.getcode.encryption.sha256"
        withHostTest {}
    }

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
