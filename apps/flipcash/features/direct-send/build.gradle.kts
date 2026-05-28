plugins {
    alias(libs.plugins.flipcash.android.feature)
}

android {
    namespace = "${Gradle.flipcashNamespace}.features.directsend"
}

dependencies {
    testImplementation(kotlin("test"))
    testImplementation(libs.bundles.unit.testing)
    testImplementation(libs.mockito.kotlin)
    testImplementation(libs.robolectric)
    testImplementation(project(":libs:test-utils"))

    implementation(libs.kotlin.stdlib)
    implementation(project(":apps:flipcash:shared:analytics"))
    implementation(project(":apps:flipcash:shared:session"))
    implementation(project(":apps:flipcash:shared:tokens"))
    implementation(project(":libs:datetime"))
    implementation(project(":libs:logging"))
    implementation(project(":libs:messaging"))
    implementation(project(":libs:permissions:bindings"))
}
