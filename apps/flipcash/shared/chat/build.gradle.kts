plugins {
    alias(libs.plugins.flipcash.android.feature)
}

android {
    namespace = "${Gradle.flipcashNamespace}.shared.chat"
}

dependencies {
    testImplementation(kotlin("test"))
    testImplementation(libs.bundles.unit.testing)
    testImplementation(libs.robolectric)
    testImplementation(libs.androidx.paging.testing)

    implementation(libs.bundles.kotlinx.serialization)

    implementation(libs.androidx.paging.runtime)

    implementation(project(":apps:flipcash:shared:persistence:sources"))
    implementation(project(":apps:flipcash:shared:persistence:db"))
    implementation(project(":apps:flipcash:shared:contacts"))
    // `api`, not `implementation`: GroupAccess.groupAccess is an extension on TokenCoordinator,
    // so this module's public surface names a type from it.
    api(project(":apps:flipcash:shared:tokens"))
    implementation(project(":apps:flipcash:shared:analytics"))
    testImplementation(testFixtures(project(":apps:flipcash:shared:analytics")))
    implementation(project(":services:flipcash"))
    implementation(project(":libs:network:connectivity:public"))
    implementation(project(":libs:emojis"))
    implementation(libs.androidx.lifecycle.process)
}
