plugins {
    alias(libs.plugins.flipcash.kmp.library)
}

kotlin {
    android {
        namespace = "com.getcode.encryption.hmac"
        withHostTest {}
    }

    sourceSets {
        commonMain {
            dependencies {
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
