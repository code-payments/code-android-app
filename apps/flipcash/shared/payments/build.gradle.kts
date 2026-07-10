plugins {
    alias(libs.plugins.flipcash.android.feature)
}

android {
    namespace = "${Gradle.flipcashNamespace}.shared.payments"
}

dependencies {
    implementation(project(":apps:flipcash:shared:analytics"))
    implementation(project(":apps:flipcash:shared:featureflags"))
    implementation(project(":apps:flipcash:shared:tokens:core"))
    implementation(project(":apps:flipcash:shared:userflags"))
    implementation(project(":libs:messaging"))
    implementation(project(":services:flipcash"))
}
