plugins {
    alias(libs.plugins.flipcash.kmp.library)
}

kotlin {
    android {
        namespace = "com.getcode.encryption.sha512"
        withHostTest {}
    }

    sourceSets {
        commonMain {
            dependencies {
                implementation(libs.kotlincrypto.hash.sha2)
                implementation(libs.kotlincrypto.macs.hmac.sha2)
            }
        }
        commonTest {
            dependencies {
                implementation(kotlin("test"))
            }
        }
    }
}
