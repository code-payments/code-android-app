import org.jetbrains.kotlin.gradle.plugin.KotlinPlatformType

plugins {
    alias(libs.plugins.flipcash.kmp.app.library)
    alias(libs.plugins.flipcash.kmp.js)
    alias(libs.plugins.flipcash.kmp.test.fixtures)
}

// The pinned outputs live once, in the device-test assets: android-api36.txt is what Android computes
// (the reference, which Node must match exactly), host-jdk.txt what the host JDK computes.
testFixtures {
    packageName = "com.getcode.opencode.pin"
    fixtures = layout.projectDirectory.dir("src/androidDeviceTest/assets/pins")
}

// OpenCode domain types (accounts, mints, rates, fiat amounts, swap parameters) with no protos,
// gRPC or DI, plus the swap instruction builders and TransactionBuilder, which take these types.
// Types that wrap verified proto state (ExchangeData, LocalFiat, AccountInfo, ...) stay in
// :services:opencode.
@OptIn(org.jetbrains.kotlin.gradle.ExperimentalKotlinGradlePluginApi::class)
kotlin {
    // jvmShared: both JVM targets (Android and desktop). Number formatting, currency symbols and
    // java.util.UUID helpers live there; on JS the formatting goes through Intl.
    applyDefaultHierarchyTemplate {
        common {
            group("jvmShared") {
                withJvm()
                withCompilations { it.platformType == KotlinPlatformType.androidJvm }
            }
        }
    }

    android {
        namespace = "${Gradle.codeNamespace}.services.opencode.model"
        // Packages src/androidDeviceTest/assets (the pin fixtures) into the device-test APK.
        androidResources { enable = true }
        withDeviceTestBuilder {}.configure {
            instrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        }
    }

    sourceSets {
        all {
            languageSettings.optIn("com.getcode.opencode.annotations.InternalOpenCodeApi")
        }
        commonMain.dependencies {
            api(project(":services:opencode-solana"))
            api(project(":libs:currency-math"))
            api(project(":libs:encryption:base58"))
            api(project(":libs:encryption:ed25519"))
            api(project(":libs:encryption:keys"))
            api(project(":libs:encryption:mnemonic"))
            api(project(":libs:encryption:sha256"))
            api(project(":libs:encryption:utils"))
            api(project(":libs:logging"))
            implementation(libs.kotlinx.serialization.json)
        }
        commonTest.dependencies {
            implementation(kotlin("test"))
        }
        // The formatting and amount cases, shared with the device test so a device's output can be
        // compared with the host JVM's and Node's.
        commonTest { kotlin.srcDir("src/pinCases/kotlin") }
        getByName("androidDeviceTest") {
            kotlin.srcDir("src/pinCases/kotlin")
            dependencies {
                implementation(libs.androidx.junit)
                implementation(libs.junit)
                implementation(libs.androidx.test.runner)
            }
        }
    }
}
