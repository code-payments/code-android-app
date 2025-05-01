plugins {
    id(Plugins.android_library)
    id(Plugins.kotlin_android)
    id(Plugins.kotlin_ksp)
    id(Plugins.hilt)
    id(Plugins.kotlin_parcelize)
}

android {
    namespace = "${Android.flipcashNamespace}.features.scanner"
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

    implementation(platform(Libs.compose_bom))
    implementation(Libs.compose_ui)
    implementation(Libs.compose_foundation)
    implementation(Libs.compose_material)
    implementation(Libs.compose_materialIconsExtended)
    implementation(Libs.compose_activities)

    implementation(project(":apps:flipcash:core"))
    implementation(project(":apps:flipcash:shared:session"))

    implementation(project(":libs:datetime"))
    implementation(project(":libs:logging"))
    implementation(project(":libs:messaging"))
    implementation(project(":libs:permissions:bindings"))
    implementation(project(":ui:analytics"))
    implementation(project(":ui:biometrics"))
    implementation(project(":ui:core"))
    implementation(project(":ui:components"))
    implementation(project(":ui:navigation"))
    implementation(project(":ui:resources"))
    implementation(project(":ui:scanner"))
    implementation(project(":ui:theme"))
    implementation(Libs.rinku_compose)
}