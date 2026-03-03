plugins {
    alias(libs.plugins.flipcash.android.feature)
}

android {
    namespace = "${Gradle.flipcashNamespace}.shared.activityfeed"
}

dependencies {
    implementation(libs.androidx.room.ktx)
    implementation(libs.androidx.room.paging)
    implementation(libs.androidx.paging.runtime)

    compileOnly(project(":apps:flipcash:shared:persistence:db"))
    implementation(project(":apps:flipcash:shared:persistence:sources"))
    implementation(project(":services:flipcash"))
    implementation(project(":libs:datetime"))
}
