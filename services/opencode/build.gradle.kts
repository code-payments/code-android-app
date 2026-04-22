plugins {
    alias(libs.plugins.flipcash.android.library)
    alias(libs.plugins.flipcash.android.ed25519.shadow)
    id("com.google.devtools.ksp")
    id("dagger.hilt.android.plugin")
    id("org.jetbrains.kotlin.plugin.parcelize")
}

android {
    namespace = "${Gradle.codeNamespace}.services.opencode"

    defaultConfig {
        consumerProguardFiles("consumer-rules.pro")

        buildConfigField("Boolean", "NOTIFY_ERRORS", "false")
        buildConfigField(
            "String",
            "GOOGLE_CLOUD_PROJECT_NUMBER",
            "\"${tryReadProperty(rootProject.rootDir, "GOOGLE_CLOUD_PROJECT_NUMBER", "-1L")}\""
        )
    }

    buildFeatures {
        buildConfig = true
    }

}

dependencies {
    implementation(project(":definitions:opencode:models"))
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
    api(project(":libs:locale:bindings"))
    implementation(project(":libs:locale:impl"))
    api(project(":libs:network:connectivity:bindings"))
    implementation(project(":ui:resources"))

    api(project(":vendor:kik:scanner"))

    implementation(libs.protobuf.validate.runtime)

    implementation(libs.javax.inject)

    implementation(libs.kotlinx.serialization.json)
    implementation(libs.javax.inject)

    implementation(libs.grpc.android)
    implementation(libs.grpc.okhttp)
    implementation(libs.grpc.kotlin)
    implementation(libs.grpc.protobuf.lite)
    implementation(libs.androidx.lifecycle.runtime)
    implementation(libs.androidx.lifecycle.process)
    implementation(libs.androidx.datastore)
    implementation(libs.okhttp)
    implementation(libs.mixpanel)

    implementation(platform(libs.firebase.bom))
    implementation(libs.firebase.installations)
    implementation(libs.firebase.perf)
    implementation(libs.firebase.messaging)

    implementation(libs.play.integrity)

    implementation(libs.androidx.paging.runtime)

    ksp(libs.androidx.room.compiler)

    implementation(libs.fingerprint.pro)

    implementation(libs.lib.phone.number.google)

    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.junit)
    androidTestImplementation(libs.androidx.test.runner)

    implementation(libs.retrofit)
    implementation(libs.retrofit.converter.gson)
    implementation(libs.okhttp.logging.interceptor)

    implementation(libs.hilt.android)
    ksp(libs.hilt.android.compiler)
    ksp(libs.hilt.compiler)

    implementation(libs.event.bus)

    testImplementation(kotlin("test"))
    testImplementation(libs.bundles.unit.testing)
}
