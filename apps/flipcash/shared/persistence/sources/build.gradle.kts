plugins {
    id(Plugins.android_library)
    id(Plugins.kotlin_android)
    id(Plugins.kotlin_ksp)
    id(Plugins.kotlin_serialization)
}

android {
    namespace = "${Android.flipcashNamespace}.shared.persistence.sources"
    compileSdk = Android.compileSdkVersion
    defaultConfig {
        minSdk = Android.minSdkVersion
        targetSdk = Android.targetSdkVersion
        buildToolsVersion = Android.buildToolsVersion
        testInstrumentationRunner = Android.testInstrumentationRunner

        javaCompileOptions {
            annotationProcessorOptions {
                arguments += mapOf("room.schemaLocation" to "$projectDir/schemas")
            }
        }
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
    implementation(Libs.inject)
    implementation(Libs.hilt)
    ksp(Libs.hilt_android_compiler)
    ksp(Libs.hilt_compiler)

    implementation(Libs.kotlinx_serialization_core)
    implementation(Libs.kotlinx_serialization_json)

    implementation(Libs.bugsnag)

    implementation(Libs.androidx_room_ktx)
    implementation(Libs.androidx_room_paging)
    implementation(Libs.androidx_paging_runtime)

    ksp(Libs.androidx_room_compiler)

    implementation(project(":apps:flipcash:core"))
    implementation(project(":apps:flipcash:shared:persistence:db"))

    implementation(project(":libs:logging"))
    implementation(project(":libs:encryption:base58"))
    implementation(project(":libs:encryption:utils"))
    implementation(project(":libs:crypto:kin"))

    implementation(project(":services:flipcash"))
}