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

    implementation(libs.bundles.kotlinx.serialization)

    implementation(libs.androidx.paging.runtime)

    implementation(project(":apps:flipcash:shared:persistence:sources"))
    implementation(project(":apps:flipcash:shared:persistence:db"))
    implementation(project(":apps:flipcash:shared:contacts"))
    implementation(project(":apps:flipcash:shared:featureflags"))
    implementation(project(":apps:flipcash:shared:tokens"))
    implementation(project(":services:flipcash"))
    implementation(project(":libs:network:connectivity:public"))
    implementation(libs.androidx.lifecycle.process)
}
