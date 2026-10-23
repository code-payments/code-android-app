plugins {
    alias(libs.plugins.flipcash.kmp.app.library)
}

kotlin {
    android {
        namespace = "${Gradle.codeNamespace}.libs.coroutines.testfixtures"
    }

    sourceSets {
        commonMain.dependencies {
            api(project(":libs:coroutines"))
            implementation(libs.kotlinx.coroutines.test)
        }
    }
}
