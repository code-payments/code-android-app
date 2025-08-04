import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    id(Plugins.android_library)
    id(Plugins.kotlin_android)
    id(Plugins.jetbrains_compose_compiler)
}

android {
    namespace = "com.kik.kikx"
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
        sourceCompatibility = JavaVersion.toVersion(Versions.java)
        targetCompatibility = JavaVersion.toVersion(Versions.java)
    }

    buildFeatures {
        compose = true
    }
}

dependencies {
    api(fileTree(mapOf("dir" to "libs", "include" to listOf("*.jar"))))
    implementation(platform(Libs.compose_bom))
    implementation(Libs.compose_ui)
    implementation(Libs.androidx_camerax_core)
    implementation(Libs.inject)
    implementation(Libs.hilt)
}
