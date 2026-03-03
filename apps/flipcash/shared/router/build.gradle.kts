plugins {
    alias(libs.plugins.flipcash.android.feature)
}

android {
    namespace = "${Gradle.flipcashNamespace}.shared.router"
}

dependencies {
    api(project(":ui:navigation"))
    implementation(project(":apps:flipcash:shared:analytics"))
    api(libs.rinku.compose)
}
