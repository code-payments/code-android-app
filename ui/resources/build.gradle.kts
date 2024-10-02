plugins {
    id(Plugins.android_library)
    id(Plugins.kotlin_android)
    id(Plugins.kotlin_serialization)
}

android {
    namespace = "${Android.codeNamespace}.util.resources"
    compileSdk = Android.compileSdkVersion
    defaultConfig {
        minSdk = Android.minSdkVersion
        targetSdk = Android.targetSdkVersion
        buildToolsVersion = Android.buildToolsVersion
        testInstrumentationRunner = Android.testInstrumentationRunner
    }

    buildFeatures {
        compose = true
    }

    composeOptions {
        kotlinCompilerExtensionVersion = Versions.compose_compiler
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
}

dependencies {
    api(Libs.androidx_annotation)
    api(Libs.androidx_appcompat)
    api(Libs.androidx_core)
    api(Libs.kotlin_stdlib)
    api(Libs.kotlinx_coroutines_core)
    api(Libs.kotlinx_coroutines_rx3)

    implementation(platform(Libs.compose_bom))
    implementation(Libs.compose_ui)
    implementation(Libs.compose_foundation)

    implementation(Libs.timber)
}
