plugins {
    alias(libs.plugins.flipcash.android.feature)
}

android {
    namespace = "${Gradle.flipcashNamespace}.shared.anaylytics"
    testFixtures {
        enable = true
    }
}

dependencies {
    testFixturesImplementation(platform(libs.compose.bom))
    testFixturesImplementation(libs.compose.ui)

    testImplementation(kotlin("test"))
    testImplementation(libs.bundles.unit.testing)
    testImplementation(libs.robolectric)

    implementation(platform(libs.firebase.bom))
    implementation(libs.firebase.messaging)
    implementation(libs.bugsnag)

    api(project(":libs:analytics-events"))
    implementation(project(":services:flipcash"))
    implementation(project(":services:opencode"))

    implementation(libs.mixpanel)

    implementation(libs.androidx.datastore)
}
