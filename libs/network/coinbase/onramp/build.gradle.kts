import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    id(Plugins.android_library)
    id(Plugins.kotlin_android)
    id(Plugins.kotlin_ksp)
    id(Plugins.kotlin_serialization)
    id(Plugins.hilt)
}

android {
    namespace = "${Gradle.codeNamespace}.libs.coinbase.onramp"
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
    implementation(Libs.kotlinx_datetime)

    implementation(Libs.retrofit)
    implementation(Libs.retrofit_converter_serialization)
    implementation(Libs.okhttp_logging_interceptor)
    implementation(Libs.kotlinx_serialization_core)
    implementation(Libs.kotlinx_serialization_json)

    implementation(Libs.timber)

    implementation(Libs.inject)
    implementation(Libs.hilt)
    ksp(Libs.hilt_android_compiler)
    ksp(Libs.hilt_compiler)
}
