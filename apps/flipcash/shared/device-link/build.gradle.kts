plugins {
    alias(libs.plugins.flipcash.kmp.app.library)
    alias(libs.plugins.compose.compiler)
    alias(libs.plugins.jetbrains.compose)
}

// The link screens and the desktop seed store. The protocol, crypto and phone state are in
// :libs:device-link, which has no UI so iOS can use it through SharedCore. The platform pieces here
// are the seed store (jvmMain: OS keyring) and the camera, which the Android app passes into the
// phone screen.
kotlin {
    android {
        namespace = "${Gradle.flipcashNamespace}.shared.devicelink"
    }

    sourceSets {
        commonMain.dependencies {
            implementation(libs.kotlinx.coroutines.core)
            api(project(":libs:device-link"))
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
        // jvmTest only for the keyring test, which needs a desktop OS keyring.
        jvmTest.dependencies {
            implementation(kotlin("test"))
        }
    }
}

tasks.withType<Test>().configureEach {
    if (providers.gradleProperty("flipcash.keyringTest").isPresent) systemProperty("flipcash.keyringTest", "true")
}
