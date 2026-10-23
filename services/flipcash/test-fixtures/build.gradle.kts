plugins {
    alias(libs.plugins.flipcash.kmp.app.library)
}

kotlin {
    android {
        namespace = "${Gradle.codeNamespace}.services.flipcash.testfixtures"
    }

    sourceSets {
        commonMain.dependencies {
            api(project(":services:flipcash"))
            implementation(project(":libs:encryption:chat-cipher"))
        }
    }
}
