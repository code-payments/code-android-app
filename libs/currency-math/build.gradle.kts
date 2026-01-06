import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    id(Plugins.android_library)
    id(Plugins.kotlin_android)
    id(Plugins.kotlin_ksp)
    id(Plugins.kotlin_serialization)
    id(Plugins.kotlin_parcelize)
}

android {
    namespace = "${Gradle.codeNamespace}.libs.currency.math"
    compileSdk = Android.compileSdkVersion
    defaultConfig {
        minSdk = Android.minSdkVersion
        testInstrumentationRunner = Android.testInstrumentationRunner
    }

    compileOptions {
        sourceCompatibility(Versions.java)
        targetCompatibility(Versions.java)
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
    androidTestImplementation(Libs.androidx_junit)
    androidTestImplementation(Libs.junit)
    androidTestImplementation(Libs.androidx_test_runner)

    implementation(Libs.inject)
    implementation(Libs.hilt)
    implementation(Libs.timber)
    implementation(Libs.bugsnag)

    testImplementation(kotlin("test"))
}

val generateCurveTables by tasks.registering(GenerateCurveTables::class) {
    rustTableUrl.set("https://raw.githubusercontent.com/code-payments/flipcash-program/refs/heads/main/api/src/table.rs")
    outputDir.set(layout.projectDirectory.dir("src/main/assets"))

    // Always regenerate (useful for CI or development)
    // TODO: enable for CI when repo is public
    // forceRegenerate.set(true)
}

// Make sure tables are generated before assets are merged
tasks.named("preBuild") {
    dependsOn(generateCurveTables)
}
