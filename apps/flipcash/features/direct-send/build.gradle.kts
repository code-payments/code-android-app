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

    implementation(libs.kotlin.stdlib)
    implementation(project(":apps:flipcash:shared:analytics"))
    implementation(project(":libs:datetime"))
    implementation(project(":libs:logging"))
    implementation(project(":libs:messaging"))
    implementation(project(":libs:permissions:bindings"))
    implementation(project(":apps:flipcash:shared:featureflags"))
    implementation(project(":apps:flipcash:shared:permissions"))
    implementation(project(":apps:flipcash:shared:contacts"))
    implementation(project(":services:flipcash"))
}
