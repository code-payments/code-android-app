plugins {
    alias(libs.plugins.flipcash.android.feature)
}

android {
    namespace = "${Gradle.flipcashNamespace}.shared.invite"
}

dependencies {
    implementation(project(":apps:flipcash:shared:shareable"))
}
