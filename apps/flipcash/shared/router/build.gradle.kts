plugins {
    alias(libs.plugins.flipcash.android.feature)
}

android {
    namespace = "${Gradle.flipcashNamespace}.shared.router"
}

dependencies {
    api(project(":ui:navigation"))
    api(libs.rinku.compose)

    implementation(project(":libs:models"))

    testImplementation(kotlin("test"))
    testImplementation(libs.robolectric)
}
