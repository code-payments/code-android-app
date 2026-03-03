plugins {
    alias(libs.plugins.flipcash.android.feature)
}

android {
    namespace = "${Gradle.flipcashNamespace}.features.withdrawal"
}

dependencies {
    implementation(project(":apps:flipcash:shared:activityfeed"))
    implementation(project(":apps:flipcash:shared:analytics"))
    implementation(project(":apps:flipcash:shared:tokens"))
    implementation(project(":libs:messaging"))

    implementation(project(":services:flipcash"))

}
