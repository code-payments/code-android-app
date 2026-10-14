plugins {
    alias(libs.plugins.flipcash.android.feature)
}

android {
    namespace = "${Gradle.flipcashNamespace}.shared.appupdates"
}

dependencies {
    api(libs.google.play.app.updates.runtime)
    api(libs.google.play.app.updates.ktx)

    implementation(project(":apps:flipcash:shared:userflags"))
    implementation(libs.androidx.datastore)
    implementation(libs.okhttp)
    implementation(libs.kotlinx.serialization.json)
    // ProcessLifecycleOwner: a flexible update that finishes downloading in the background installs.
    implementation(libs.androidx.lifecycle.process)

    testImplementation(kotlin("test"))
    testImplementation(libs.bundles.unit.testing)
    testImplementation(libs.robolectric)
    testImplementation(testFixtures(project(":libs:coroutines")))
}
