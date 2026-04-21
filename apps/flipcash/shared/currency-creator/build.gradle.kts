plugins {
    alias(libs.plugins.flipcash.android.feature)
}

android {
    namespace = "${Gradle.flipcashNamespace}.shared.currencycreator"
}

dependencies {
    implementation(project(":apps:flipcash:shared:persistence:sources"))
    implementation(project(":services:flipcash"))
    implementation(project(":services:opencode"))
}
