import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    id(Plugins.android_library)
    id(Plugins.kotlin_android)
    id(Plugins.kotlin_ksp)
    id(Plugins.hilt)
    id(Plugins.kotlin_parcelize)
    id(Plugins.jetbrains_compose_compiler)
}

android {
    namespace = "${Gradle.flipcashNamespace}.features.pools"
    compileSdk = Android.compileSdkVersion
    defaultConfig {
        minSdk = Android.minSdkVersion
        testInstrumentationRunner = Android.testInstrumentationRunner
    }

    buildFeatures {
        buildConfig = true
        compose = true
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

    implementation(platform(Libs.compose_bom))
    implementation(Libs.compose_ui)
    implementation(Libs.compose_foundation)
    implementation(Libs.compose_material)
    implementation(Libs.compose_materialIconsExtended)
    implementation(Libs.compose_paging)

    implementation(project(":apps:flipcash:core"))
    implementation(project(":apps:flipcash:shared:featureflags"))
    implementation(project(":apps:flipcash:shared:onramp:common"))
    implementation(project(":apps:flipcash:shared:payments"))
    implementation(project(":apps:flipcash:shared:pools"))
    implementation(project(":apps:flipcash:shared:shareable"))
    implementation(project(":libs:logging"))
    implementation(project(":libs:messaging"))

    implementation(project(":services:flipcash"))

    implementation(project(":ui:analytics"))
    implementation(project(":ui:core"))
    implementation(project(":ui:components"))
    implementation(project(":ui:navigation"))
    implementation(project(":ui:resources"))
    implementation(project(":ui:theme"))

    implementation(Libs.rinku_compose)
}