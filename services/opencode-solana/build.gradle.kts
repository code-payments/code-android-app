plugins {
    alias(libs.plugins.flipcash.kmp.app.library)
    alias(libs.plugins.flipcash.kmp.apple)
    alias(libs.plugins.flipcash.kmp.js)
    alias(libs.plugins.flipcash.kmp.test.fixtures)
}

// Compiles `src/commonTest/resources` into a generated `TestFixtures.kt` on `commonTest`, readable
// from every target -- see the `flipcash.kmp.test.fixtures` convention plugin.
testFixtures {
    packageName = "com.getcode.opencode.solana"
}

// Solana transaction encoding for the OpenCode protocol: Message/VersionedMessage, SolanaTransaction,
// program instruction builders and PDA derivation. No protos, gRPC or DI.
kotlin {
    android {
        namespace = "${Gradle.codeNamespace}.services.opencode.solana"
    }

    sourceSets {
        all {
            languageSettings.optIn("com.getcode.opencode.annotations.InternalOpenCodeApi")
        }
        commonMain.dependencies {
            api(project(":libs:encryption:base58"))
            api(project(":libs:encryption:ed25519"))
            api(project(":libs:encryption:keys"))
            api(project(":libs:encryption:sha256"))
            api(project(":libs:encryption:utils"))
            implementation(project(":libs:logging"))
            implementation(libs.kotlinx.serialization.json)
        }
        commonTest.dependencies {
            implementation(kotlin("test"))
        }
    }
}
