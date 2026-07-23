plugins {
    alias(libs.plugins.flipcash.android.feature)
}

android {
    namespace = "${Gradle.flipcashNamespace}.shared.notifications"
    testOptions.unitTests.isIncludeAndroidResources = true
}

dependencies {
    implementation(project(":apps:flipcash:shared:authentication"))
    implementation(project(":apps:flipcash:shared:chat"))
    implementation(project(":apps:flipcash:shared:contacts"))
    implementation(project(":apps:flipcash:shared:persistence:sources"))
    implementation(project(":apps:flipcash:shared:phone"))
    implementation(project(":apps:flipcash:shared:push"))
    implementation(project(":apps:flipcash:shared:tokens"))
    implementation(project(":services:flipcash"))

    implementation(platform(libs.firebase.bom))
    implementation(libs.firebase.messaging)

    // Coil core (no Compose) to load remote profile-picture avatars for chat
    // notifications, reusing the app's SingletonImageLoader + disk cache.
    implementation(libs.coil3.core)

    implementation(libs.androidx.datastore)

    testImplementation(kotlin("test"))
    testImplementation(libs.robolectric)
    testImplementation(libs.mockk)
    testImplementation(libs.kotlinx.coroutines.test)
}
