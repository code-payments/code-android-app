import com.google.firebase.crashlytics.buildtools.gradle.CrashlyticsExtension
import org.jetbrains.kotlin.cli.common.toBooleanLenient

plugins {
    id(Plugins.android_application)
    id(Plugins.kotlin_android)
    id(Plugins.kotlin_parcelize)
    id(Plugins.kotlin_kapt)
    id(Plugins.kotlin_serialization)
    id(Plugins.androidx_navigation_safeargs)
    id(Plugins.hilt)
    id(Plugins.google_services)
    id(Plugins.firebase_crashlytics)
    id(Plugins.firebase_perf)
    id(Plugins.bugsnag)
    id(Plugins.secrets_gradle_plugin)
}

val contributorsSigningConfig = ContributorsSignatory(rootProject)

android {
    namespace = Android.namespace
    compileSdk = Android.compileSdkVersion

    defaultConfig {
        applicationId = Android.namespace
        versionCode = 392
        versionName = "1.1.$versionCode"

        minSdk = Android.minSdkVersion
        targetSdk = Android.targetSdkVersion
        buildToolsVersion = Android.buildToolsVersion
        testInstrumentationRunner = Android.testInstrumentationRunner

        resValue("string", "applicationId", Android.namespace)

        buildConfigField("String", "MIXPANEL_API_KEY", "\"${tryReadProperty(rootProject.rootDir, "MIXPANEL_API_KEY")}\"")
        buildConfigField("String", "KADO_API_KEY", "\"${tryReadProperty(rootProject.rootDir, "KADO_API_KEY")}\"")
        buildConfigField("Boolean", "NOTIFY_ERRORS", "false")
    }

    signingConfigs {
        create("contributors") {
            storeFile = contributorsSigningConfig.keystore
            storePassword = contributorsSigningConfig.keystorePassword
            keyAlias = contributorsSigningConfig.keyAlias
            keyPassword = contributorsSigningConfig.keyPassword
        }
    }

    buildFeatures {
        buildConfig = true
        compose = true
    }

    composeOptions {
        kotlinCompilerExtensionVersion = Versions.compose_compiler
    }

    buildTypes {
        getByName("release") {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
        }
        getByName("debug") {
            applicationIdSuffix = ".dev"
            signingConfig = signingConfigs.getByName("contributors")

            val debugMinifyEnabled = tryReadProperty(rootProject.rootDir, "DEBUG_MINIFY", "false").toBooleanLenient() ?: false
            isMinifyEnabled = debugMinifyEnabled
            isShrinkResources = debugMinifyEnabled

            if (debugMinifyEnabled) {
                proguardFiles(
                    getDefaultProguardFile("proguard-android-optimize.txt"),
                    "proguard-rules.pro"
                )
            }

            configure<CrashlyticsExtension> {
                mappingFileUploadEnabled = tryReadProperty(rootProject.rootDir, "DEBUG_CRASHLYTICS_UPLOAD", "false").toBooleanLenient() ?: false
            }
        }
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
}

dependencies {
    implementation(fileTree(mapOf("dir" to "libs", "include" to listOf("*.jar"))))

    implementation(project(":api"))
    implementation(project(":common:resources"))
    implementation(project(":common:theme"))
    implementation(project(":vendor:tipkit-m2"))

    //standard libraries
    implementation(Libs.kotlinx_collections_immutable)
    implementation(Libs.kotlinx_serialization_json)
    implementation(Libs.kotlinx_datetime)
    implementation(Libs.androidx_core)
    implementation(Libs.androidx_constraint_layout)
    implementation(Libs.androidx_lifecycle_runtime)
    implementation(Libs.androidx_lifecycle_viewmodel)
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

    androidTestImplementation("io.mockk:mockk:1.13.11")

    //Jetpack compose
    implementation(platform(Libs.compose_bom))
    implementation(Libs.compose_ui)
    debugImplementation(Libs.compose_ui_tools)
    implementation(Libs.compose_ui_tools_preview)
    implementation(Libs.compose_accompanist)
    implementation(Libs.compose_foundation)
    implementation(Libs.compose_material)
    implementation(Libs.compose_materialIconsExtended)
    implementation(Libs.compose_activities)
    implementation(Libs.compose_view_models)
    implementation(Libs.compose_livedata)
    implementation(Libs.compose_navigation)
    implementation(Libs.compose_paging)
    implementation(Libs.compose_voyager_navigation)
    implementation(Libs.compose_voyager_navigation_transitions)
    implementation(Libs.compose_voyager_navigation_bottomsheet)
    implementation(Libs.compose_voyager_navigation_hilt)

    // cameraX
    implementation(Libs.androidx_camerax_core)
    implementation(Libs.androidx_camerax_camera2)
    implementation(Libs.androidx_camerax_lifecycle)
    implementation(Libs.androidx_camerax_view)

    implementation(Libs.coil3)
    implementation(Libs.coil3_network)

    implementation(Libs.androidx_browser)
    implementation(Libs.androidx_constraint_layout_compose)

    implementation(Libs.rxjava)
    implementation(Libs.rxandroid)

    implementation(Libs.slf4j)
    implementation(Libs.grpc_android)

    implementation(platform(Libs.firebase_bom))
    implementation(Libs.firebase_analytics)
    implementation(Libs.firebase_crashlytics)
    implementation(Libs.firebase_messaging)

    implementation(Libs.hilt_nav_compose)
    implementation(Libs.lib_phone_number_port)
    implementation(Libs.mp_android_chart)
    implementation(Libs.qr_generator)
    implementation(Libs.zxing)
    implementation(Libs.mixpanel)

    implementation(Libs.cloudy)

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
    implementation(Libs.androidx_room_paging)
    kapt(Libs.androidx_room_compiler)

    implementation(Libs.markwon_core)
    implementation(Libs.markwon_linkify)
    implementation(Libs.markwon_ext_strikethrough)

    implementation(Libs.play_service_auth)
    implementation(Libs.play_service_auth_phone)

    implementation(Libs.timber)
    implementation(Libs.bugsnag)

    implementation("dev.chrisbanes.haze:haze:0.7.1")
}
