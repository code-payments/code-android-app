plugins {
    id(Plugins.android_library)
    id(Plugins.kotlin_android)
    id(Plugins.kotlin_ksp)
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
        compose = true
    }

    composeOptions {
        kotlinCompilerExtensionVersion = Versions.compose_compiler
    }
}

dependencies {
    implementation(Libs.inject)
    implementation(Libs.hilt)
    ksp(Libs.hilt_android_compiler)
    ksp(Libs.hilt_compiler)

    implementation(Libs.timber)

    implementation(Libs.androidx_browser)

    implementation(Libs.kotlinx_serialization_core)
    implementation(Libs.kotlinx_serialization_json)

    implementation(platform(Libs.compose_bom))
    implementation(Libs.compose_ui)

    implementation(platform(Libs.firebase_bom))
    implementation(Libs.firebase_messaging)
    implementation(Libs.bugsnag)

    api(project(":services:flipcash-compose"))

    implementation(project(":libs:messaging"))
    api(project(":libs:permissions:public"))
    implementation(project(":libs:vibrator:public"))

    implementation(project(":ui:navigation"))
    implementation(project(":ui:theme"))
    implementation(Libs.rinku_compose)

    api(project(":vendor:kik:scanner"))

    api(project(":ui:core"))

    api(project(":vendor:tipkit:tipkit-m2"))
}