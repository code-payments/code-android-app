plugins {
    alias(libs.plugins.flipcash.kmp.library)
}

kotlin {
    android {
        namespace = "com.flipcash.reporting"
        withHostTest {}
    }

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
