plugins {
    alias(libs.plugins.flipcash.android.feature)
}

android {
    namespace = "${Gradle.flipcashNamespace}.shared.transfers"
}

dependencies {
    implementation(project(":libs:messaging"))

}
