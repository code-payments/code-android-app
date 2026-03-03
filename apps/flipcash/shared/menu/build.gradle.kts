plugins {
    alias(libs.plugins.flipcash.android.feature)
}

android {
    namespace = "${Gradle.flipcashNamespace}.shared.menu"
}

dependencies {
    implementation(project(":apps:flipcash:shared:featureflags"))
}
