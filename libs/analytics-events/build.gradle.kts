plugins {
    alias(libs.plugins.flipcash.kmp.library)
    // Generates the builders and value enums from events.toml into commonMain.
    alias(libs.plugins.flipcash.analytics.catalogue)
}

kotlin {
    android {
        namespace = "com.flipcash.analytics"
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
