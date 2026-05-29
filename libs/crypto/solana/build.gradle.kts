plugins {
    alias(libs.plugins.flipcash.android.library)
    alias(libs.plugins.flipcash.android.ed25519.shadow)
    id("com.google.devtools.ksp")
    id("dagger.hilt.android.plugin")
}

android {
    namespace = "${Gradle.codeNamespace}.vendor.solana"
}

dependencies {
    implementation(project(":libs:encryption:base58"))
    implementation(project(":libs:encryption:ed25519"))
    implementation(project(":libs:encryption:hmac"))
    implementation(project(":libs:encryption:keys"))
    implementation(project(":libs:encryption:sha256"))
    implementation(project(":libs:encryption:sha512"))
    implementation(project(":libs:encryption:utils"))
    implementation(project(":libs:currency"))
    implementation(libs.bundles.kotlinx.serialization)

    implementation(libs.okhttp)
    implementation(libs.okhttp.logging.interceptor)

    implementation("org.sol4k:sol4k:0.5.17")
    api("com.solanamobile:web3-solana:0.2.5")
    api("com.solanamobile:rpc-core:0.2.9")
    implementation("com.solanamobile:rpc-okiodriver:0.2.9")

    implementation(libs.bundles.hilt)
    ksp(libs.bundles.hilt.compiler)

    testImplementation(kotlin("test"))
}
