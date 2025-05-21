import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    id(Plugins.android_library)
    id(Plugins.kotlin_android)
    id(Plugins.kotlin_kapt)
    id(Plugins.kotlin_ksp)
    id(Plugins.kotlin_serialization)
    id(Plugins.hilt)
}

android {
    namespace = "${Android.codeNamespace}.util.vibration"
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
    implementation(project(":libs:vibrator:impl"))
    api(project(":libs:vibrator:public"))

    implementation(Libs.hilt)
    kapt(Libs.hilt_android_compiler)
    kapt(Libs.hilt_compiler)
    ksp(Libs.hilt_android_compiler)
    ksp(Libs.hilt_compiler)
}
