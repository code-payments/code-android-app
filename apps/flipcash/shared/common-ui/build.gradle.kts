plugins {
    alias(libs.plugins.flipcash.android.library.compose)
}

android {
    namespace = "${Gradle.flipcashNamespace}.shared.common.ui"
}

dependencies {
    implementation(project(":apps:flipcash:core"))
    implementation(project(":ui:core"))
    implementation(project(":ui:components"))
    implementation(project(":ui:theme"))
    implementation(project(":ui:resources"))
    implementation(project(":services:flipcash"))
    implementation(project(":libs:datetime"))
    implementation(project(":apps:flipcash:shared:theme"))

    api(libs.compose.material.icons.extended)
    testImplementation(libs.robolectric)
}
