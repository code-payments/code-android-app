plugins {
    alias(libs.plugins.flipcash.kmp.app.library)
    alias(libs.plugins.compose.compiler)
    alias(libs.plugins.jetbrains.compose)
}

// Link a desktop to a phone over the LAN. The protocol, crypto, state and screens are common; the
// platform pieces are the seed store (jvmMain: OS keyring) and the camera, which the Android app
// passes into the phone screen.
kotlin {
    android {
        namespace = "${Gradle.flipcashNamespace}.shared.devicelink"
    }

    sourceSets {
        commonMain.dependencies {
            implementation(libs.kotlinx.coroutines.core)
            implementation(libs.sodium.bindings.kmp)
            implementation(libs.ktor.network)
            implementation(libs.qrose)
            implementation(libs.jetbrains.compose.runtime)
            implementation(libs.jetbrains.compose.foundation)
            implementation(libs.jetbrains.compose.ui)
            implementation(libs.jetbrains.compose.material)
            implementation(project(":ui:theme"))
        }
        jvmMain.dependencies {
            implementation(libs.java.keyring)
        }
        // jvmTest, not commonTest: libsodium's Android binding loads its .so from an APK, so the
        // protocol can't run in an Android host test (the same reason chat-cipher has no host test).
        jvmTest.dependencies {
            implementation(kotlin("test"))
            implementation(libs.kotlinx.coroutines.test)
        }
    }
}

tasks.withType<Test>().configureEach {
    if (providers.gradleProperty("flipcash.keyringTest").isPresent) systemProperty("flipcash.keyringTest", "true")
}
