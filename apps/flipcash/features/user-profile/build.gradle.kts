plugins {
    alias(libs.plugins.flipcash.android.feature)
}

android {
    namespace = "${Gradle.flipcashNamespace}.features.userprofile"
}

dependencies {
    testImplementation(kotlin("test"))
    testImplementation(libs.bundles.unit.testing)
    testImplementation(testFixtures(project(":ui:resources")))
    testImplementation(libs.mockito.kotlin)

    implementation(libs.bundles.kotlinx.serialization)

    implementation(project(":apps:flipcash:shared:amount-entry"))
    implementation(project(":apps:flipcash:shared:analytics"))
    implementation(project(":apps:flipcash:shared:blob"))
    implementation(project(":apps:flipcash:shared:common-ui"))
    implementation(project(":apps:flipcash:shared:featureflags"))
    implementation(project(":apps:flipcash:shared:payments"))
    implementation(project(":apps:flipcash:shared:userflags"))

    implementation(project(":libs:messaging"))
}
