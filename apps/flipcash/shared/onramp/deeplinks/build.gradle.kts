plugins {
    alias(libs.plugins.flipcash.android.feature)
}

android {
    namespace = "${Gradle.flipcashNamespace}.shared.onramp.deeplinks"
}

dependencies {
    testImplementation(kotlin("test"))
    testImplementation(libs.bundles.unit.testing)
    testImplementation(project(":libs:test-utils"))

    implementation(libs.bundles.kotlinx.serialization)
    implementation(libs.phantom.connect) {
        exclude(group = "com.ionspin.kotlin", module = "multiplatform-crypto-libsodium-bindings-android-debug")
    }
    implementation(libs.phantom.connect.wallet) {
        exclude(group = "com.ionspin.kotlin", module = "multiplatform-crypto-libsodium-bindings-android-debug")
    }

    implementation(project(":apps:flipcash:shared:analytics"))
    implementation(project(":apps:flipcash:shared:userflags"))
    implementation(project(":libs:crypto:solana"))
    implementation(project(":libs:messaging"))
}
