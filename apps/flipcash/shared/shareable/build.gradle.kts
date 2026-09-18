plugins {
    alias(libs.plugins.flipcash.android.feature)
}

android {
    namespace = "${Gradle.flipcashNamespace}.shared.shareable"
}

dependencies {
    testImplementation(kotlin("test"))
    testImplementation(libs.bundles.unit.testing)
    testImplementation(libs.robolectric)
    testImplementation(testFixtures(project(":ui:resources")))

    implementation(libs.bundles.kotlinx.serialization)

    implementation(libs.androidx.localbroadcastmanager)

    implementation(project(":libs:messaging"))
}
