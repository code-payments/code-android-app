plugins {
    alias(libs.plugins.flipcash.android.feature)
}

android {
    namespace = "${Gradle.flipcashNamespace}.shared.workers"
}

dependencies {
    implementation(libs.androidx.work)
    implementation(libs.bundles.kotlinx.serialization)

    implementation(libs.hilt.worker)

    implementation(project(":apps:flipcash:shared:authentication"))
    implementation(project(":apps:flipcash:shared:tokens"))
    implementation(project(":services:flipcash"))

    testImplementation(kotlin("test"))
    testImplementation(libs.bundles.unit.testing)
    testImplementation(libs.robolectric)
    testImplementation(libs.androidx.work.testing)
    testImplementation(project(":libs:test-utils"))
}
