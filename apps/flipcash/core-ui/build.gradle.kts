plugins {
    alias(libs.plugins.flipcash.android.feature)
}

android {
    namespace = "${Gradle.flipcashNamespace}.core.ui"
}

dependencies {
    implementation(project(":apps:flipcash:shared:featureflags"))

    implementation(libs.compose.material3)
    implementation(libs.bundles.haze)

    testImplementation(kotlin("test"))
    testImplementation(libs.bundles.unit.testing)
    testImplementation(libs.robolectric)
}
