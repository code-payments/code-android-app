plugins {
    alias(libs.plugins.flipcash.android.library.compose)
}

android {
    namespace = "${Gradle.flipcashNamespace}.shared.userflags"
}

dependencies {
    implementation(libs.bundles.hilt)
    implementation(libs.androidx.datastore)
    implementation(libs.compose.foundation)
    implementation(libs.compose.ui.text)

    implementation(project(":libs:coroutines"))
    implementation(project(":libs:datetime"))
    implementation(project(":libs:messaging"))

    implementation(project(":services:flipcash"))
    implementation(project(":services:opencode"))

    testImplementation(kotlin("test"))
    testImplementation(libs.bundles.unit.testing)
    testImplementation(libs.mockito.kotlin)
}
