plugins {
    alias(libs.plugins.flipcash.android.feature)
}

android {
    namespace = "${Gradle.flipcashNamespace}.features.search"
}

dependencies {
    testImplementation(kotlin("test"))
    testImplementation(libs.bundles.unit.testing)
    testImplementation(libs.robolectric)
    testImplementation(project(":apps:flipcash:shared:analytics:test-fixtures"))
    testImplementation(project(":libs:coroutines:test-fixtures"))

    implementation(project(":services:flipcash"))
    implementation(project(":services:opencode"))
    implementation(project(":apps:flipcash:shared:analytics"))
    implementation(project(":apps:flipcash:shared:chat"))
    implementation(project(":apps:flipcash:shared:chat-ui"))
    implementation(project(":apps:flipcash:shared:persistence:sources"))
    implementation(project(":apps:flipcash:shared:tokens"))
    implementation(project(":libs:datetime"))
    implementation(libs.androidx.datastore)
    implementation(libs.coil3)
}
