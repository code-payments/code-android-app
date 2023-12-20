import java.util.Properties

plugins {
    id(Plugins.android_application)
    id(Plugins.kotlin_android)
    id(Plugins.kotlin_parcelize)
    id(Plugins.kotlin_kapt)
    id(Plugins.androidx_navigation_safeargs)
    id(Plugins.hilt)
    id(Plugins.google_services)
    id(Plugins.firebase_crashlytics)
    id(Plugins.firebase_perf)
    id(Plugins.bugsnag)
    id(Plugins.secrets_gradle_plugin)
}

android {
    namespace = "com.getcode"
    compileSdk = Android.compileSdkVersion

    defaultConfig {
        applicationId = "com.getcode"
        versionCode = 297
        versionName = "1.1.$versionCode"

        minSdk = Android.minSdkVersion
        targetSdk = Android.targetSdkVersion
        buildToolsVersion = Android.buildToolsVersion
        testInstrumentationRunner = Android.testInstrumentationRunner

        val propertiesFile = project.rootProject.file("local.properties")
        val properties = Properties()
        properties.load(propertiesFile.inputStream())
        buildConfigField("String", "MIXPANEL_API_KEY", "\"${properties.getProperty("MIXPANEL_API_KEY")}\"")
    }

    signingConfigs {
    }

    buildFeatures {
        viewBinding = true
        dataBinding = true
        compose = true
    }
    composeOptions {
        kotlinCompilerExtensionVersion = "1.5.3"
    }

    buildTypes {
        getByName("release") {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            //signingConfig = signingConfigs.getByName("debug")
        }
        getByName("debug") {
            applicationIdSuffix = ".dev"


            //isMinifyEnabled = true
            //isShrinkResources = true
            //proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
        }
    }

    compileOptions {
        sourceCompatibility(Versions.java)
        targetCompatibility(Versions.java)
    }

    java {
        toolchain {
            languageVersion.set(JavaLanguageVersion.of(17))
        }
    }

    kotlinOptions {
        jvmTarget = Versions.java
        freeCompilerArgs += listOf(
            "-Xuse-experimental=kotlinx.coroutines.ExperimentalCoroutinesApi",
            "-Xuse-experimental=kotlinx.coroutines.FlowPreview",
            "-opt-in=kotlin.RequiresOptIn"
        )
    }
    packaging {
        resources.excludes.add("**/*.proto")
        resources.excludes.add("META-INF/LICENSE.md")
        resources.excludes.add("META-INF/LICENSE-notice.md")
    }

    buildFeatures {
        buildConfig = true
    }
}


dependencies {
    implementation(fileTree(mapOf("dir" to "libs", "include" to listOf("*.jar"))))

    implementation(project(":api"))
    implementation(project(":model"))
    implementation(project(":ed25519"))

    //standard libraries
    implementation(Libs.kotlin_stdlib)
    implementation(Libs.kotlinx_collections_immutable)
    implementation(Libs.androidx_core)
    implementation(Libs.androidx_constraint_layout)
    implementation(Libs.androidx_lifecycle_runtime)
    implementation(Libs.androidx_navigation_fragment)
    implementation(Libs.androidx_navigation_ui)

    //hilt dependency injection
    implementation(Libs.hilt)
    kapt(Libs.hilt_android_compiler)
    kapt(Libs.hilt_compiler)
    androidTestImplementation(Libs.hilt)
    androidTestImplementation(Libs.hilt_android_test)
    kaptAndroidTest(Libs.hilt_android_compiler)
    testImplementation(Libs.hilt_android_test)
    kaptTest(Libs.hilt_android_compiler)

    androidTestImplementation("io.mockk:mockk:1.13.5")

    //Jetpack compose
    implementation(Libs.compose_ui)
    debugImplementation(Libs.compose_ui_tools)
    implementation(Libs.compose_ui_tools_preview)
    implementation(Libs.compose_foundation)
    implementation(Libs.compose_material)
    implementation("androidx.activity:activity-compose:1.8.0")
    implementation(Libs.compose_view_models)
    implementation(Libs.compose_livedata)
    implementation(Libs.compose_navigation)
    implementation(Libs.androidx_constraint_layout_compose)

    implementation(Libs.rxjava)
    implementation(Libs.rxandroid)

    implementation(Libs.compose_accompanist)
    implementation(Libs.slf4j)
    implementation(Libs.grpc_android)
    implementation(Libs.kin_sdk)

    implementation(platform(Libs.firebase_bom))
    implementation(Libs.firebase_analytics)

    implementation(Libs.hilt_nav_compose)
    implementation(Libs.lib_phone_number_port)
    implementation(Libs.mp_android_chart)
    implementation(Libs.qr_generator)
    implementation(Libs.zxing)

    implementation(Libs.androidx_browser)
    implementation(Libs.firebase_crashlytics)
    implementation(Libs.firebase_messaging)
    implementation(Libs.mixpanel)

    androidTestImplementation(Libs.androidx_test_runner)
    androidTestImplementation(Libs.androidx_junit)
    androidTestImplementation(Libs.junit)
    androidTestImplementation(Libs.espresso_core)
    androidTestImplementation(Libs.espresso_contrib) {
        exclude(module = "protobuf-lite")
    }
    androidTestImplementation(Libs.espresso_intents)
    implementation(Libs.androidx_room_runtime)
    implementation(Libs.androidx_room_ktx)
    implementation(Libs.androidx_room_rxjava3)
    kapt(Libs.androidx_room_compiler)

    implementation(Libs.markwon_core)
    implementation(Libs.markwon_linkify)
    implementation(Libs.markwon_ext_strikethrough)

    implementation(Libs.play_service_auth)
    implementation(Libs.play_service_auth_phone)

    implementation(Libs.timber)
    implementation(Libs.bugsnag)
}
