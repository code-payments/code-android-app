import kotlinx.serialization.Serializable
import java.net.URL
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

buildscript {
    repositories {
        mavenCentral()
    }
    dependencies {
        classpath(Libs.kotlinx_serialization_json)
    }
}

plugins {
    id(Plugins.android_library)
    id(Plugins.kotlin_android)
    id(Plugins.kotlin_kapt)
    id(Plugins.kotlin_serialization)
}

android {
    namespace = "${Android.flipcashNamespace}.core"
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

    buildFeatures {
        buildConfig = true
    }
}

dependencies {
    implementation(Libs.inject)
    implementation(Libs.hilt)
    implementation(Libs.timber)

    implementation(Libs.androidx_browser)

    implementation(platform(Libs.compose_bom))
    implementation(Libs.compose_ui)

    implementation(platform(Libs.firebase_bom))
    implementation(Libs.firebase_messaging)
    implementation(Libs.bugsnag)

    implementation(project(":services:flipcash"))

    implementation(project(":ui:navigation"))
    implementation(project(":ui:theme"))
    implementation(Libs.rinku_compose)

    api(project(":ui:core"))
}