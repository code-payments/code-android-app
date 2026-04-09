plugins {
    alias(libs.plugins.flipcash.android.library.compose)
}

android {
    namespace = "${Gradle.codeNamespace}.libs.permissions"
}

dependencies {
    implementation(project(":libs:logging"))
    implementation(project(":libs:messaging"))
    implementation(project(":ui:components"))
    implementation(project(":ui:resources"))

    implementation(libs.compose.activities)
    implementation(libs.kotlinx.serialization.json)
    implementation(libs.kotlinx.datetime)
    implementation(libs.javax.inject)

    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.junit)
    androidTestImplementation(libs.androidx.test.runner)
    implementation(libs.hilt.android)
}
