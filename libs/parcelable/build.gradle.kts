plugins {
    alias(libs.plugins.flipcash.kmp.app.library)
    alias(libs.plugins.flipcash.kmp.apple)
    alias(libs.plugins.flipcash.kmp.js)
}

kotlin {
    // nonJvm: Node and Apple share one set of marker actuals.
    applyDefaultHierarchyTemplate {
        common {
            group("nonJvm") {
                withJs()
                withApple()
            }
        }
    }

    android {
        namespace = "${Gradle.codeNamespace}.libs.parcelable"
    }
}
