plugins {
    alias(libs.plugins.flipcash.android.feature)
}

android {
    namespace = "${Gradle.flipcashNamespace}.features.lab"
}

dependencies {
    implementation(project(":apps:flipcash:shared:featureflags"))
    implementation(project(":apps:flipcash:shared:userflags"))
    implementation(project(":libs:messaging"))

    implementation(libs.ocp.client.protocol)
    implementation(libs.flipcash2.client.protocol)

    testImplementation(kotlin("test"))
    testImplementation(libs.bundles.unit.testing)
    testImplementation(libs.bundles.compose.ui.testing)
}
