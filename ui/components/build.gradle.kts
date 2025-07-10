import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    id(Plugins.android_library)
    id(Plugins.kotlin_android)
    id(Plugins.kotlin_parcelize)
    id(Plugins.jetbrains_compose_compiler)
}

android {
    namespace = "com.getcode.ui.components"
    compileSdk = Android.compileSdkVersion
    defaultConfig {
        minSdk = Android.minSdkVersion
        testInstrumentationRunner = Android.testInstrumentationRunner
    }

    kotlinOptions {
        jvmTarget = JvmTarget.fromTarget(Versions.java).target
        freeCompilerArgs += listOf(
            "-opt-in=kotlin.ExperimentalUnsignedTypes",
            "-opt-in=kotlin.RequiresOptIn"
        )
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    buildFeatures {
        buildConfig = true
        compose = true
    }
}

dependencies {
    implementation(project(":libs:datetime"))
    implementation(project(":libs:encryption:ed25519"))
    implementation(project(":libs:encryption:utils"))
    implementation(project(":libs:currency"))
    implementation(project(":libs:messaging"))
    implementation(project(":libs:models"))
    implementation(project(":libs:network:exchange"))
    implementation(project(":libs:network:connectivity:public"))
    implementation(project(":libs:opengraph"))
    implementation(project(":libs:vibrator:public"))
    api(project(":ui:core"))
    implementation(project(":ui:emojis"))
    implementation(project(":ui:theme"))
    implementation(project(":ui:resources"))

    api(Libs.cloudy)

    implementation(Libs.coil3)
    implementation(Libs.coil3_network)

    implementation(Libs.kotlinx_datetime)

    implementation(platform(Libs.compose_bom))
    implementation(Libs.compose_animation)
    implementation(Libs.compose_foundation)
    implementation(Libs.compose_ui)
    implementation(Libs.compose_activities)
    implementation(project(":ui:navigation"))
    debugApi(Libs.compose_ui_tools)
    api(Libs.compose_ui_tools_preview)
    implementation(Libs.compose_material)
    implementation(Libs.compose_materialIconsExtended)
    implementation(Libs.compose_accompanist)
    implementation(Libs.compose_paging)
    implementation(Libs.timber)
}