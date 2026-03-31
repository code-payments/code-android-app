plugins {
    alias(libs.plugins.flipcash.android.library)
    id("com.google.devtools.ksp")
}

android {
    namespace = "${Gradle.codeNamespace}.services.common"

    defaultConfig {
        consumerProguardFiles("consumer-rules.pro")
    }

    buildFeatures {
        buildConfig = true
    }
}

dependencies {
    api(project(":libs:datetime"))
    api(project(":libs:encryption:base58"))
    api(project(":libs:encryption:ed25519"))
    api(project(":libs:encryption:hmac"))
    api(project(":libs:encryption:keys"))
    api(project(":libs:encryption:mnemonic"))
    api(project(":libs:encryption:sha256"))
    api(project(":libs:encryption:sha512"))
    api(project(":libs:encryption:utils"))
    api(project(":libs:currency"))
    api(project(":libs:crypto:kin"))
    api(project(":libs:crypto:solana"))
    api(project(":libs:models"))
    api(project(":libs:logging"))
    api(project(":libs:network:exchange"))
    api(project(":libs:network:connectivity:public"))
    implementation(project(":ui:resources"))
    implementation(project(":vendor:kik:scanner"))

    implementation(libs.rxjava)
    implementation(libs.kotlinx.serialization.json)
    implementation(libs.javax.inject)

    implementation(libs.grpc.okhttp)
    implementation(libs.grpc.kotlin)
    implementation(libs.androidx.lifecycle.runtime)
    implementation(libs.androidx.room.runtime)
    implementation(libs.androidx.room.ktx)
    implementation(libs.androidx.room.rxjava3)
    implementation(libs.androidx.room.paging)
    implementation(libs.okhttp)
    implementation(libs.mixpanel)

    implementation(platform(libs.firebase.bom))
    implementation(libs.firebase.crashlytics)
    implementation(libs.firebase.installations)
    implementation(libs.firebase.perf)
    implementation(libs.firebase.messaging)

    implementation(libs.play.integrity)

    implementation(libs.androidx.paging.runtime)

    ksp(libs.androidx.room.compiler)
    implementation(libs.sqlcipher)

    implementation(libs.fingerprint.pro)

    implementation(libs.lib.phone.number.google)

    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.junit)
    androidTestImplementation(libs.androidx.test.runner)
    implementation(libs.hilt.android)

    implementation(libs.bugsnag)
}
