plugins {
    id(Plugins.android_library)
    id(Plugins.kotlin_android)
    id(Plugins.kotlin_ksp)
    id(Plugins.hilt)
    id(Plugins.kotlin_parcelize)
}

android {
    namespace = "${Android.flipcashNamespace}.shared.activityfeed"
    compileSdk = Android.compileSdkVersion
    defaultConfig {
        minSdk = Android.minSdkVersion
        targetSdk = Android.targetSdkVersion
        buildToolsVersion = Android.buildToolsVersion
        testInstrumentationRunner = Android.testInstrumentationRunner
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

    implementation(Libs.androidx_room_ktx)
    implementation(Libs.androidx_room_paging)
    implementation(Libs.androidx_paging_runtime)

    implementation(project(":apps:flipcash:core"))
    compileOnly(project(":apps:flipcash:shared:persistence:db"))
    implementation(project(":apps:flipcash:shared:persistence:sources"))
    implementation(project(":services:flipcash"))
    implementation(project(":libs:datetime"))
    implementation(project(":libs:logging"))
}