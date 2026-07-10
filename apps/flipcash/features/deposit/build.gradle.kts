plugins {
    alias(libs.plugins.flipcash.android.feature)
}

android {
    namespace = "${Gradle.flipcashNamespace}.features.deposit"
}

dependencies {
    testImplementation(kotlin("test"))

    implementation(project(":libs:messaging"))

    implementation(project(":apps:flipcash:shared:analytics"))
    implementation(project(":apps:flipcash:shared:featureflags"))
    implementation(project(":apps:flipcash:shared:tokens"))
    implementation(project(":services:flipcash"))

}
