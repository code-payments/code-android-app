plugins {
    id(Plugins.android_library)
    id(Plugins.kotlin_android)
}

android {
    namespace = "com.getcode.theme"
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
            "-Xuse-experimental=kotlinx.coroutines.ExperimentalCoroutinesApi",
            "-Xuse-experimental=kotlinx.coroutines.FlowPreview",
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
    implementation(platform(Libs.compose_bom))
    implementation(Libs.compose_ui)
    debugImplementation(Libs.compose_ui_tools)
    implementation(Libs.compose_ui_tools_preview)
    implementation(Libs.compose_material)
    implementation(Libs.compose_accompanist)

    implementation(Libs.timber)

    implementation(Libs.androidx_appcompat)
    implementation(Libs.androidx_core)
    implementation(Libs.androidx_activity)
    implementation(Libs.androidx_navigation_fragment)
}
