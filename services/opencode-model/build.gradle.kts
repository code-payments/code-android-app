import org.jetbrains.kotlin.gradle.plugin.KotlinPlatformType

plugins {
    alias(libs.plugins.flipcash.kmp.app.library)
    alias(libs.plugins.flipcash.kmp.js)
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
    }
}
