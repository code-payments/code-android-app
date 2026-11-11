import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import org.jetbrains.kotlin.gradle.targets.js.testing.KotlinJsTest

// OCP calls in common code: Wire messages (com.flipcash:ocp-client-protocol-wire), request signing
// with our Ed25519, and a GrpcTransport each platform implements: jvmMain over grpc-java (OkHttp),
// jsMain over @grpc/grpc-js for Node.
//
// Not on Android's classpath: the app and :services:opencode do not depend on this module, and it
// has no android target. The Wire artifact `api`s protovalidate-runtime 0.2.0 while the app pins
// 0.1.3, so depending on it from an Android module would move the app's runtime classpath.
//
// Its iOS targets are the two the Wire artifact publishes (no iosX64/macOS), so this module does not
// apply flipcash.kmp.library, which declares all five Apple targets.
plugins {
    alias(libs.plugins.kotlin.multiplatform)
    alias(libs.plugins.flipcash.kmp.js)
    alias(libs.plugins.flipcash.kmp.test.fixtures)
}

testFixtures {
    packageName = "com.getcode.opencode.transport"
}

kotlin {
    jvm {
        compilerOptions {
            jvmTarget.set(JvmTarget.fromTarget(libs.versions.android.java.get()))
        }
    }
    js {
        nodejs()
    }
    iosArm64()
    iosSimulatorArm64()

    sourceSets {
        commonMain.dependencies {
            api(libs.ocp.client.protocol.wire)
            api(project(":libs:encryption:ed25519"))
            implementation(libs.kotlinx.coroutines.core)
        }
        commonTest.dependencies {
            implementation(kotlin("test"))
            implementation(libs.kotlinx.coroutines.test)
            implementation(libs.kotlinx.serialization.json)
        }
        jvmMain.dependencies {
            implementation(libs.grpc.okhttp)
            implementation(libs.grpc.kotlin)
        }
        jsMain.dependencies {
            // Exact pin, recorded in kotlin-js-store/package-lock.json.
            implementation(npm("@grpc/grpc-js", "1.14.6"))
        }
        jsTest.dependencies {
            implementation(project(":libs:encryption:mnemonic"))
        }
    }
}

// LiveAccountCallTest calls the production OCP server, so it only runs when OCP_LIVE=1. A recovery
// phrase in FLIPCASH_TEST_MNEMONIC adds a GetTokenAccountInfos call for that account.
tasks.withType<KotlinJsTest>().configureEach {
    listOf("OCP_LIVE", "FLIPCASH_TEST_MNEMONIC").forEach { name ->
        providers.environmentVariable(name).orNull?.let { environment(name, it) }
    }
}
