plugins {
    alias(libs.plugins.flipcash.kmp.app.library)
}

kotlin {
    android {
        namespace = "${Gradle.codeNamespace}.util.locale"
    }

    sourceSets {
        commonMain.dependencies {
            implementation(project(":libs:datetime"))
            implementation(project(":libs:currency"))
            api(libs.androidx.annotation)
            api(libs.kotlin.stdlib)
            api(libs.kotlinx.coroutines.core)
        }
        androidMain.dependencies {
            implementation(libs.okhttp)
        }
    }
}
