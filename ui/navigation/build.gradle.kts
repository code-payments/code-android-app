plugins {
    alias(libs.plugins.flipcash.android.library.compose)
    id("org.jetbrains.kotlin.plugin.parcelize")
}

android {
    namespace = "${Gradle.codeNamespace}.navigation"
}

dependencies {
    implementation(project(":libs:logging"))
    implementation(project(":ui:core"))
    implementation(project(":ui:theme"))
    implementation(libs.androidx.annotation)
    api(libs.kotlin.stdlib)
    api(libs.rxjava)
    api(libs.rxandroid)
    implementation(libs.compose.material)
    implementation(libs.compose.activities)
    implementation(libs.androidx.lifecycle.runtime)
    implementation(libs.androidx.lifecycle.viewmodel)
    implementation(libs.androidx.navigation.fragment)
    api(libs.voyager.navigator)
    api(libs.voyager.transitions)
    api(libs.voyager.bottomsheet)
    api(libs.voyager.tabs)
    api(libs.voyager.hilt)
    api(libs.rinku)
}
