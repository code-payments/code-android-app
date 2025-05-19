import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    id(Plugins.android_library)
    id(Plugins.kotlin_android)
    id(Plugins.kotlin_serialization)
    id(Plugins.jetbrains_compose_compiler)
}

android {
    namespace = "dev.bmcreations.tipkit"
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

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    buildFeatures {
        compose = true
    }
}

dependencies {
    implementation(platform(Libs.compose_bom))
    implementation(Libs.compose_animation)
    implementation(Libs.compose_foundation)
    implementation(Libs.compose_ui_graphics)
    implementation(Libs.compose_animation)
    implementation(Libs.compose_ui_tools_preview)
    implementation(Libs.kotlinx_serialization_core)
    implementation(Libs.kotlinx_serialization_json)
    implementation(Libs.kotlinx_datetime)
    implementation(Libs.androidx_datastore)
}
