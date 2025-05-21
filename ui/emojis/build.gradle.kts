import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    id(Plugins.android_library)
    id(Plugins.kotlin_android)
    id(Plugins.kotlin_serialization)
    id(Plugins.jetbrains_compose_compiler)
}

android {
    namespace = "${Android.codeNamespace}.ui.emojis"
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

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    buildFeatures {
        buildConfig = true
        compose = true
    }
}

dependencies {
    api(project(":libs:emojis"))
    implementation(project(":libs:search"))

    implementation(project(":ui:core"))
    implementation(project(":ui:theme"))
    implementation(project(":ui:resources"))

    implementation("androidx.emoji2:emoji2:1.5.0")
    // Optional: Include a bundled emoji font (offline support)
    implementation("androidx.emoji2:emoji2-bundled:1.5.0")

    api(Libs.androidx_annotation)
    api(Libs.kotlin_stdlib)
    api(Libs.kotlinx_coroutines_core)

    implementation(Libs.compose_ui_tools_preview)

    implementation(platform(Libs.compose_bom))
    implementation(Libs.compose_ui)
    implementation(Libs.compose_foundation)
    implementation(Libs.compose_animation)
    implementation(Libs.compose_material)
    implementation(Libs.compose_materialIconsExtended)

    implementation(Libs.compose_voyager_navigation)
}