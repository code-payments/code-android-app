plugins {
    alias(libs.plugins.flipcash.android.library.compose)
}

android {
    namespace = "${Gradle.codeNamespace}.libs.opengraph"
}

dependencies {
    implementation("org.jsoup:jsoup:1.16.1")
    implementation(project(":libs:encryption:utils"))

    implementation(libs.bundles.kotlinx.serialization)
    implementation(libs.bundles.hilt)

    implementation(libs.androidx.datastore)
}
