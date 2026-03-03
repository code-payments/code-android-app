plugins {
    alias(libs.plugins.flipcash.android.feature)
}

android {
    namespace = "${Gradle.flipcashNamespace}.features.currency"
}

dependencies {
    implementation(libs.androidx.datastore)

    implementation(project(":apps:flipcash:shared:currency-selection:core"))
    implementation(project(":libs:datetime"))
}
