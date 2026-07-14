plugins {
    alias(libs.plugins.flipcash.android.feature)
}

android {
    namespace = "${Gradle.flipcashNamespace}.shared.contacts"
}

dependencies {
    testImplementation(kotlin("test"))
    testImplementation(libs.bundles.unit.testing)
    testImplementation(libs.robolectric)

    implementation(project(":services:flipcash"))
    implementation(project(":services:opencode"))
    implementation(project(":apps:flipcash:shared:analytics"))
    implementation(project(":apps:flipcash:shared:persistence:db"))
    implementation(project(":apps:flipcash:shared:persistence:sources"))
    implementation(project(":apps:flipcash:shared:phone"))
    implementation(project(":apps:flipcash:shared:featureflags"))
    implementation(project(":libs:encryption:keys"))
    implementation(project(":libs:network:connectivity:public"))
    implementation(libs.androidx.datastore)
    implementation(libs.androidx.lifecycle.process)
    implementation(libs.bundles.room)
}
