plugins {
    alias(libs.plugins.flipcash.kmp.app.library)
}

kotlin {
    android {
        namespace = "${Gradle.flipcashNamespace}.shared.analytics.testfixtures"
    }

    sourceSets {
        commonMain.dependencies {
            api(project(":apps:flipcash:shared:analytics"))
        }
    }
}
