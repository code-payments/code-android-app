import com.bugsnag.gradle.dsl.debug
import com.bugsnag.gradle.dsl.release
import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.plugin.parcelize")
    id("com.google.devtools.ksp")
    id("org.jetbrains.kotlin.plugin.serialization")
    alias(libs.plugins.navigation.safeargs)
    id("dagger.hilt.android.plugin")
    alias(libs.plugins.google.services)
    alias(libs.plugins.firebase.perf)
    alias(libs.plugins.bugsnag.gradle)
    alias(libs.plugins.secrets)
    id("org.jetbrains.kotlin.plugin.compose")
    alias(libs.plugins.kover)
}

fun gitVersionCode(): Int {
    val result = providers.exec {
        commandLine("git", "rev-list", "--count", "HEAD")
    }.standardOutput.asText.get().trim()
    return result.toInt().also { println("VersionCode $it") }
}

val contributorsSigningConfig = ContributorsSignatory(rootProject)
val appNamespace = "${Gradle.flipcashNamespace}.app.android"

android {
    // static namespace
    namespace = appNamespace
    compileSdk = Android.compileSdkVersion

    defaultConfig {
        versionCode = Packaging.Flipcash.versionCode ?: gitVersionCode()
        versionName = Packaging.Flipcash.versionName
        applicationId = appNamespace
        minSdk = Android.minSdkVersion
        targetSdk = Android.targetSdkVersion
        testInstrumentationRunner = Android.testInstrumentationRunner

        buildConfigField("String", "VERSION_NAME", "\"${Packaging.Flipcash.versionName}\"")
        buildConfigField("String", "MIXPANEL_API_KEY", "\"${tryReadProperty(rootProject.rootDir, "MIXPANEL_API_KEY")}\"")
        buildConfigField("Boolean", "NOTIFY_ERRORS", "false")

        manifestPlaceholders["BUGSNAG_API_KEY"] = tryReadProperty(rootProject.rootDir, "BUGSNAG_API_KEY")
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
        resValues = true
    }

    buildTypes {
        getByName("release") {
            resValue("string", "applicationId", appNamespace)
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
        }
        getByName("debug") {
            resValue("string", "applicationId", appNamespace)
            signingConfig = signingConfigs.getByName("contributors")

            val debugMinifyEnabled = tryReadProperty(rootProject.rootDir, "DEBUG_MINIFY", "false").toBooleanStrictOrNull() ?: false
            isMinifyEnabled = debugMinifyEnabled
            isShrinkResources = debugMinifyEnabled

            if (debugMinifyEnabled) {
                proguardFiles(
                    getDefaultProguardFile("proguard-android-optimize.txt"),
                    "proguard-rules.pro"
                )
            }
        }
    }

    compileOptions {
        sourceCompatibility(Android.javaVersion)
        targetCompatibility(Android.javaVersion)
        isCoreLibraryDesugaringEnabled = true
    }

    packaging {
        resources.excludes += setOf("**/*.proto", "META-INF/LICENSE.md", "META-INF/LICENSE-notice.md", "META-INF/versions/9/OSGI-INF/MANIFEST.MF")
    }
}

configurations.all {
    // protobuf-javalite 4.x already includes well-known types, making
    // Firebase's protolite-well-known-types redundant and conflicting.
    exclude(group = "com.google.firebase", module = "protolite-well-known-types")
    // Crashlytics SDK is pulled transitively via firebase-bom but we use Bugsnag
    // for error reporting. Without the Crashlytics Gradle plugin the SDK crashes
    // at startup due to a missing build ID resource.
    exclude(group = "com.google.firebase", module = "firebase-crashlytics")
}

bugsnag {
    variants {
        release {
            autoCreateBuild = true
        }
        debug {
            enabled = false
        }
    }
}

kotlin {
    jvmToolchain {
        languageVersion.set(JavaLanguageVersion.of(Android.javaVersion))
    }

    compilerOptions {
        jvmTarget.set(JvmTarget.fromTarget(Android.javaVersion))
        optIn.addAll(
            "kotlin.time.ExperimentalTime",
            "kotlin.ExperimentalUnsignedTypes",
            "kotlin.RequiresOptIn"
        )
    }
}

dependencies {
    implementation(fileTree(mapOf("dir" to "libs", "include" to listOf("*.jar"))))
    implementation(project(":services:flipcash-compose"))

    implementation(project(":apps:flipcash:core"))
    implementation(project(":apps:flipcash:shared:accesskey"))
    implementation(project(":apps:flipcash:shared:analytics"))
    implementation(project(":apps:flipcash:shared:appsettings"))
    implementation(project(":apps:flipcash:shared:appupdates"))
    implementation(project(":apps:flipcash:shared:authentication"))
    implementation(project(":apps:flipcash:shared:bill-customization"))
    implementation(project(":apps:flipcash:shared:featureflags"))
    implementation(project(":apps:flipcash:shared:router"))
    implementation(project(":apps:flipcash:shared:session"))
    implementation(project(":apps:flipcash:shared:google-play-billing"))
    implementation(project(":apps:flipcash:shared:currency-selection:core"))
    implementation(project(":apps:flipcash:shared:currency-selection:ui"))
    implementation(project(":apps:flipcash:shared:notifications"))
    implementation(project(":apps:flipcash:shared:onramp:coinbase"))
    implementation(project(":apps:flipcash:shared:onramp:deeplinks"))
    implementation(project(":apps:flipcash:shared:payments"))
    implementation(project(":apps:flipcash:shared:permissions"))
    implementation(project(":apps:flipcash:shared:phone"))
    implementation(project(":apps:flipcash:shared:shareable"))
    implementation(project(":apps:flipcash:shared:tokens"))
    implementation(project(":apps:flipcash:shared:web"))
    implementation(project(":apps:flipcash:shared:workers"))
    implementation(project(":apps:flipcash:features:login"))
    implementation(project(":apps:flipcash:features:purchase"))
    implementation(project(":apps:flipcash:features:scanner"))
    implementation(project(":apps:flipcash:features:cash"))
    implementation(project(":apps:flipcash:features:balance"))
    implementation(project(":apps:flipcash:features:menu"))
    implementation(project(":apps:flipcash:features:lab"))
    implementation(project(":apps:flipcash:features:advanced"))
    implementation(project(":apps:flipcash:features:device-logs"))
    implementation(project(":apps:flipcash:features:appsettings"))
    implementation(project(":apps:flipcash:features:appupdates"))
    implementation(project(":apps:flipcash:features:deposit"))
    implementation(project(":apps:flipcash:features:myaccount"))
    implementation(project(":apps:flipcash:features:backupkey"))
    implementation(project(":apps:flipcash:features:shareapp"))
    implementation(project(":apps:flipcash:features:withdrawal"))
    implementation(project(":apps:flipcash:features:contact-verification"))
    implementation(project(":apps:flipcash:features:tokens"))
    implementation(project(":apps:flipcash:features:transactions"))
    implementation(project(":apps:flipcash:features:bill-customization"))
    implementation(project(":apps:flipcash:features:currency-creator"))
    implementation(project(":apps:flipcash:features:discovery"))
    implementation(project(":apps:flipcash:features:userflags"))

    implementation(project(":libs:crypto:solana"))
    implementation(project(":libs:datetime"))
    implementation(project(":libs:locale:bindings"))
    implementation(project(":libs:logging"))
    implementation(project(":libs:vibrator:bindings"))
    implementation(project(":libs:currency"))
    implementation(project(":libs:messaging"))
    implementation(project(":libs:network:connectivity:bindings"))
    implementation(project(":libs:permissions:bindings"))
    implementation(project(":libs:quickresponse"))
    implementation(project(":ui:biometrics"))
    implementation(project(":ui:components"))
    implementation(project(":ui:navigation"))
    implementation(project(":ui:scanner"))
    implementation(project(":ui:resources"))
    implementation(project(":ui:theme"))

    coreLibraryDesugaring(libs.android.desugaring)

    //standard libraries
    implementation(libs.kotlinx.collections.immutable)
    implementation(libs.kotlinx.serialization.json)
    implementation(libs.androidx.core)
    implementation(libs.androidx.lifecycle.runtime)
    implementation(libs.androidx.lifecycle.viewmodel)
    implementation(libs.androidx.navigation.ui)
    implementation(libs.androidx.work)
    implementation("androidx.webkit:webkit:1.14.0")

    //hilt dependency injection
    implementation(libs.hilt.android)
    implementation(libs.hilt.worker)
    ksp(libs.hilt.android.compiler)
    ksp(libs.hilt.compiler)

    androidTestImplementation(libs.hilt.android)
    androidTestImplementation(libs.hilt.android.test)
    kspAndroidTest(libs.hilt.android.compiler)
    testImplementation(libs.hilt.android.test)
    kspTest(libs.hilt.android.compiler)

    androidTestImplementation("io.mockk:mockk:1.14.6")

    //Jetpack compose
    implementation(platform(libs.compose.bom))
    implementation(libs.compose.ui)
    debugImplementation(libs.compose.ui.tools)
    implementation(libs.compose.accompanist)
    implementation(libs.compose.foundation)
    implementation(libs.compose.material)
    implementation(libs.compose.material.icons.extended)
    implementation(libs.compose.activities)
    implementation(libs.compose.view.models)

    implementation(libs.androidx.activity)

    implementation(libs.androidx.browser)

    implementation(libs.androidx.camerax.lifecycle)

    implementation(libs.slf4j)
    implementation(libs.grpc.android)

    implementation(platform(libs.firebase.bom))
    implementation(libs.firebase.analytics)
    implementation(libs.firebase.messaging)

    implementation(libs.mixpanel)

    androidTestImplementation(libs.androidx.test.runner)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.junit)
    androidTestImplementation(libs.espresso.core)
    androidTestImplementation(libs.espresso.contrib) {
        exclude(module = "protobuf-lite")
    }
    androidTestImplementation(libs.espresso.intents)

    implementation(libs.timber)
    implementation(libs.bugsnag)
}
