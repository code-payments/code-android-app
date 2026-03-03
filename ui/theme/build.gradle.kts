plugins {
    alias(libs.plugins.flipcash.android.library.compose)
}

android {
    namespace = "com.getcode.theme"

    buildFeatures {
        buildConfig = true
    }
}

dependencies {    debugImplementation(libs.compose.ui.tools)
    implementation(libs.compose.ui.tools.preview)
    implementation(libs.compose.material)
    implementation(libs.compose.accompanist)

    implementation(libs.androidx.appcompat)
    implementation(libs.androidx.core)
    implementation(libs.androidx.activity)
    implementation(libs.androidx.navigation.fragment)
}
