plugins {
    alias(libs.plugins.flipcash.android.feature)
}

android {
    namespace = "${Gradle.flipcashNamespace}.features.deposit"
}

dependencies {
    implementation(project(":libs:messaging"))

    implementation(project(":services:flipcash"))

}
