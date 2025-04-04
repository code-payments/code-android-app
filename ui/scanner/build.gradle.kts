plugins {
    id(Plugins.android_library)
    id(Plugins.kotlin_android)
    id(Plugins.kotlin_serialization)
}

android {
    namespace = "${Android.codeNamespace}.ui.scanner"
    compileSdk = Android.compileSdkVersion
    defaultConfig {
        minSdk = Android.minSdkVersion
        targetSdk = Android.targetSdkVersion
        buildToolsVersion = Android.buildToolsVersion
        testInstrumentationRunner = Android.testInstrumentationRunner
    }

    kotlinOptions {
        jvmTarget = Versions.java
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

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlin {
        jvmToolchain(17)
    }

    buildFeatures {
        buildConfig = true
        compose = true
    }

    composeOptions {
        kotlinCompilerExtensionVersion = Versions.compose_compiler
    }
}

dependencies {
    implementation(project(":libs:logging"))

    implementation(project(":ui:biometrics"))
    implementation(project(":ui:components"))
    implementation(project(":ui:theme"))

    api(project(":vendor:kik:scanner"))

    implementation("com.google.guava:guava:33.4.6-android")

    // cameraX
    implementation(Libs.androidx_camerax_core)
    implementation(Libs.androidx_camerax_camera2)
    implementation(Libs.androidx_camerax_lifecycle)
    implementation(Libs.androidx_camerax_view)

    implementation(Libs.androidx_annotation)
    implementation(Libs.kotlin_stdlib)
    implementation(Libs.kotlinx_coroutines_core)

    implementation(platform(Libs.compose_bom))
    implementation(Libs.compose_ui)
    implementation(Libs.compose_foundation)
    implementation(Libs.compose_animation)
    implementation(Libs.compose_material)

    implementation(Libs.timber)
}