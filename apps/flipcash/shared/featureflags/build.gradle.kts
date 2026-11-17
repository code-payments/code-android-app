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

    implementation(libs.androidx.datastore)

    testImplementation(kotlin("test"))
    testImplementation(libs.bundles.unit.testing)
    testImplementation(libs.robolectric)
    testImplementation(project(":libs:coroutines:test-fixtures"))
}
