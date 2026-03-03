plugins {
    alias(libs.plugins.flipcash.android.feature)
}

android {
    namespace = "${Gradle.flipcashNamespace}.shared.flags"
}

dependencies {
    implementation(project(":apps:flipcash:shared:ksp"))
    ksp(project(":apps:flipcash:shared:ksp"))

    implementation(platform(libs.firebase.bom))
    implementation(libs.firebase.messaging)
    implementation(libs.bugsnag)

    implementation(libs.androidx.datastore)
}
