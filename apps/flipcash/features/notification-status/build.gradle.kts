plugins {
    alias(libs.plugins.flipcash.android.feature)
}

android {
    namespace = "${Gradle.flipcashNamespace}.features.notificationstatus"
}

dependencies {
    implementation(project(":apps:flipcash:shared:authentication"))
    implementation(project(":apps:flipcash:shared:notifications"))
    implementation(project(":libs:logging"))
    implementation(libs.play.services.base)
    implementation(libs.bundles.haze)

    testImplementation(kotlin("test"))
    testImplementation(libs.bundles.unit.testing)
}
