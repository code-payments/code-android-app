plugins {
    id(Plugins.android_library)
    id(Plugins.kotlin_android)
    id(Plugins.kotlin_kapt)
    id(Plugins.kotlin_serialization)
}

android {
    namespace = "${Android.codeNamespace}.api"
    compileSdk = Android.compileSdkVersion
    defaultConfig {
        minSdk = Android.minSdkVersion
        targetSdk = Android.targetSdkVersion
        buildToolsVersion = Android.buildToolsVersion
        testInstrumentationRunner = Android.testInstrumentationRunner

        buildConfigField("Boolean", "NOTIFY_ERRORS", "false")
        buildConfigField(
            "String",
            "GOOGLE_CLOUD_PROJECT_NUMBER",
            "\"${tryReadProperty(rootProject.rootDir, "GOOGLE_CLOUD_PROJECT_NUMBER", "-1L")}\""
        )

        buildConfigField(
            "String",
            "FINGERPRINT_API_KEY",
            "\"${tryReadProperty(rootProject.rootDir, "FINGERPRINT_API_KEY")}\""
        )

        javaCompileOptions {
            annotationProcessorOptions {
                arguments += mapOf("room.schemaLocation" to "$projectDir/schemas")
            }
        }
    }

    buildTypes {
        getByName("release") {
            buildConfigField("Boolean", "NOTIFY_ERRORS", "true")
        }
        getByName("debug") {
            buildConfigField(
                "Boolean",
                "NOTIFY_ERRORS",
                tryReadProperty(rootProject.rootDir, "NOTIFY_ERRORS", "false")
            )
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
            "-Xuse-experimental=kotlinx.coroutines.ExperimentalCoroutinesApi",
            "-Xuse-experimental=kotlinx.coroutines.FlowPreview",
            "-opt-in=kotlin.ExperimentalUnsignedTypes",
            "-opt-in=kotlin.RequiresOptIn"
        )
    }

    buildFeatures {
        buildConfig = true
    }
}

dependencies {
    implementation(project(":common:resources"))
    api(project(":service:models"))
    implementation(project(":crypto:ed25519"))
    implementation(project(":crypto:kin"))

    implementation(Libs.rxjava)
    implementation(Libs.kotlinx_coroutines_core)
    implementation(Libs.kotlinx_serialization_json)
    implementation(Libs.kotlinx_datetime)
    implementation(Libs.inject)

    implementation(Libs.grpc_okhttp)
    implementation(Libs.grpc_kotlin)
    implementation(Libs.androidx_lifecycle_runtime)
    implementation(Libs.androidx_room_runtime)
    implementation(Libs.androidx_room_ktx)
    implementation(Libs.androidx_room_rxjava3)
    implementation(Libs.androidx_room_paging)
    implementation(Libs.okhttp)
    implementation(Libs.mixpanel)

    implementation(platform(Libs.firebase_bom))
    implementation(Libs.play_integrity)

    implementation(Libs.androidx_paging_runtime)

    kapt(Libs.androidx_room_compiler)
    implementation(Libs.sqlcipher)

    api(Libs.sodium_bindings)

    implementation(Libs.fingerprint_pro)

    implementation(Libs.lib_phone_number_google)

    implementation(platform(Libs.firebase_bom))
    implementation(Libs.firebase_crashlytics)
    implementation(Libs.firebase_installations)
    implementation(Libs.firebase_perf)
    implementation(Libs.firebase_messaging)

    androidTestImplementation(Libs.androidx_junit)
    androidTestImplementation(Libs.junit)
    androidTestImplementation(Libs.androidx_test_runner)
    implementation(Libs.hilt)

    implementation(Libs.timber)
    implementation(Libs.bugsnag)
}
