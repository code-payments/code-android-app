plugins {
    alias(libs.plugins.flipcash.android.feature)
}

android {
    namespace = "${Gradle.flipcashNamespace}.shared.anaylytics"
}

dependencies {
    implementation(platform(libs.firebase.bom))
    implementation(libs.firebase.messaging)
    implementation(libs.bugsnag)

    implementation(libs.firebase.perf)

    api(project(":libs:analytics"))
    implementation(project(":services:flipcash"))
    implementation(project(":services:opencode"))

    implementation(libs.mixpanel)

    implementation(libs.androidx.datastore)
}
