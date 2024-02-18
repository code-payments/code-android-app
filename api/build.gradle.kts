import com.android.build.gradle.internal.cxx.configure.gradleLocalProperties
import org.jetbrains.kotlin.cli.common.toBooleanLenient
import java.util.Properties

plugins {
    id(Plugins.android_library)
    id(Plugins.kotlin_android)
    id(Plugins.kotlin_kapt)
    id(Plugins.kotlin_serialization)
}

android {
    namespace = "${Android.namespace}.api"
    compileSdk = Android.compileSdkVersion
    defaultConfig {
        minSdk = Android.minSdkVersion
        targetSdk = Android.targetSdkVersion
        buildToolsVersion = Android.buildToolsVersion
        testInstrumentationRunner = Android.testInstrumentationRunner

        buildConfigField("Boolean", "NOTIFY_ERRORS", "false")

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
                (gradleLocalProperties(rootProject.rootDir).getProperty("NOTIFY_ERRORS")
                    .toBooleanLenient()
                    ?: (System.getenv("NOTIFY_ERRORS").toBooleanLenient()
                        ?: false)
                        ).toString()
            )
        }
    }

    java {
        toolchain {
            languageVersion.set(JavaLanguageVersion.of(17))
        }
    }

    kotlinOptions {
        jvmTarget = Versions.java
        freeCompilerArgs += listOf(
            "-Xuse-experimental=kotlinx.coroutines.ExperimentalCoroutinesApi",
            "-Xuse-experimental=kotlinx.coroutines.FlowPreview",
            "-opt-in=kotlin.RequiresOptIn"
        )
    }

    buildFeatures {
        buildConfig = true
    }
}

dependencies {
    implementation(project(":model"))
    implementation(project(":ed25519"))

    implementation(Libs.kotlin_stdlib)
    implementation(Libs.rxjava)
    api(Libs.kotlinx_coroutines_core)
    implementation(Libs.kotlinx_coroutines_core)
    api(Libs.kotlinx_coroutines_rx3)
    implementation(Libs.kotlinx_serialization_json)
    implementation(Libs.inject)

    implementation(Libs.grpc_okhttp)
    implementation(Libs.grpc_kotlin)
    implementation(Libs.androidx_room_runtime)
    implementation(Libs.androidx_room_ktx)
    implementation(Libs.androidx_room_rxjava3)
    implementation(Libs.okhttp)
    implementation(Libs.mixpanel)

    implementation(platform(Libs.firebase_bom))
    implementation(Libs.firebase_appcheck)
    implementation(Libs.firebase_appcheck_debug)
    implementation(Libs.firebase_appcheck_playintegrity)

    implementation(Libs.androidx_paging_runtime)

    kapt(Libs.androidx_room_compiler)
    implementation(Libs.sqlcipher)

    implementation(Libs.lib_phone_number_google)

    implementation(platform(Libs.firebase_bom))
    implementation(Libs.firebase_crashlytics)
    implementation(Libs.firebase_perf)
    implementation(Libs.firebase_messaging)

    androidTestImplementation(Libs.androidx_junit)
    androidTestImplementation(Libs.junit)
    androidTestImplementation(Libs.androidx_test_runner)
    implementation(Libs.hilt)

    implementation(Libs.kin_sdk)

    implementation(Libs.timber)
    implementation(Libs.bugsnag)
}
