import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    id(Plugins.android_library)
    id(Plugins.kotlin_android)
    id(Plugins.kotlin_kapt)
    id(Plugins.kotlin_serialization)
    id(Plugins.kotlin_parcelize)
}

android {
    namespace = "${Gradle.codeNamespace}.libs.models"
    compileSdk = Android.compileSdkVersion
    defaultConfig {
        minSdk = Android.minSdkVersion
        testInstrumentationRunner = Android.testInstrumentationRunner
    }

    compileOptions {
        sourceCompatibility(Versions.java)
        targetCompatibility(Versions.java)
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
    implementation(project(":libs:encryption:base58"))
    implementation(project(":libs:encryption:ed25519"))
    implementation(project(":libs:encryption:keys"))
    implementation(project(":libs:encryption:utils"))
    implementation(project(":libs:crypto:kin"))
    implementation(project(":libs:currency"))
    api(project(":libs:models"))
    implementation(Libs.kotlinx_serialization_json)

    implementation(Libs.androidx_room_runtime)
    implementation(Libs.androidx_room_ktx)

    implementation(Libs.sodium_bindings)

    implementation(project(":definitions:code:models"))
}
