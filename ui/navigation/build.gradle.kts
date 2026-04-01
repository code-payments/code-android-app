plugins {
    alias(libs.plugins.flipcash.android.library.compose)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.kotlin.ksp)
    alias(libs.plugins.hilt)
    id("org.jetbrains.kotlin.plugin.parcelize")
}

android {
    namespace = "${Gradle.codeNamespace}.navigation"
    buildFeatures { buildConfig = true }
}

dependencies {
    api(project(":libs:coroutines"))
    implementation(project(":libs:logging"))
    implementation(project(":ui:core"))
    api(project(":ui:resources"))
    implementation(project(":ui:theme"))

    api(libs.rxjava)

    implementation(libs.compose.material3)
    implementation(libs.compose.activities)
    implementation(libs.kotlinx.serialization.json)
    implementation(libs.kotlin.reflect)

    implementation(libs.hilt.android)
    ksp(libs.hilt.android.compiler)

    api(libs.navigation3.runtime)
    api(libs.navigation3.ui)
    api(libs.lifecycle.viewmodel.navigation3)
    api(libs.hilt.nav.compose)
    api(libs.rinku)
}
