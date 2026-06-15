plugins {
    alias(libs.plugins.flipcash.android.feature)
}

android {
    namespace = "${Gradle.flipcashNamespace}.features.messenger"
}

dependencies {
    implementation(project(":apps:flipcash:shared:chat"))
    implementation(project(":apps:flipcash:shared:amount-entry"))
    implementation(project(":apps:flipcash:shared:contacts"))
    implementation(project(":apps:flipcash:shared:featureflags"))
    implementation(project(":apps:flipcash:shared:payments"))
    implementation(project(":apps:flipcash:shared:tokens"))
    implementation(project(":libs:messaging"))
    implementation(project(":services:flipcash"))
    implementation(project(":services:opencode"))
    implementation(project(":libs:datetime"))
    implementation(libs.compose.paging)
    implementation(libs.bundles.haze)
}
