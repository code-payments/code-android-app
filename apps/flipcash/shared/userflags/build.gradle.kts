plugins {
    alias(libs.plugins.flipcash.android.library)
}

android {
    namespace = "${Gradle.flipcashNamespace}.shared.userflags"
}

dependencies {
    implementation(libs.bundles.hilt)
    implementation(libs.androidx.datastore)

    implementation(project(":libs:coroutines"))
    implementation(project(":libs:datetime"))
    implementation(project(":libs:messaging"))

    implementation(project(":services:flipcash"))
    implementation(project(":services:opencode"))

    testImplementation(kotlin("test"))
    testImplementation(libs.bundles.unit.testing)
    testImplementation(libs.mockito.kotlin)
    testImplementation(project(":libs:test-utils"))
}
