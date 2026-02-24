import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    id(Plugins.android_library)
    id(Plugins.kotlin_android)
    id(Plugins.kotlin_serialization)
    id(Plugins.jetbrains_compose_compiler)
}

android {
    namespace = "${Gradle.codeNamespace}.ui.scanner"
    compileSdk = Android.compileSdkVersion
    defaultConfig {
        minSdk = Android.minSdkVersion
        testInstrumentationRunner = Android.testInstrumentationRunner
    }

    compileOptions {
        sourceCompatibility = JavaVersion.toVersion(Versions.java)
        targetCompatibility = JavaVersion.toVersion(Versions.java)
    }

    buildFeatures {
        buildConfig = true
        compose = true
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
    implementation(project(":libs:code-detection"))
    implementation(project(":libs:logging"))
    implementation(project(":libs:quickresponse"))

    implementation(project(":ui:biometrics"))
    implementation(project(":ui:components"))
    implementation(project(":ui:theme"))

    api(project(":vendor:kik:scanner"))

    implementation("com.google.guava:guava:33.4.8-android")

    // cameraX
    implementation(Libs.androidx_camerax_core)
    implementation(Libs.androidx_camerax_camera2)
    implementation(Libs.androidx_camerax_lifecycle)
    implementation(Libs.androidx_camerax_view)

    implementation(Libs.androidx_annotation)
    implementation(Libs.kotlin_stdlib)
    implementation(Libs.kotlinx_coroutines_core)

    implementation(platform(Libs.compose_bom))
    implementation(Libs.compose_ui)
    implementation(Libs.compose_foundation)
    implementation(Libs.compose_animation)
    implementation(Libs.compose_material)

    implementation(Libs.timber)
}