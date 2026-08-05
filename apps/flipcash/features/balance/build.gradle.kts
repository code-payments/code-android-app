plugins {
    alias(libs.plugins.flipcash.android.feature)
}

android {
    namespace = "${Gradle.flipcashNamespace}.features.balance"
}

dependencies {
    testImplementation(kotlin("test"))
    testImplementation(libs.bundles.unit.testing)
    testImplementation(libs.bundles.compose.ui.testing)

    implementation(libs.compose.paging)

    implementation(project(":apps:flipcash:shared:analytics"))
    implementation(project(":apps:flipcash:shared:featureflags"))
    implementation(project(":apps:flipcash:shared:funding"))
    implementation(project(":apps:flipcash:shared:tokens"))
    implementation(project(":apps:flipcash:shared:userflags"))
    implementation(project(":libs:datetime"))
    implementation(project(":libs:messaging"))
    implementation(project(":libs:permissions:bindings"))

}
