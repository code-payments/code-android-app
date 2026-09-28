plugins {
    alias(libs.plugins.flipcash.android.feature)
}

android {
    namespace = "${Gradle.flipcashNamespace}.shared.invite"
}

dependencies {
    implementation(project(":apps:flipcash:shared:shareable"))

    testImplementation(kotlin("test"))
    testImplementation(libs.bundles.unit.testing)
    testImplementation(libs.robolectric)
    testImplementation(testFixtures(project(":libs:coroutines")))
}
