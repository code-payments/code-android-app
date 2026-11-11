plugins {
    alias(libs.plugins.flipcash.kmp.app.library)
}

// OpenCode domain types (accounts, mints, rates, fiat amounts, swap parameters) with no protos,
// gRPC or DI, plus the swap instruction builders and TransactionBuilder, which take these types.
// Types that wrap verified proto state (ExchangeData, LocalFiat, AccountInfo, ...) stay in
// :services:opencode.
kotlin {
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
