plugins {
    alias(libs.plugins.flipcash.android.library.compose)
}

android {
    namespace = "${Gradle.flipcashNamespace}.cardexpand"
}

dependencies {
    implementation(libs.compose.foundation)

    testImplementation(kotlin("test"))
    testImplementation(libs.bundles.unit.testing)
}
