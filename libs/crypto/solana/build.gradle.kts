plugins {
    id(Plugins.android_library)
    id(Plugins.kotlin_android)
    id(Plugins.kotlin_kapt)
    id(Plugins.kotlin_serialization)
}

android {
    namespace = "${Android.codeNamespace}.vendor.solana"
    compileSdk = Android.compileSdkVersion
    defaultConfig {
        minSdk = Android.minSdkVersion
        targetSdk = Android.targetSdkVersion
        buildToolsVersion = Android.buildToolsVersion
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

    kotlinOptions {
        jvmTarget = Versions.java
        freeCompilerArgs += listOf(
            "-opt-in=kotlin.ExperimentalUnsignedTypes",
            "-opt-in=kotlin.RequiresOptIn"
        )
    }
}

dependencies {
    implementation(project(":libs:encryption:base58"))
    implementation(project(":libs:encryption:ed25519"))
    implementation(project(":libs:encryption:hmac"))
    implementation(project(":libs:encryption:keys"))
    implementation(project(":libs:encryption:sha256"))
    implementation(project(":libs:encryption:sha512"))
    implementation(project(":libs:encryption:utils"))
    implementation(project(":libs:crypto:kin"))
    implementation(project(":libs:currency"))
//    implementation(project(":libs:models"))
    implementation(Libs.timber)
    implementation(Libs.kotlinx_serialization_json)
}