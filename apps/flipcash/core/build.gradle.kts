plugins {
    alias(libs.plugins.flipcash.android.feature)
}

android {
    namespace = "${Gradle.flipcashNamespace}.core"
}

dependencies {
    testImplementation(kotlin("test"))
    testImplementation(libs.bundles.unit.testing)

    implementation(libs.androidx.browser)

    implementation(libs.kotlinx.serialization.core)
    implementation(libs.kotlinx.serialization.json)

    implementation(platform(libs.firebase.bom))
    implementation(libs.firebase.messaging)
    implementation(libs.bugsnag)

    implementation(libs.androidx.localbroadcastmanager)
    implementation(libs.androidx.credentials)
    implementation(libs.androidx.credentials.play.auth)
    implementation(libs.androidx.datastore)
    implementation(libs.compose.material3)

    api(libs.coil3)
    api(libs.coil3.network)

    api(project(":services:flipcash-compose"))

    implementation(project(":libs:messaging"))
    api(project(":libs:permissions:public"))
    implementation(project(":libs:vibrator:public"))

    implementation(project(":apps:flipcash:shared:userflags"))
    api(project(":apps:flipcash:shared:theme"))

    api(libs.sodium.bindings)

    api(project(":vendor:kik:scanner"))

    api(project(":ui:core"))
    api(libs.navigation3.runtime)

    api(project(":vendor:tipkit:tipkit-m2"))
}
