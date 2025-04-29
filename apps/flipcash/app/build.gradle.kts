import com.google.firebase.crashlytics.buildtools.gradle.CrashlyticsExtension
import org.jetbrains.kotlin.cli.common.toBooleanLenient

plugins {
    id(Plugins.android_application)
    id(Plugins.kotlin_android)
    id(Plugins.kotlin_parcelize)
    id(Plugins.kotlin_ksp)
    id(Plugins.kotlin_serialization)
    id(Plugins.androidx_navigation_safeargs)
    id(Plugins.hilt)
    id(Plugins.google_services)
    id(Plugins.firebase_crashlytics)
    id(Plugins.firebase_perf)
    id(Plugins.bugsnag)
    id(Plugins.secrets_gradle_plugin)
    id(Plugins.versioning_gradle_plugin)
}

val contributorsSigningConfig = ContributorsSignatory(rootProject)
val appNamespace = "${Android.flipcashNamespace}.app"

android {
    // static namespace
    namespace = appNamespace
    compileSdk = Android.compileSdkVersion

    defaultConfig {
        versionCode = versioning.getVersionCode()
        versionName = Packaging.Flipcash.versionName
        applicationId = appNamespace
        minSdk = Android.minSdkVersion
        targetSdk = Android.targetSdkVersion
        buildToolsVersion = Android.buildToolsVersion
        testInstrumentationRunner = Android.testInstrumentationRunner

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
            resValue("string", "applicationId", appNamespace)
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
        }
        getByName("debug") {
            applicationIdSuffix = ".dev"
            resValue("string", "applicationId", "${appNamespace}.dev")
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
        isCoreLibraryDesugaringEnabled = true
    }

    java {
        toolchain {
            languageVersion.set(JavaLanguageVersion.of(Versions.java))
        }
    }

    kotlinOptions {
        jvmTarget = Versions.java
        freeCompilerArgs += listOf(
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
    implementation(project(":services:flipcash-compose"))

    implementation(project(":apps:flipcash:core"))
    implementation(project(":apps:flipcash:features:currency-selection:core"))
    implementation(project(":apps:flipcash:features:currency-selection:ui"))
    implementation(project(":apps:flipcash:features:accesskey"))
    implementation(project(":apps:flipcash:features:login"))
    implementation(project(":apps:flipcash:features:router"))
    implementation(project(":apps:flipcash:features:scanner"))
    implementation(project(":apps:flipcash:features:give"))
    implementation(project(":apps:flipcash:features:balance"))
    implementation(project(":apps:flipcash:features:send"))
    implementation(project(":apps:flipcash:features:menu"))

    implementation(project(":libs:datetime"))
    implementation(project(":libs:locale:bindings"))
    implementation(project(":libs:vibrator:bindings"))
    implementation(project(":libs:currency"))
    implementation(project(":libs:messaging"))
    implementation(project(":libs:network:connectivity:bindings"))
    implementation(project(":libs:permissions:bindings"))
    implementation(project(":libs:quickresponse"))
    implementation(project(":libs:requests"))
    implementation(project(":ui:components"))
    implementation(project(":ui:scanner"))
    implementation(project(":ui:resources"))
    implementation(project(":ui:theme"))

    coreLibraryDesugaring(Libs.android_desugaring)

    //standard libraries
    implementation(Libs.kotlinx_collections_immutable)
    implementation(Libs.kotlinx_serialization_json)
    implementation(Libs.androidx_core)
    implementation(Libs.androidx_lifecycle_runtime)
    implementation(Libs.androidx_lifecycle_viewmodel)
    implementation(Libs.androidx_navigation_ui)

    //hilt dependency injection
    implementation(Libs.hilt)
    implementation("androidx.webkit:webkit:1.13.0")
    ksp(Libs.hilt_android_compiler)
    ksp(Libs.hilt_compiler)

    androidTestImplementation(Libs.hilt)
    androidTestImplementation(Libs.hilt_android_test)
    kspAndroidTest(Libs.hilt_android_compiler)
    testImplementation(Libs.hilt_android_test)
    kspTest(Libs.hilt_android_compiler)

    androidTestImplementation("io.mockk:mockk:1.13.17")

    //Jetpack compose
    implementation(platform(Libs.compose_bom))
    implementation(Libs.compose_ui)
    debugImplementation(Libs.compose_ui_tools)
    implementation(Libs.compose_accompanist)
    implementation(Libs.compose_foundation)
    implementation(Libs.compose_material)
    implementation(Libs.compose_materialIconsExtended)
    implementation(Libs.compose_activities)
    implementation(Libs.compose_view_models)

    implementation(Libs.androidx_activity)

    implementation(Libs.coil3)
    implementation(Libs.coil3_network)

    implementation(Libs.androidx_browser)

    implementation(Libs.slf4j)
    implementation(Libs.grpc_android)

    implementation(platform(Libs.firebase_bom))
    implementation(Libs.firebase_analytics)
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

    implementation(Libs.timber)
    implementation(Libs.bugsnag)
}