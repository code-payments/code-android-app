import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    id(Plugins.android_library)
    id(Plugins.kotlin_android)
    id(Plugins.kotlin_ksp)
    id(Plugins.hilt)
    id(Plugins.kotlin_parcelize)
    id(Plugins.kotlin_serialization)
    id(Plugins.jetbrains_compose_compiler)
}

android {
    namespace = "${Gradle.flipcashNamespace}.features.onramp"
    compileSdk = Android.compileSdkVersion
    defaultConfig {
        minSdk = Android.minSdkVersion
        testInstrumentationRunner = Android.testInstrumentationRunner

        buildConfigField("String", "COINBASE_ONRAMP_API_KEY", "\"${tryReadProperty(rootProject.rootDir, "COINBASE_ONRAMP_API_KEY")}\"")
    }

    kotlinOptions {
        jvmTarget = JvmTarget.fromTarget(Versions.java).target
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
}

dependencies {
    implementation(Libs.inject)
    implementation(Libs.hilt)
    ksp(Libs.hilt_android_compiler)
    ksp(Libs.hilt_compiler)

    implementation(platform(Libs.compose_bom))
    implementation(Libs.compose_activities)
    implementation(Libs.compose_ui)
    implementation(Libs.compose_foundation)
    implementation(Libs.compose_material)
    implementation(Libs.compose_webview)

    implementation(Libs.androidx_localbroadcastmanager)

    implementation(Libs.kotlinx_serialization_core)
    implementation(Libs.kotlinx_serialization_json)

    implementation(project(":apps:flipcash:core"))
    implementation(project(":apps:flipcash:shared:coinbase-onramp"))
    implementation(project(":apps:flipcash:shared:router"))

    implementation(project(":libs:datetime"))
    implementation(project(":libs:logging"))
    implementation(project(":libs:messaging"))
    implementation(project(":ui:analytics"))
    implementation(project(":ui:core"))
    implementation(project(":ui:components"))
    implementation(project(":ui:navigation"))
    implementation(project(":ui:resources"))
    implementation(project(":ui:theme"))
}