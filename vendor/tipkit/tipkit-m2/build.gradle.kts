plugins {
    alias(libs.plugins.flipcash.android.library.compose)
}

android {
    namespace = "dev.bmcreations.tipkit"
}

dependencies {
    api(project(":vendor:tipkit:tipkit"))
    implementation(project(":ui:theme"))
    implementation(libs.compose.ui.graphics)
    implementation(libs.compose.material)
    implementation(libs.compose.material.icons.core)
}
