import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    id(Plugins.android_library)
    id(Plugins.kotlin_android)
    id(Plugins.kotlin_ksp)
    id(Plugins.kotlin_serialization)
    id(Plugins.androidx_room)
}

android {
    namespace = "${Gradle.flipcashNamespace}.shared.persistence.db"
    compileSdk = Android.compileSdkVersion
    defaultConfig {
        minSdk = Android.minSdkVersion
        testInstrumentationRunner = Android.testInstrumentationRunner
    }

    room {
        schemaDirectory("$projectDir/schemas")
    }
}

kotlin {
    jvmToolchain {
        languageVersion.set(JavaLanguageVersion.of(Versions.java))
    }

    compilerOptions {
        jvmTarget.set(JvmTarget.fromTarget(Versions.java))
        optIn.addAll(
            "kotlin.time.ExperimentalTime",
            "kotlin.ExperimentalUnsignedTypes",
            "kotlin.RequiresOptIn"
        )
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

    implementation(project(":libs:logging"))
    implementation(project(":libs:models"))
    implementation(project(":libs:encryption:base58"))
    implementation(project(":libs:encryption:utils"))
    implementation(project(":libs:crypto:kin"))

    implementation(project(":services:flipcash"))
}