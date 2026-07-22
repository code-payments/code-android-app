plugins {
    alias(libs.plugins.flipcash.android.feature)
}

android {
    namespace = "${Gradle.flipcashNamespace}.features.tipping"
}

dependencies {
    implementation(project(":services:flipcash"))
    implementation(project(":apps:flipcash:shared:bills"))
    implementation(project(":apps:flipcash:shared:chat"))
    implementation(project(":apps:flipcash:shared:chat-ui"))
    implementation(project(":apps:flipcash:shared:tipping"))
}
