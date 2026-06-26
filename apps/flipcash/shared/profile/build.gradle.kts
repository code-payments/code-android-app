plugins {
    alias(libs.plugins.flipcash.android.library)
    alias(libs.plugins.kotlin.serialization)
}

android {
    namespace = "${Gradle.flipcashNamespace}.shared.profile"
}

dependencies {
    implementation(libs.bundles.hilt)
    implementation(libs.androidx.datastore)
    implementation(libs.bundles.kotlinx.serialization)

    implementation(project(":libs:coroutines"))
    implementation(project(":services:flipcash"))
}
