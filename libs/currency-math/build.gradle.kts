import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    id(Plugins.android_library)
    id(Plugins.kotlin_android)
    id(Plugins.kotlin_ksp)
    id(Plugins.kotlin_serialization)
    id(Plugins.kotlin_parcelize)
}

android {
    namespace = "${Gradle.codeNamespace}.libs.currency.math"
    compileSdk = Android.compileSdkVersion
    defaultConfig {
        minSdk = Android.minSdkVersion
        testInstrumentationRunner = Android.testInstrumentationRunner
    }

    compileOptions {
        sourceCompatibility(Versions.java)
        targetCompatibility(Versions.java)
    }

    java {
        toolchain {
            languageVersion.set(JavaLanguageVersion.of(Versions.java))
        }
    }

    kotlinOptions {
        jvmTarget = JvmTarget.fromTarget(Versions.java).target
        freeCompilerArgs += listOf(
            "-opt-in=kotlin.ExperimentalUnsignedTypes",
            "-opt-in=kotlin.RequiresOptIn"
        )
    }
}

dependencies {
    androidTestImplementation(Libs.androidx_junit)
    androidTestImplementation(Libs.junit)
    androidTestImplementation(Libs.androidx_test_runner)

    implementation(Libs.inject)
    implementation(Libs.hilt)
    implementation(Libs.timber)
    implementation(Libs.bugsnag)

    testImplementation(kotlin("test"))
}
