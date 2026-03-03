plugins {
    alias(libs.plugins.flipcash.android.feature)
}

android {
    namespace = "${Gradle.flipcashNamespace}.shared.billing"
}

dependencies {
    implementation(libs.androidx.datastore)

    api(libs.google.play.billing.runtime)
    api(libs.google.play.billing.ktx)

    implementation(project(":apps:flipcash:shared:analytics"))
    implementation(project(":apps:flipcash:shared:currency-selection:core"))
    implementation(project(":libs:datetime"))
}
