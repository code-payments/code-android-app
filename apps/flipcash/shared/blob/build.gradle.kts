plugins {
    alias(libs.plugins.flipcash.android.library)
    alias(libs.plugins.kotlin.serialization)
}

android {
    namespace = "${Gradle.flipcashNamespace}.shared.blob"
}

dependencies {
    implementation(libs.bundles.hilt)
    implementation(libs.androidx.datastore)
    implementation(libs.bundles.kotlinx.serialization)

    implementation(project(":libs:coroutines"))
    implementation(project(":services:flipcash"))
    implementation(project(":ui:resources"))

    testImplementation(kotlin("test"))
    testImplementation(libs.bundles.unit.testing)
    testImplementation(libs.robolectric)
}
