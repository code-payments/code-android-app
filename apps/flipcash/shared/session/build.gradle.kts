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
    namespace = "${Android.flipcashNamespace}.shared.session"
    compileSdk = Android.compileSdkVersion
    defaultConfig {
        minSdk = Android.minSdkVersion
        targetSdk = Android.targetSdkVersion
        buildToolsVersion = Android.buildToolsVersion
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

    implementation(project(":apps:flipcash:shared:activityfeed"))
    implementation(project(":apps:flipcash:shared:shareable"))
    implementation(project(":apps:flipcash:core"))
    implementation(project(":services:flipcash"))
    implementation(project(":libs:datetime"))
    implementation(project(":libs:logging"))
    implementation(project(":libs:messaging"))
    implementation(project(":libs:vibrator:bindings"))
}