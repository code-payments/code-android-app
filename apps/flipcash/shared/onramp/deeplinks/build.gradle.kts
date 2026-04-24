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

    implementation(project(":apps:flipcash:shared:analytics"))
    implementation(project(":libs:crypto:solana"))
    implementation(project(":libs:messaging"))
}
