plugins {
    alias(libs.plugins.flipcash.android.feature)
}

android {
    namespace = "${Gradle.flipcashNamespace}.features.withdrawal"
}

dependencies {
    testImplementation(kotlin("test"))
    testImplementation(libs.bundles.unit.testing)
    testImplementation(project(":libs:test-utils"))

    implementation(project(":apps:flipcash:shared:amount-entry"))
    implementation(project(":apps:flipcash:shared:activityfeed"))
    implementation(project(":apps:flipcash:shared:analytics"))
    implementation(project(":apps:flipcash:shared:tokens"))
    implementation(project(":apps:flipcash:shared:userflags"))
    implementation(project(":libs:messaging"))

    implementation(project(":services:flipcash"))

}
