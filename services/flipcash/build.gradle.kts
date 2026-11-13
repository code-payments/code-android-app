plugins {
    alias(libs.plugins.flipcash.kmp.app.library)
    alias(libs.plugins.kotlin.ksp)
    id("org.jetbrains.kotlin.plugin.parcelize")
}

kotlin {
    android {
        namespace = "${Gradle.codeNamespace}.services.flipcash"
        optimization {
            consumerKeepRules.apply {
                publish = true
                file("consumer-rules.pro")
            }
        }
    }

    sourceSets {
        commonMain.dependencies {
            implementation(libs.flipcash2.client.protocol)
            api(project(":libs:network:jwt"))
            api(project(":services:opencode"))
            implementation(project(":ui:resources"))
            // `api`: ChatContentCrypto and ChatKeySource name ChatCipher and its KeyPair.
            api(project(":libs:encryption:chat-cipher"))

            implementation(libs.bundles.hilt.common)
            implementation(libs.kotlinx.serialization.json)
            implementation(libs.grpc.okhttp)
            implementation(libs.grpc.kotlin)
            implementation(libs.grpc.protobuf.lite)
            implementation(libs.grpc.stub)
            implementation(libs.protobuf.validate.runtime)
            implementation(libs.androidx.paging.common)
            implementation(libs.okhttp)
            implementation(libs.event.bus)
        }
        androidMain.dependencies {
            implementation(libs.grpc.android)
            implementation(libs.androidx.lifecycle.runtime)
            implementation(libs.androidx.lifecycle.process)
            implementation(libs.mixpanel)
            implementation(project.dependencies.platform(libs.firebase.bom))
            implementation(libs.firebase.installations)
            implementation(libs.play.integrity)
            implementation(libs.fingerprint.pro)
            implementation(libs.hilt.android)

            // Not imported by this module; kept so the app's runtime classpath is unchanged.
            implementation(libs.androidx.room.runtime)
            implementation(libs.androidx.room.ktx)
            implementation(libs.androidx.room.paging)
            implementation(libs.androidx.paging.runtime)
        }
        getByName("androidHostTest").dependencies {
            implementation(kotlin("test"))
            implementation(libs.bundles.unit.testing)
            implementation(project(":services:flipcash:test-fixtures"))
        }
        jvmTest.dependencies {
            implementation(kotlin("test"))
            implementation(libs.kotlinx.coroutines.core)
            // LiveResponseParityTest parses OCP GetIntentMetadata responses with protobuf-java.
            implementation(libs.ocp.client.protocol)
        }
    }
}

tasks.named<Test>("jvmTest") {
    // ChatEnvelopeFixtureTest and ChatClientFixtureTest record Android's chat bytes into the
    // fixtures :services:flipcash-chat and :services:flipcash-client check their Wire code against.
    // Run with -Dfixture.update=true to rewrite them.
    val vectors = rootProject.file("libs/encryption/chat-cipher/src/commonTest/resources")
    val fixture = rootProject.file("services/flipcash-chat/src/commonTest/resources/chat_envelopes.json")
    systemProperty("chatCipherVectors", vectors.absolutePath)
    systemProperty("chatEnvelopeFixture", fixture.absolutePath)
    systemProperty(
        "flipcashClientFixture",
        rootProject.file("services/flipcash-client/src/commonTest/resources/flipcash_client.json").absolutePath,
    )
    inputs.dir(vectors)
    providers.systemProperty("fixture.update").orNull?.let { systemProperty("fixture.update", it) }
    // LiveResponseParityTest compares the responses :services:flipcash-client's LiveChatReadTest
    // wrote to FLIPCASH_LIVE_DUMP; it skips when that is unset.
    providers.environmentVariable("FLIPCASH_LIVE_DUMP").orNull?.let {
        systemProperty("flipcashLiveDump", it)
        outputs.upToDateWhen { false }
    }
}

dependencies {
    add("kspAndroid", libs.hilt.android.compiler)
    add("kspAndroid", libs.hilt.compiler)
    add("kspJvm", libs.dagger.compiler)
    add("jvmMainImplementation", libs.dagger)
}
