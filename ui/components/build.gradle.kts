plugins {
    id(Plugins.android_library)
    id(Plugins.kotlin_android)
}

android {
    namespace = "com.getcode.ui.components"
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
            "-Xuse-experimental=kotlinx.coroutines.ExperimentalCoroutinesApi",
            "-Xuse-experimental=kotlinx.coroutines.FlowPreview",
            "-opt-in=kotlin.ExperimentalUnsignedTypes",
            "-opt-in=kotlin.RequiresOptIn"
        )
    }

    java {
        toolchain {
            languageVersion.set(JavaLanguageVersion.of(Versions.java))
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlin {
        jvmToolchain(17)
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
    implementation(project(":libs:api"))
    implementation(project(":libs:datetime"))
    implementation(project(":libs:crypto:ed25519"))
    implementation(project(":libs:currency"))
    implementation(project(":libs:network:exchange"))
    implementation(project(":libs:requests"))
    implementation(project(":ui:theme"))
    implementation(project(":ui:resources"))

    implementation(Libs.coil3)
    implementation(Libs.coil3_network)

    implementation(Libs.kotlinx_datetime)

    implementation(platform(Libs.compose_bom))
    implementation(Libs.compose_ui)
    implementation(Libs.compose_activities)
    debugImplementation(Libs.compose_ui_tools)
    implementation(Libs.compose_ui_tools_preview)
    implementation(Libs.compose_material)
    implementation(Libs.compose_materialIconsExtended)
    implementation(Libs.compose_accompanist)
    implementation(Libs.compose_paging)
    implementation(Libs.timber)
}