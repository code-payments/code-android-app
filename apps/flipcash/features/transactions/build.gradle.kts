plugins {
    alias(libs.plugins.flipcash.android.feature)
}

android {
    namespace = "${Gradle.flipcashNamespace}.features.transactions"
}

dependencies {
    testImplementation(kotlin("test"))
    testImplementation(libs.bundles.unit.testing)
    testImplementation(project(":ui:resources:test-fixtures"))
    testImplementation(libs.mockito.kotlin)

    implementation(libs.compose.paging)

    implementation(project(":apps:flipcash:shared:transaction-history"))
    implementation(project(":apps:flipcash:shared:blocklist"))
    implementation(project(":apps:flipcash:shared:featureflags"))
    implementation(project(":apps:flipcash:shared:tokens"))

    implementation(project(":libs:datetime"))
    implementation(project(":libs:messaging"))
}
