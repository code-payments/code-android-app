import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    id(Plugins.android_library)
    id(Plugins.kotlin_android)
    id(Plugins.kotlin_ksp)
    id(Plugins.hilt)
    id(Plugins.kotlin_parcelize)
    id(Plugins.jetbrains_compose_compiler)
}

android {
    namespace = "${Android.flipcashNamespace}.shared.shareable"
    compileSdk = Android.compileSdkVersion
    defaultConfig {
        minSdk = Android.minSdkVersion
        testInstrumentationRunner = Android.testInstrumentationRunner
    }

    kotlinOptions {
        jvmTarget = JvmTarget.fromTarget(Versions.java).target
        freeCompilerArgs += listOf(
            "-opt-in=kotlin.ExperimentalUnsignedTypes",
            "-opt-in=kotlin.RequiresOptIn"
        )
    }

    java {
        toolchain {
            languageVersion.set(JavaLanguageVersion.of(Versions.java))
        }
    }

    buildFeatures {
        compose = true
    }
}

dependencies {
    implementation(Libs.inject)
    implementation(Libs.hilt)
    ksp(Libs.hilt_android_compiler)
    ksp(Libs.hilt_compiler)

    implementation(platform(Libs.compose_bom))
    implementation(Libs.compose_ui)

    implementation(Libs.androidx_localbroadcastmanager)

    implementation(project(":apps:flipcash:core"))

    implementation(project(":ui:resources"))
    implementation(project(":ui:theme"))
}