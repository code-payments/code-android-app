import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    id(Plugins.android_library)
    id(Plugins.kotlin_android)
    id(Plugins.kotlin_ksp)
    id(Plugins.hilt)
    id(Plugins.kotlin_parcelize)
}

android {
    namespace = "${Gradle.flipcashNamespace}.shared.authentication"
    compileSdk = Android.compileSdkVersion
    defaultConfig {
        minSdk = Android.minSdkVersion
        testInstrumentationRunner = Android.testInstrumentationRunner
    }

    buildFeatures {
        buildConfig = true
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

    implementation(platform(Libs.firebase_bom))
    implementation(Libs.firebase_messaging)
    implementation(Libs.bugsnag)

    implementation(Libs.androidx_credentials)
    implementation(Libs.androidx_credentials_play_auth)
    implementation(Libs.androidx_datastore)

    implementation(project(":apps:flipcash:shared:appsettings"))
    implementation(project(":apps:flipcash:shared:persistence:provider"))
    implementation(project(":apps:flipcash:shared:featureflags"))
    implementation(project(":services:flipcash"))
}