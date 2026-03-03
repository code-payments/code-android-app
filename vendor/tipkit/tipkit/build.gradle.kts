plugins {
    alias(libs.plugins.flipcash.android.library.compose)
}

android {
    namespace = "dev.bmcreations.tipkit"
}

dependencies {
    implementation(libs.compose.animation)
    implementation(libs.compose.ui.graphics)
    implementation(libs.compose.animation)
    implementation(libs.compose.ui.tools.preview)
    implementation(libs.kotlinx.serialization.core)
    implementation(libs.kotlinx.serialization.json)
    implementation(libs.kotlinx.datetime)
    implementation(libs.androidx.datastore)
}
