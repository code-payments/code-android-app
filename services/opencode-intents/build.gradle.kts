plugins {
    alias(libs.plugins.flipcash.kmp.app.library)
    alias(libs.plugins.flipcash.kmp.apple)
    alias(libs.plugins.flipcash.kmp.js)
    alias(libs.plugins.flipcash.kmp.test.fixtures)
}

testFixtures {
    packageName = "com.getcode.opencode.intents"
}

// OCP intent construction and signing: IntentType/ActionType, the transfer, withdraw, remote send,
// create account and distribution intents, their actions and compact-message signing, and the
// domain metadata they carry (TransactionMetadata, ExchangeData, LocalFiat). No protos, gRPC or DI:
// :services:opencode encodes these with protobuf-java and :services:opencode-client with Wire.
kotlin {
    android {
        namespace = "${Gradle.codeNamespace}.services.opencode.intents"
    }

    sourceSets {
        all {
            languageSettings.optIn("com.getcode.opencode.annotations.InternalOpenCodeApi")
        }
        commonMain.dependencies {
            api(project(":services:opencode-model"))
            api(project(":services:opencode-solana"))
            api(project(":libs:encryption:ed25519"))
            api(project(":libs:encryption:keys"))
            api(project(":libs:encryption:sha256"))
            api(project(":libs:encryption:utils"))
            implementation(libs.kotlinx.serialization.json)
        }
        commonTest.dependencies {
            implementation(kotlin("test"))
            implementation(project(":libs:encryption:mnemonic"))
        }
    }
}
