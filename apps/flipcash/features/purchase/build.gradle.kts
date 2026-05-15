plugins {
    alias(libs.plugins.flipcash.android.feature)
}

android {
    namespace = "${Gradle.flipcashNamespace}.features.purchase"
}

dependencies {
    implementation(project(":apps:flipcash:shared:authentication"))
    implementation(project(":apps:flipcash:shared:google-play-billing"))
    implementation(project(":libs:datetime"))
    implementation(project(":libs:messaging"))

    implementation(project(":services:flipcash"))
    implementation(project(":services:opencode"))

}
