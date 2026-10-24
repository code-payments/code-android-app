plugins {
    alias(libs.plugins.flipcash.kmp.library)
}

// The link protocol, crypto and phone-side state, with no UI, so iOS can drive it through
// SharedCore. The Compose screens and the desktop seed store live in :apps:flipcash:shared:device-link.
kotlin {
    android {
        namespace = "com.flipcash.libs.devicelink"
    }

    sourceSets {
        commonMain.dependencies {
            // StateFlow is part of PhoneLinkController's API.
            api(libs.kotlinx.coroutines.core)
            implementation(libs.sodium.bindings.kmp)
            implementation(libs.ktor.network)
        }
        jvmTest.dependencies {
            implementation(kotlin("test"))
            implementation(libs.kotlinx.coroutines.test)
        }
    }
}
