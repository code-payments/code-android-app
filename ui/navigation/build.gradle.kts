plugins {
    id(Plugins.android_library)
    id(Plugins.kotlin_android)
    id(Plugins.kotlin_serialization)
    id(Plugins.kotlin_parcelize)
    id(Plugins.jetbrains_compose_compiler)
}

android {
    namespace = "${Android.codeNamespace}.navigation"
    compileSdk = Android.compileSdkVersion
    defaultConfig {
        minSdk = Android.minSdkVersion
        targetSdk = Android.targetSdkVersion
        buildToolsVersion = Android.buildToolsVersion
        testInstrumentationRunner = Android.testInstrumentationRunner
    }

    buildFeatures {
        compose = true
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
}

dependencies {
    implementation(project(":libs:models"))
    implementation(project(":ui:core"))
    implementation(project(":ui:theme"))

    implementation(Libs.androidx_annotation)
    api(Libs.kotlin_stdlib)

    api(Libs.rxjava)
    api(Libs.rxandroid)

    implementation(platform(Libs.compose_bom))
    implementation(Libs.compose_ui)
    implementation(Libs.compose_foundation)
    implementation(Libs.compose_material)
    implementation(Libs.compose_activities)

    implementation(Libs.androidx_lifecycle_runtime)
    implementation(Libs.androidx_lifecycle_viewmodel)
    implementation(Libs.androidx_navigation_fragment)

    implementation(Libs.timber)

    api(Libs.compose_voyager_navigation)
    api(Libs.compose_voyager_navigation_transitions)
    api(Libs.compose_voyager_navigation_bottomsheet)
    api(Libs.compose_voyager_navigation_tabs)
    api(Libs.compose_voyager_navigation_hilt)

    api(Libs.rinku)
}
