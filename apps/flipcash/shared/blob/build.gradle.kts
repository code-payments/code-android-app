plugins {
    alias(libs.plugins.flipcash.kmp.app.library)
    alias(libs.plugins.compose.compiler)
    alias(libs.plugins.jetbrains.compose)
    alias(libs.plugins.kotlin.ksp)
}

kotlin {
    android {
        namespace = "${Gradle.flipcashNamespace}.shared.blob"
    }

    sourceSets {
        commonMain.dependencies {
            implementation(project(":apps:flipcash:core"))
            implementation(project(":libs:coroutines"))
            implementation(project(":services:flipcash"))

            implementation(libs.androidx.datastore.preferences.core)
            implementation(libs.bundles.hilt.common)
            implementation(libs.bundles.kotlinx.serialization)
        }
        androidMain.dependencies {
            implementation(libs.bundles.hilt)
            implementation(project(":ui:resources"))
        }
        jvmMain.dependencies {
            // Skia decodes and encodes on desktop; the API comes with Compose UI.
            implementation(libs.jetbrains.compose.ui)
        }
        commonTest {
            dependencies {
                implementation(kotlin("test"))
                implementation(libs.kotlinx.coroutines.test)
                implementation(libs.mockk)
                // The BlurHash round-trip test decodes with the real decoder.
                implementation(project(":apps:flipcash:shared:common-ui"))
            }
        }
        jvmTest.dependencies {
            implementation(compose.desktop.currentOs)
        }
        getByName("androidHostTest").dependencies {
            implementation(kotlin("test"))
            implementation(libs.bundles.unit.testing)
            implementation(libs.robolectric)
        }
    }
}

dependencies {
    add("kspAndroid", libs.hilt.android.compiler)
    add("kspAndroid", libs.hilt.compiler)
    add("kspJvm", libs.dagger.compiler)
    add("jvmMainImplementation", libs.dagger)
}
