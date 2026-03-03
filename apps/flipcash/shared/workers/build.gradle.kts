plugins {
    alias(libs.plugins.flipcash.android.feature)
}

android {
    namespace = "${Gradle.flipcashNamespace}.shared.workers"
}

dependencies {
    implementation(libs.androidx.work)
    implementation(libs.kotlinx.serialization.json)

    implementation(libs.hilt.worker)

    implementation(project(":apps:flipcash:shared:authentication"))
    implementation(project(":apps:flipcash:shared:tokens"))
    implementation(project(":services:flipcash"))
}
