import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    id(Plugins.android_library)
    id(Plugins.kotlin_android)
    id(Plugins.kotlin_kapt)
    id(Plugins.kotlin_serialization)
    id(Plugins.jetbrains_compose_compiler)
}

android {
    namespace = "${Gradle.codeNamespace}.libs.opengraph"
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

    buildFeatures {
        compose = true
    }

    kotlin {
        jvmToolchain(Versions.java.toInt())
        compilerOptions {
            optIn.addAll(
                listOf(
                    "kotlin.RequiresOptIn",
                    "kotlin.ExperimentalUnsignedTypes",
                    "kotlin.time.ExperimentalTime"
                )
            )
        }
    }
}

dependencies {
    //Jetpack compose
    implementation(platform(Libs.compose_bom))
    implementation(Libs.compose_ui)

    implementation("org.jsoup:jsoup:1.16.1")
    implementation(project(":libs:encryption:utils"))

    implementation(Libs.kotlinx_coroutines_core)
    implementation(Libs.kotlinx_serialization_json)
    implementation(Libs.inject)

    implementation(Libs.androidx_datastore)

    implementation(Libs.hilt)

    implementation(Libs.timber)
    implementation(Libs.bugsnag)
}
