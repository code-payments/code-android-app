import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    id(Plugins.android_library)
    id(Plugins.kotlin_android)
    id(Plugins.kotlin_serialization)
    id(Plugins.kotlin_kapt)
    id(Plugins.kotlin_ksp)
    id(Plugins.hilt)
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
    implementation(project(":libs:locale:impl"))
    api(project(":libs:locale:public"))

    implementation(Libs.hilt)
    kapt(Libs.hilt_android_compiler)
    kapt(Libs.hilt_compiler)
    ksp(Libs.hilt_android_compiler)
    ksp(Libs.hilt_compiler)
}
