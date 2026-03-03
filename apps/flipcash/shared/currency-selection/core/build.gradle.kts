plugins {
    alias(libs.plugins.flipcash.android.feature)
}

android {
    namespace = "${Gradle.flipcashNamespace}.features.currency"
    buildFeatures {
        buildConfig = false
    }
}

dependencies {
    implementation(libs.androidx.datastore)

}
