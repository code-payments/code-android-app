plugins {
    id(Plugins.android_library)
    id(Plugins.kotlin_android)
    id(Plugins.kotlin_serialization)
}

android {
    namespace = "${Android.codeNamespace}.util.locale"
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
}

dependencies {
    implementation(project(":libs:datetime"))
    implementation(project(":libs:currency"))
    api(Libs.androidx_annotation)
    api(Libs.kotlin_stdlib)
    api(Libs.kotlinx_coroutines_core)
    api(Libs.kotlinx_coroutines_rx3)
}
