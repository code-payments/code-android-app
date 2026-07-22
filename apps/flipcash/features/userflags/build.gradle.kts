plugins {
    alias(libs.plugins.flipcash.android.feature)
}

android {
    namespace = "${Gradle.flipcashNamespace}.features.userflags"
}

dependencies {
    implementation(project(":apps:flipcash:shared:userflags"))
    implementation(project(":apps:flipcash:shared:region-selection:core"))
    implementation(project(":libs:messaging"))
}
