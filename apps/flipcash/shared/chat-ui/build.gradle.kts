plugins {
    alias(libs.plugins.flipcash.android.library.compose)
}

android {
    namespace = "${Gradle.flipcashNamespace}.shared.chat.ui"
}

dependencies {
    implementation(project(":apps:flipcash:core"))
    implementation(project(":ui:core"))
    implementation(project(":ui:components"))
    implementation(project(":ui:theme"))
    implementation(project(":ui:resources"))
    implementation(project(":services:flipcash"))
    implementation(project(":services:opencode-compose"))
    implementation(project(":libs:datetime"))
    implementation(libs.androidx.paging.runtime)
    implementation(libs.compose.paging)
    implementation(project(":apps:flipcash:shared:theme"))
}
