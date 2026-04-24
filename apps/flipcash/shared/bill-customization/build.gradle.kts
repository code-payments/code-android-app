plugins {
    alias(libs.plugins.flipcash.android.feature)
}

android {
    namespace = "${Gradle.flipcashNamespace}.shared.bill.customizations"
}

dependencies {
    implementation(libs.bundles.kotlinx.serialization)

    implementation(project(":apps:flipcash:shared:featureflags"))
    implementation(project(":apps:flipcash:shared:router"))
    implementation(project(":libs:messaging"))
}
