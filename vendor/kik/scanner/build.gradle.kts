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
        ndkVersion = "28.1.13356709"
        externalNativeBuild {
            cmake {
                cppFlags += "-std=c++11"
                cppFlags += listOf(
                    "-O3",
                    "-ffast-math",
                    "-funsafe-math-optimizations",
                    "-funroll-loops",
                )
            }
        }
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

    externalNativeBuild {
        cmake {
            path = file("CMakeLists.txt")
        }
    }
}

dependencies {
    implementation(platform(Libs.compose_bom))
    implementation(Libs.compose_ui)
    implementation(Libs.androidx_camerax_core)
    implementation(Libs.inject)
    implementation(Libs.hilt)

    implementation(project(":libs:encryption:ed25519"))
    implementation(project(":vendor:opencv:sdk"))
}
