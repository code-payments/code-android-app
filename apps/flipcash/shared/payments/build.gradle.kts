plugins {
    alias(libs.plugins.flipcash.android.feature)
}

android {
    namespace = "${Gradle.flipcashNamespace}.shared.payments"
}

dependencies {
    implementation(libs.androidx.localbroadcastmanager)

    implementation(project(":apps:flipcash:shared:activityfeed"))
    implementation(project(":libs:messaging"))

    testImplementation(kotlin("test"))
    testImplementation(libs.bundles.unit.testing)
    testImplementation(libs.kotlin.reflect)
    testImplementation(project(":libs:test-utils"))
}
