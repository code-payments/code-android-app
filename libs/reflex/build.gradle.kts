plugins {
    alias(libs.plugins.flipcash.kmp.app.library)
}

// Headless introspection for view models (Reflex). Only the desktop host depends on this; Android
// production code must not.
kotlin {
    android {
        namespace = "${Gradle.codeNamespace}.libs.reflex"
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
