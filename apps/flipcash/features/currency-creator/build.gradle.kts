plugins {
    alias(libs.plugins.flipcash.android.feature)
}

android {
    namespace = "${Gradle.flipcashNamespace}.features.currencycreator"
}

dependencies {
    implementation(project(":apps:flipcash:features:bill-customization"))
    implementation(project(":apps:flipcash:shared:bills"))
    implementation(project(":apps:flipcash:shared:onramp:deeplinks"))
    implementation(project(":apps:flipcash:shared:payments"))
    implementation(project(":apps:flipcash:shared:session"))
    implementation(project(":apps:flipcash:shared:tokens"))
    implementation(project(":apps:flipcash:shared:userflags"))
    implementation(project(":libs:messaging"))

    implementation(project(":services:flipcash"))
    implementation(project(":services:opencode"))
}
