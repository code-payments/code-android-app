plugins {
    alias(libs.plugins.flipcash.android.feature)
}

android {
    namespace = "${Gradle.flipcashNamespace}.features.advanced"
}

dependencies {
    implementation(project(":apps:flipcash:features:device-logs"))
    implementation(project(":apps:flipcash:shared:bill-customization"))
    implementation(project(":apps:flipcash:shared:featureflags"))
    implementation(project(":apps:flipcash:shared:menu"))
    implementation(project(":apps:flipcash:shared:userflags"))

    implementation(project(":libs:datetime"))
    implementation(project(":libs:messaging"))
    implementation(project(":libs:permissions:bindings"))
}
