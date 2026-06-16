plugins {
    alias(libs.plugins.flipcash.android.feature)
}

android {
    namespace = "${Gradle.flipcashNamespace}.shared.phone"
}

dependencies {
    api(libs.lib.phone.number.port)

    api(libs.rinku.compose)

    implementation(libs.play.services.auth.api.phone)

    testImplementation(kotlin("test"))
    testImplementation(libs.bundles.unit.testing)
    testImplementation(libs.robolectric)
}
