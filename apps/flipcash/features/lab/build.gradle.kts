plugins {
    alias(libs.plugins.flipcash.android.feature)
}

android {
    namespace = "${Gradle.flipcashNamespace}.features.lab"
}

dependencies {
    implementation(project(":apps:flipcash:shared:featureflags"))
    implementation(project(":apps:flipcash:shared:userflags"))
    implementation(project(":libs:messaging"))
}
