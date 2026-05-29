plugins {
    alias(libs.plugins.flipcash.android.feature)
}

android {
    namespace = "${Gradle.flipcashNamespace}.features.invite"
}

dependencies {
    implementation(project(":apps:flipcash:shared:invite"))
}
