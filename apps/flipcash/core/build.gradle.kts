import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    id(Plugins.android_library)
    id(Plugins.kotlin_android)
    id(Plugins.kotlin_ksp)
    id(Plugins.kotlin_serialization)
    id(Plugins.kotlin_parcelize)
    id(Plugins.jetbrains_compose_compiler)
}

android {
    namespace = "${Gradle.flipcashNamespace}.core"
    compileSdk = Android.compileSdkVersion
    defaultConfig {
        minSdk = Android.minSdkVersion
        testInstrumentationRunner = Android.testInstrumentationRunner
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
    implementation(Libs.compose_foundation)
    implementation(Libs.compose_material)

    implementation(platform(Libs.firebase_bom))
    implementation(Libs.firebase_messaging)
    implementation(Libs.bugsnag)

    implementation(Libs.androidx_localbroadcastmanager)
    implementation(Libs.androidx_credentials)
    implementation(Libs.androidx_credentials_play_auth)
    implementation(Libs.androidx_datastore)

    api(project(":services:flipcash-compose"))

    implementation(project(":libs:messaging"))
    api(project(":libs:permissions:public"))
    implementation(project(":libs:vibrator:public"))

    api(project(":apps:flipcash:shared:theme"))

    implementation(project(":ui:components"))
    implementation(project(":ui:navigation"))
    implementation(project(":ui:theme"))
    implementation(Libs.rinku_compose)

    api(project(":vendor:kik:scanner"))

    api(project(":ui:core"))

    api(project(":vendor:tipkit:tipkit-m2"))
}