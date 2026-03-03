plugins {
    alias(libs.plugins.flipcash.android.feature)
}

android {
    namespace = "${Gradle.flipcashNamespace}.features.payments"
}

dependencies {
    implementation(libs.compose.activities)

    implementation(project(":apps:flipcash:shared:payments"))
    implementation(project(":libs:messaging"))

    implementation(project(":services:flipcash"))

}
