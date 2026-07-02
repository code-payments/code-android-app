plugins {
    id("com.android.test")
    alias(libs.plugins.androidx.baselineprofile)
}

val contributorsSigningConfig = ContributorsSignatory(rootDir)

android {
    namespace = "com.flipcash.benchmark"
    compileSdk = libs.versions.android.compileSdk.get().toInt()

    defaultConfig {
        minSdk = 29
        targetSdk = libs.versions.android.targetSdk.get().toInt()
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        testInstrumentationRunnerArguments["androidx.benchmark.suppressErrors"] = "EMULATOR"
    }

    signingConfigs {
        create("contributors") {
            storeFile = contributorsSigningConfig.keystore
            storePassword = contributorsSigningConfig.keystorePassword
            keyAlias = contributorsSigningConfig.keyAlias
            keyPassword = contributorsSigningConfig.keyPassword
        }
    }

    buildTypes {
        create("benchmark") {
            isDebuggable = true
            signingConfig = signingConfigs.getByName("contributors")
            matchingFallbacks += listOf("debug")
        }
    }

    compileOptions {
        sourceCompatibility(libs.versions.android.java.get())
        targetCompatibility(libs.versions.android.java.get())
    }

    kotlin {
        jvmToolchain(libs.versions.android.java.get().toInt())
    }

    targetProjectPath = ":apps:flipcash:app"
    experimentalProperties["android.experimental.self-instrumenting"] = true
}

dependencies {
    implementation(libs.androidx.benchmark.macro.junit4)
    implementation(libs.androidx.test.runner)
    implementation(libs.androidx.junit)
}
