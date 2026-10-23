plugins {
    alias(libs.plugins.flipcash.kmp.app.library)
    alias(libs.plugins.kotlin.ksp)
    id("org.jetbrains.kotlin.plugin.parcelize")
    alias(libs.plugins.metro)
}

// Metro generates this module's provider factories for the desktop graph. Through Dagger's factories
// instead, Metro 1.4.5 drops provider-level @Singleton (it does not read @ScopeMetadata), so each
// injection site got its own ChannelEventBus.
metro {
    interop {
        includeJavax()
        includeDagger()
    }
}

kotlin {
    android {
        namespace = "${Gradle.codeNamespace}.services.opencode"
        androidResources { enable = true }
    }

    sourceSets {
        commonMain.dependencies {
            implementation(libs.ocp.client.protocol)
            api(project(":libs:currency-math"))
            api(project(":libs:datetime"))
            api(project(":libs:encryption:base58"))
            api(project(":libs:encryption:ed25519"))
            api(project(":libs:encryption:hmac"))
            api(project(":libs:encryption:keys"))
            api(project(":libs:encryption:mnemonic"))
            api(project(":libs:encryption:sha256"))
            api(project(":libs:encryption:sha512"))
            api(project(":libs:encryption:utils"))
            api(project(":libs:logging"))
            api(project(":libs:locale:public"))
            api(project(":libs:network:connectivity:public"))
            implementation(project(":ui:resources"))

            implementation(libs.protobuf.validate.runtime)
            implementation(libs.bundles.hilt.common)
            implementation(libs.kotlinx.serialization.json)
            implementation(libs.kotlinx.datetime)
            implementation(libs.kotlinx.coroutines.core)
            implementation(libs.grpc.okhttp)
            implementation(libs.grpc.kotlin)
            implementation(libs.grpc.protobuf.lite)
            implementation(libs.event.bus)
        }
        androidMain.dependencies {
            api(project(":libs:locale:bindings"))
            implementation(project(":libs:locale:impl"))
            api(project(":libs:network:connectivity:bindings"))
            api(project(":vendor:kik:scanner"))

            implementation(libs.grpc.android)
            implementation(libs.androidx.lifecycle.runtime)
            implementation(libs.androidx.lifecycle.process)
            implementation(libs.hilt.android)

            // Not imported by this module; kept so the app's runtime classpath is unchanged.
            implementation(libs.androidx.datastore)
            implementation(libs.okhttp)
            implementation(libs.mixpanel)
            implementation(project.dependencies.platform(libs.firebase.bom))
            implementation(libs.firebase.installations)
            implementation(libs.firebase.perf)
            implementation(libs.firebase.messaging)
            implementation(libs.play.integrity)
            implementation(libs.androidx.paging.runtime)
            implementation(libs.fingerprint.pro)
            implementation(libs.retrofit)
            implementation(libs.retrofit.converter.gson)
            implementation(libs.okhttp.logging.interceptor)
        }
        getByName("androidHostTest").dependencies {
            implementation(kotlin("test"))
            implementation(libs.bundles.unit.testing)
            implementation(project(":libs:currency-math:test-fixtures"))
            implementation(project(":libs:test-utils"))
        }
        jvmTest.dependencies {
            implementation(kotlin("test"))
            implementation(libs.kotlinx.coroutines.test)
        }
    }
}

dependencies {
    add("kspAndroid", libs.hilt.android.compiler)
    add("kspAndroid", libs.hilt.compiler)
    add("jvmMainImplementation", libs.dagger)
}

// The headless balance test reads its recovery phrase from local.properties (gitignored) or the
// FLIPCASH_TEST_MNEMONIC environment variable, and skips when neither is set.
tasks.named<Test>("jvmTest") {
    val mnemonic = tryReadProperty(rootProject.rootDir, "flipcash.test.mnemonic", "")
    if (mnemonic.isNotBlank()) systemProperty("flipcash.test.mnemonic", mnemonic)
}
