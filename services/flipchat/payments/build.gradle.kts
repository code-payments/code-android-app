plugins {
    id(Plugins.android_library)
    id(Plugins.kotlin_android)
    id(Plugins.kotlin_kapt)
    id(Plugins.kotlin_serialization)
}

android {
    namespace = "${Android.flipchatNamespace}.services.payments"
    compileSdk = Android.compileSdkVersion
    defaultConfig {
        minSdk = Android.minSdkVersion
        targetSdk = Android.targetSdkVersion
        buildToolsVersion = Android.buildToolsVersion
        testInstrumentationRunner = Android.testInstrumentationRunner

        buildConfigField("String", "VERSION_NAME", "\"${Packaging.Flipchat.versionName}\"")

        javaCompileOptions {
            annotationProcessorOptions {
                arguments += mapOf("room.schemaLocation" to "$projectDir/schemas")
            }
        }
    }

    java {
        toolchain {
            languageVersion.set(JavaLanguageVersion.of(Versions.java))
        }
    }

    kotlinOptions {
        jvmTarget = Versions.java
        freeCompilerArgs += listOf(
            "-opt-in=kotlin.ExperimentalUnsignedTypes",
            "-opt-in=kotlin.RequiresOptIn"
        )
    }

    buildFeatures {
        buildConfig = true
    }
}

dependencies {
    implementation(project(":definitions:code-vm:models"))
    implementation(project(":services:flipchat:core"))
    api(project(":services:shared"))
    implementation(project(":ui:resources"))

    implementation(project(":libs:messaging"))
    implementation(project(":libs:requests"))

    implementation(platform(Libs.compose_bom))
    implementation(Libs.compose_ui)

    implementation(Libs.kotlinx_coroutines_core)
    implementation(Libs.kotlinx_serialization_json)
    implementation(Libs.inject)

    implementation(Libs.grpc_android)
    implementation(Libs.grpc_okhttp)
    implementation(Libs.grpc_kotlin)
    implementation(Libs.androidx_lifecycle_runtime)
    implementation(Libs.androidx_room_runtime)
    implementation(Libs.androidx_room_ktx)
    implementation(Libs.androidx_room_paging)
    implementation(Libs.androidx_room_rxjava3)
    implementation(Libs.okhttp)
    implementation(Libs.mixpanel)

    implementation(platform(Libs.firebase_bom))
    implementation(Libs.firebase_crashlytics)
    implementation(Libs.firebase_installations)
    implementation(Libs.firebase_perf)
    implementation(Libs.firebase_messaging)

    implementation(Libs.play_integrity)

    implementation(Libs.androidx_paging_runtime)

    kapt(Libs.androidx_room_compiler)
    implementation(Libs.sqlcipher)

    implementation(Libs.fingerprint_pro)

    implementation(Libs.lib_phone_number_google)

    androidTestImplementation(Libs.androidx_junit)
    androidTestImplementation(Libs.junit)
    androidTestImplementation(Libs.androidx_test_runner)

    implementation(Libs.hilt)
    kapt(Libs.hilt_android_compiler)
    kapt(Libs.hilt_compiler)

    implementation(Libs.timber)
    implementation(Libs.bugsnag)
}
