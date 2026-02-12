import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    id(Plugins.android_library)
    id(Plugins.kotlin_android)
    id(Plugins.kotlin_ksp)
    id(Plugins.hilt)
    id(Plugins.kotlin_parcelize)
}

android {
    namespace = "${Gradle.flipcashNamespace}.shared.workers"
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
    implementation(Libs.androidx_work)
    implementation(Libs.kotlinx_serialization_json)

    implementation(Libs.inject)
    implementation(Libs.hilt)
    implementation(Libs.hilt_worker)
    ksp(Libs.hilt_android_compiler)
    ksp(Libs.hilt_compiler)

    implementation(project(":apps:flipcash:core"))
    implementation(project(":apps:flipcash:shared:authentication"))
    implementation(project(":apps:flipcash:shared:tokens"))
    implementation(project(":services:flipcash"))

}