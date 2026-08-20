plugins {
    alias(libs.plugins.flipcash.android.feature)
}

android {
    namespace = "${Gradle.flipcashNamespace}.features.myaccount"
}

dependencies {
    testImplementation(kotlin("test"))
    testImplementation(libs.bundles.unit.testing)
    testImplementation(libs.bundles.compose.ui.testing)

    implementation(libs.compose.paging)

    implementation(project(":apps:flipcash:shared:appsettings"))
    implementation(project(":apps:flipcash:shared:authentication"))
    implementation(project(":apps:flipcash:shared:blocklist"))
    implementation(project(":apps:flipcash:shared:common-ui"))
    implementation(project(":apps:flipcash:shared:contacts"))
    implementation(project(":apps:flipcash:shared:featureflags"))
    implementation(project(":apps:flipcash:shared:menu"))

    implementation(project(":libs:datetime"))
    implementation(project(":libs:encryption:utils"))
    implementation(project(":libs:messaging"))
    implementation(project(":libs:permissions:bindings"))
    implementation(project(":ui:biometrics"))
}
