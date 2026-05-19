plugins {
    alias(libs.plugins.flipcash.android.library)
    id("com.google.devtools.ksp")
}

android {
    namespace = "${Gradle.codeNamespace}.services.flipcash"

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
    implementation(project(":definitions:flipcash:models"))
    api(project(":libs:network:jwt"))
    api(project(":services:opencode"))
    implementation(project(":ui:resources"))

    implementation(libs.javax.inject)

    implementation(libs.kotlinx.serialization.json)
    implementation(libs.javax.inject)

    implementation(libs.grpc.android)
    implementation(libs.grpc.okhttp)
    implementation(libs.grpc.kotlin)
    implementation(libs.grpc.protobuf.lite)
    implementation(libs.protobuf.validate.runtime)
    implementation(libs.androidx.lifecycle.runtime)
    implementation(libs.androidx.room.runtime)
    implementation(libs.androidx.room.ktx)
    implementation(libs.androidx.room.paging)
    implementation(libs.okhttp)
    implementation(libs.mixpanel)

    implementation(platform(libs.firebase.bom))
    implementation(libs.firebase.installations)
    implementation(libs.firebase.messaging)

    implementation(libs.play.integrity)

    implementation(libs.androidx.paging.runtime)

    ksp(libs.androidx.room.compiler)

    implementation(libs.fingerprint.pro)

    implementation(libs.lib.phone.number.google)

    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.junit)
    androidTestImplementation(libs.androidx.test.runner)

    implementation(libs.hilt.android)
    ksp(libs.hilt.android.compiler)
    ksp(libs.hilt.compiler)

    implementation(libs.event.bus)

    testImplementation(kotlin("test"))
    testImplementation(libs.bundles.unit.testing)
}
