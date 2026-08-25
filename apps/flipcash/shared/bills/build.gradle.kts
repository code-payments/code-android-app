plugins {
    alias(libs.plugins.flipcash.android.feature)
}

android {
    namespace = "${Gradle.flipcashNamespace}.shared.bills"
}

dependencies {
    testImplementation(kotlin("test"))
    testImplementation(libs.bundles.unit.testing)
    testImplementation(libs.bundles.compose.ui.testing)

    implementation(platform(libs.firebase.bom))
    implementation(libs.firebase.messaging)

    implementation(project(":libs:messaging"))
    implementation(project(":apps:flipcash:shared:common-ui"))
    implementation(project(":apps:flipcash:shared:featureflags"))
    implementation(project(":apps:flipcash:shared:session"))

    implementation(libs.androidx.datastore)
    implementation(libs.bundles.haze)
}
