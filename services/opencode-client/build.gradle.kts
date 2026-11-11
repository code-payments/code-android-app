import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import org.jetbrains.kotlin.gradle.targets.js.testing.KotlinJsTest

// OCP read calls and the SubmitIntent encoding in common code, over :services:opencode-transport's
// GrpcTransport. Wire messages stay internal; the API takes and returns the domain types in
// :services:opencode-model and :services:opencode-intents.
//
// Same targets as :services:opencode-transport and for the same reason: the Wire artifact `api`s
// protovalidate-runtime 0.2.0 while the app pins 0.1.3, so there is no android target and nothing on
// Android's classpath depends on this module yet.
plugins {
    alias(libs.plugins.kotlin.multiplatform)
    alias(libs.plugins.flipcash.kmp.js)
    alias(libs.plugins.flipcash.kmp.test.fixtures)
}

testFixtures {
    packageName = "com.getcode.opencode.client"
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
        all {
            languageSettings.optIn("com.getcode.opencode.annotations.InternalOpenCodeApi")
        }
        commonMain.dependencies {
            api(project(":services:opencode-transport"))
            api(project(":services:opencode-intents"))
            implementation(libs.ocp.client.protocol.wire)
            implementation(libs.kotlinx.coroutines.core)
        }
        commonTest {
            // The domain rendering :services:opencode's OcpClientFixtureTest pins, compiled into both.
            kotlin.srcDir("src/parityShared/kotlin")
            dependencies {
                implementation(kotlin("test"))
                implementation(libs.kotlinx.coroutines.test)
                implementation(libs.kotlinx.serialization.json)
            }
        }
    }
}

// The live tests call the production OCP server (read calls only), so they run only when OCP_LIVE=1.
// A recovery phrase in FLIPCASH_TEST_MNEMONIC adds a GetTokenAccountInfos call for that account.
val liveEnv = listOf("OCP_LIVE", "FLIPCASH_TEST_MNEMONIC")
tasks.withType<KotlinJsTest>().configureEach {
    liveEnv.forEach { name -> providers.environmentVariable(name).orNull?.let { environment(name, it) } }
}
tasks.withType<Test>().configureEach {
    liveEnv.forEach { name -> providers.environmentVariable(name).orNull?.let { environment(name, it) } }
}
