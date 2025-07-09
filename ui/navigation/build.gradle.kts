import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    id(Plugins.android_library)
    id(Plugins.kotlin_android)
    id(Plugins.kotlin_serialization)
    id(Plugins.kotlin_parcelize)
    id(Plugins.jetbrains_compose_compiler)
}

android {
    namespace = "${Gradle.codeNamespace}.navigation"
    compileSdk = Android.compileSdkVersion
    defaultConfig {
        minSdk = Android.minSdkVersion
        testInstrumentationRunner = Android.testInstrumentationRunner
    }

    buildFeatures {
        compose = true
    }

    java {
        toolchain {
            languageVersion.set(JavaLanguageVersion.of(Versions.java))
        }
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
