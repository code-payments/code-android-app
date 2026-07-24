plugins {
    alias(libs.plugins.flipcash.android.feature)
}

android {
    namespace = "${Gradle.flipcashNamespace}.shared.bills"
}

dependencies {
    implementation(platform(libs.firebase.bom))
    implementation(libs.firebase.messaging)

    implementation(project(":libs:messaging"))
    implementation(project(":apps:flipcash:shared:common-ui"))

    implementation(libs.androidx.datastore)
    implementation(libs.bundles.haze)
}
