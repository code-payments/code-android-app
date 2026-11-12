import org.jetbrains.kotlin.gradle.dsl.JvmTarget

// Flipcash messaging and event-stream calls in common code, sent over :services:opencode-transport:
// the chat event stream (StreamEvents, with its ping/pong), GetReactors, and the chat payment
// AppMetadata an intent carries. Requests are flipcash2 Wire messages signed as Android's
// authenticate() signs them; the Wire classes stay internal and the public API is domain types.
//
// Not on the app's classpath: Android keeps its protobuf-java services in :services:flipcash.
//
// No android target: the transport has none. Targets are the ones both Wire artifacts publish.
plugins {
    alias(libs.plugins.kotlin.multiplatform)
    alias(libs.plugins.flipcash.kmp.js)
    alias(libs.plugins.flipcash.kmp.test.fixtures)
}

testFixtures {
    packageName = "com.flipcash.client"
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
            // GrpcTransport is part of this module's API; MessageContent comes from flipcash-chat.
            api(project(":services:opencode-transport"))
            api(project(":services:flipcash-chat"))
            implementation(libs.flipcash2.client.protocol.wire)
            implementation(libs.kotlinx.coroutines.core)
        }
        commonTest.dependencies {
            implementation(kotlin("test"))
            implementation(project(":libs:encryption:mnemonic"))
            implementation(libs.kotlinx.coroutines.test)
            implementation(libs.kotlinx.serialization.json)
        }
        jvmTest.dependencies {
            // Only for LiveChatReadTest's JVM transport; never on the main classpath.
            implementation(libs.grpc.okhttp)
            implementation(libs.grpc.stub)
        }
    }
}

// LiveChatReadTest calls the production Flipcash server with read calls only, so it runs only when
// FLIPCASH_LIVE=1 and FLIPCASH_TEST_ACCESS_KEY holds the test account's login entropy.
// FLIPCASH_LIVE_DUMP names a directory for the raw responses (keep it outside the repo).
val liveVars: Map<String, String> = listOf("FLIPCASH_LIVE", "FLIPCASH_TEST_ACCESS_KEY", "FLIPCASH_LIVE_DUMP")
    .mapNotNull { name -> providers.environmentVariable(name).orNull?.let { name to it } }
    .toMap()
val liveRun = liveVars["FLIPCASH_LIVE"] == "1"
tasks.withType<Test>().configureEach {
    environment(liveVars)
    val rerun = liveRun // a local, so the lambda below does not capture the build script
    outputs.upToDateWhen { !rerun }
}
tasks.withType<org.jetbrains.kotlin.gradle.targets.js.testing.KotlinJsTest>().configureEach {
    liveVars.forEach { (name, value) -> environment(name, value) }
    val rerun = liveRun
    outputs.upToDateWhen { !rerun }
}
