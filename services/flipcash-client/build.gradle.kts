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

// KeyEnvelopeRequestTest reads flipcash-chat's envelope vectors (their Set/GetKeyEnvelope requests
// were captured from Android's ChatApi), so they are staged next to this module's own.
val stageTestVectors = tasks.register<Sync>("stageTestVectors") {
    from(rootProject.layout.projectDirectory.file("services/flipcash-chat/src/commonTest/resources/chat_envelopes.json"))
    from(layout.projectDirectory.dir("src/commonTest/resources"))
    into(layout.buildDirectory.dir("stagedTestVectors"))
}

testFixtures {
    packageName = "com.flipcash.client"
    fixtures.set(layout.dir(stageTestVectors.map { it.destinationDir }))
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
            // SignedRequestFixtureTest re-serializes each signed request with protobuf-java (lite),
            // the runtime Android sends with.
            implementation(libs.flipcash2.client.protocol)
        }
    }
}

// LiveChatReadTest calls the production Flipcash server with read calls only, so it runs only when
// FLIPCASH_LIVE=1 and the test account is given as FLIPCASH_TEST_ACCESS_KEY_FILE (the path of
// a file holding its access key words; only the path reaches Gradle).
// FLIPCASH_LIVE_DUMP names a directory for the raw responses (keep it outside the repo).
val liveVars: Map<String, String> = listOf("FLIPCASH_LIVE", "FLIPCASH_TEST_ACCESS_KEY_FILE", "FLIPCASH_LIVE_DUMP")
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

// FlipcashCanonicalSchemaTest compares the schema it rebuilds from the flipcash2 Wire artifact with
// this file, and the adapter registry the canonical-signing test uses with the second.
val canonicalSchemaFile = layout.projectDirectory
    .file("src/commonMain/kotlin/com/flipcash/client/FlipcashCanonicalSchema.kt").asFile.absolutePath
val signedFixtureFile = layout.projectDirectory
    .file("src/commonTest/resources/signed_flipcash_requests.json").asFile.absolutePath
val signedAdaptersFile = layout.projectDirectory
    .file("src/commonTest/kotlin/com/flipcash/client/SignedMessageAdapters.kt").asFile.absolutePath
tasks.named<Test>("jvmTest") {
    systemProperty("canonicalSchemaFile", canonicalSchemaFile)
    systemProperty("signedAdaptersFile", signedAdaptersFile)
    systemProperty("signedFixtureFile", signedFixtureFile)
    providers.environmentVariable("UPDATE_SIGNED_FIXTURE").orNull?.let { environment("UPDATE_SIGNED_FIXTURE", it) }
    providers.environmentVariable("UPDATE_CANONICAL_SCHEMA").orNull?.let { environment("UPDATE_CANONICAL_SCHEMA", it) }
}
