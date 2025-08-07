import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    id(Plugins.android_library)
    id(Plugins.kotlin_android)
    id(Plugins.kotlin_ksp)
    id(Plugins.kotlin_serialization)
    id(Plugins.hilt)
}

android {
    namespace = "${Gradle.codeNamespace}.vendor.solana"
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

    kotlinOptions {
        jvmTarget = JvmTarget.fromTarget(Versions.java).target
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
    implementation(Libs.timber)
    implementation(Libs.kotlinx_serialization_json)

    implementation(Libs.okhttp)
    implementation(Libs.okhttp_logging_interceptor)

    implementation("org.sol4k:sol4k:0.5.16")
    api("com.solanamobile:web3-solana:0.2.5")
    api("com.solanamobile:rpc-core:0.2.8")
    implementation("com.solanamobile:rpc-okiodriver:0.2.8")

    implementation(Libs.hilt)
    ksp(Libs.hilt_android_compiler)
    ksp(Libs.hilt_compiler)
}
