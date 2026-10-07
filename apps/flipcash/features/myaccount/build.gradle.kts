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
    testImplementation(testFixtures(project(":ui:resources")))

    implementation(libs.bundles.haze)
    implementation(libs.compose.paging)

    implementation(project(":apps:flipcash:shared:analytics"))
    implementation(project(":apps:flipcash:shared:appsettings"))
    implementation(project(":apps:flipcash:shared:appupdates"))
    implementation(project(":apps:flipcash:shared:authentication"))
    implementation(project(":apps:flipcash:shared:bill-customization"))
    implementation(project(":apps:flipcash:shared:blocklist"))
    implementation(project(":apps:flipcash:shared:chat"))
    implementation(project(":apps:flipcash:shared:common-ui"))
    implementation(project(":apps:flipcash:shared:contacts"))
    implementation(project(":apps:flipcash:shared:featureflags"))
    implementation(project(":apps:flipcash:shared:funding"))
    implementation(project(":apps:flipcash:shared:menu"))
    implementation(project(":apps:flipcash:shared:payments"))
    implementation(project(":apps:flipcash:shared:tokens:core"))
    implementation(project(":apps:flipcash:shared:userflags"))

    implementation(project(":libs:datetime"))
    implementation(project(":libs:encryption:utils"))
    implementation(project(":libs:messaging"))
    implementation(project(":libs:permissions:bindings"))
    implementation(project(":ui:biometrics"))
}
