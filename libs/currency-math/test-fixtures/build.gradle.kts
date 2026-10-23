plugins {
    alias(libs.plugins.flipcash.kmp.app.library)
}

kotlin {
    android {
        namespace = "${Gradle.codeNamespace}.libs.currency.math.testfixtures"
    }

    sourceSets {
        commonMain.dependencies {
            api(project(":libs:currency-math"))
        }
    }
}
