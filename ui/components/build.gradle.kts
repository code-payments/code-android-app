plugins {
    alias(libs.plugins.flipcash.android.library.compose)
    id("org.jetbrains.kotlin.plugin.parcelize")
}

android {
    namespace = "com.getcode.ui.components"

    buildFeatures {
        buildConfig = true
    }
}

dependencies {
    testImplementation(kotlin("test"))
    implementation(project(":libs:datetime"))
    implementation(project(":libs:encryption:ed25519"))
    implementation(project(":libs:encryption:utils"))
    implementation(project(":libs:currency"))
    implementation(project(":libs:messaging"))
    implementation(project(":libs:models"))
    implementation(project(":libs:network:exchange"))
    implementation(project(":libs:network:connectivity:public"))
    implementation(project(":libs:opengraph"))
    implementation(project(":libs:vibrator:public"))
    api(project(":ui:core"))
    implementation(project(":ui:emojis"))
    implementation(project(":ui:theme"))
    implementation(project(":ui:resources"))
    api(libs.cloudy)
    implementation(libs.coil3)
    implementation(libs.coil3.network)
    implementation(libs.kotlinx.datetime)
    implementation(libs.compose.animation)
    implementation(libs.compose.ui.text)
    implementation(libs.compose.activities)
    implementation(project(":ui:navigation"))
    debugApi(libs.compose.ui.tools)
    api(libs.compose.ui.tools.preview)
    implementation(libs.compose.material)
    api(libs.compose.material3)
    implementation(libs.compose.material.icons.extended)
    implementation(libs.compose.accompanist)
    implementation(libs.compose.paging)
    api(libs.vico.compose)
    implementation(libs.bundles.haze)
}
