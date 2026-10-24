plugins {
    alias(libs.plugins.flipcash.kmp.app.library)
}

// Test-only: the startup wiring both the Android and desktop graphs are checked against.
kotlin {
    android {
        namespace = "${Gradle.flipcashNamespace}.startup.contract"
    }
}
