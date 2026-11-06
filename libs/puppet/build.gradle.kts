plugins {
    alias(libs.plugins.flipcash.kmp.app.library)
}

// Headless introspection for view models (Puppet). Only the desktop host depends on this; Android
// production code must not.
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
