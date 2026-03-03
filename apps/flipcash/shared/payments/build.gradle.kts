plugins {
    alias(libs.plugins.flipcash.android.feature)
}

android {
    namespace = "${Gradle.flipcashNamespace}.shared.payments"
}

dependencies {
    implementation(libs.androidx.localbroadcastmanager)

    implementation(project(":apps:flipcash:shared:activityfeed"))
    implementation(project(":libs:messaging"))
}
