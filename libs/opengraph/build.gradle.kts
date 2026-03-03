plugins {
    alias(libs.plugins.flipcash.android.library.compose)
}

android {
    namespace = "${Gradle.codeNamespace}.libs.opengraph"
}

dependencies {
    implementation("org.jsoup:jsoup:1.16.1")
    implementation(project(":libs:encryption:utils"))

    implementation(libs.kotlinx.serialization.json)
    implementation(libs.javax.inject)

    implementation(libs.androidx.datastore)
    implementation(libs.hilt.android)

    implementation(libs.bugsnag)
}
