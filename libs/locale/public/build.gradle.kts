import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    id(Plugins.android_library)
    id(Plugins.kotlin_android)
    id(Plugins.kotlin_serialization)
}

android {
    namespace = "${Gradle.codeNamespace}.util.locale"
    compileSdk = Android.compileSdkVersion
    defaultConfig {
        minSdk = Android.minSdkVersion
        testInstrumentationRunner = Android.testInstrumentationRunner
    }
}

kotlin {
    jvmToolchain {
        languageVersion.set(JavaLanguageVersion.of(Versions.java))
    }

    compilerOptions {
        jvmTarget.set(JvmTarget.fromTarget(Versions.java))
        optIn.addAll(
            "kotlin.time.ExperimentalTime",
            "kotlin.ExperimentalUnsignedTypes",
            "kotlin.RequiresOptIn"
        )
    }
}

dependencies {
    implementation(project(":libs:datetime"))
    implementation(project(":libs:currency"))
    implementation(Libs.okhttp)
    api(Libs.androidx_annotation)
    api(Libs.kotlin_stdlib)
    api(Libs.kotlinx_coroutines_core)
    api(Libs.kotlinx_coroutines_rx3)
}
