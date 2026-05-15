plugins {
    alias(libs.plugins.flipcash.android.feature)
}

android {
    namespace = "${Gradle.flipcashNamespace}.features.contact.verification"
}

dependencies {
    testImplementation(kotlin("test"))
    testImplementation(libs.bundles.unit.testing)
    testImplementation(libs.mockito.kotlin)
    testImplementation(project(":libs:test-utils"))

    implementation(libs.bundles.kotlinx.serialization)

    implementation(project(":apps:flipcash:shared:analytics"))
    implementation(project(":apps:flipcash:shared:phone"))
    implementation(project(":libs:messaging"))
}
