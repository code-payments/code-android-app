plugins {
    alias(libs.plugins.flipcash.kmp.app.library)
}

// Headless introspection for view models (Puppet). Only test code depends on this (chat-list's
// jvmTest replays test-vectors/behaviors/ through it); Android production code must not.
kotlin {
    android {
        namespace = "${Gradle.codeNamespace}.libs.puppet"
    }

    sourceSets {
        commonMain.dependencies {
            api(libs.kotlinx.serialization.json)
        }
        commonTest.dependencies {
            implementation(kotlin("test"))
            implementation(libs.kotlinx.coroutines.test)
        }
    }
}
