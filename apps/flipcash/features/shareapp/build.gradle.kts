plugins {
    alias(libs.plugins.flipcash.android.feature)
}

android {
    namespace = "${Gradle.flipcashNamespace}.features.shareapp"
}

dependencies {
    implementation(project(":apps:flipcash:shared:shareable"))

    implementation(project(":libs:datetime"))
    implementation(project(":libs:quickresponse"))
}
