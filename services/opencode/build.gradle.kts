plugins {
    id(Plugins.android_library)
    id(Plugins.kotlin_android)
    id(Plugins.kotlin_ksp)
    id(Plugins.hilt)
    id(Plugins.kotlin_serialization)
}

android {
    namespace = "${Android.codeNamespace}.services.opencode"
    compileSdk = Android.compileSdkVersion
    defaultConfig {
        minSdk = Android.minSdkVersion
        targetSdk = Android.targetSdkVersion
        buildToolsVersion = Android.buildToolsVersion
        testInstrumentationRunner = Android.testInstrumentationRunner

        consumerProguardFiles("consumer-rules.pro")

        buildConfigField("Boolean", "NOTIFY_ERRORS", "false")
        buildConfigField(
            "String",
            "GOOGLE_CLOUD_PROJECT_NUMBER",
            "\"${tryReadProperty(rootProject.rootDir, "GOOGLE_CLOUD_PROJECT_NUMBER", "-1L")}\""
        )
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
        compose = true
    }

    composeOptions {
        kotlinCompilerExtensionVersion = Versions.compose_compiler
    }
}

dependencies {
    implementation(project(":definitions:opencode:models"))
    api(project(":libs:datetime"))
    api(project(":libs:encryption:base58"))
    api(project(":libs:encryption:ed25519"))
    api(project(":libs:encryption:hmac"))
    api(project(":libs:encryption:keys"))
    api(project(":libs:encryption:mnemonic"))
    api(project(":libs:encryption:sha256"))
    api(project(":libs:encryption:sha512"))
    api(project(":libs:encryption:utils"))
    api(project(":libs:crypto:kin"))
    api(project(":libs:crypto:solana"))
    api(project(":libs:logging"))
    api(project(":libs:locale:bindings"))
    api(project(":libs:network:connectivity:public"))
    implementation(project(":ui:resources"))

    api(project(":libs:analytics"))

    api(Libs.sodium_bindings)

    //Jetpack compose
    implementation(platform(Libs.compose_bom))
    implementation(Libs.compose_ui)

    implementation(Libs.inject)

    implementation(Libs.kotlinx_coroutines_core)
    implementation(Libs.kotlinx_serialization_json)
    implementation(Libs.inject)

    implementation(Libs.grpc_android)
    implementation(Libs.grpc_okhttp)
    implementation(Libs.grpc_kotlin)
    implementation(Libs.androidx_lifecycle_runtime)
    implementation(Libs.okhttp)
    implementation(Libs.mixpanel)

    implementation(platform(Libs.firebase_bom))
    implementation(Libs.firebase_crashlytics)
    implementation(Libs.firebase_installations)
    implementation(Libs.firebase_perf)
    implementation(Libs.firebase_messaging)

    implementation(Libs.play_integrity)

    implementation(Libs.androidx_paging_runtime)

    ksp(Libs.androidx_room_compiler)
    implementation(Libs.sqlcipher)

    implementation(Libs.fingerprint_pro)

    implementation(Libs.lib_phone_number_google)

    androidTestImplementation(Libs.androidx_junit)
    androidTestImplementation(Libs.junit)
    androidTestImplementation(Libs.androidx_test_runner)

    implementation(Libs.retrofit)
    implementation(Libs.retrofit_converter)
    implementation(Libs.okhttp_logging_interceptor)

    implementation(Libs.hilt)
    ksp(Libs.hilt_android_compiler)
    ksp(Libs.hilt_compiler)

    implementation(Libs.timber)
    implementation(Libs.bugsnag)
}
