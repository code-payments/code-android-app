plugins {
    alias(libs.plugins.flipcash.android.feature)
}

android {
    namespace = "${Gradle.flipcashNamespace}.features.tokens"
}

dependencies {
    implementation(libs.compose.activities)

    implementation(libs.haze)
    implementation(libs.haze.materials)

    implementation(project(":apps:flipcash:shared:analytics"))
    implementation(project(":apps:flipcash:shared:onramp:deeplinks"))
    implementation(project(":apps:flipcash:shared:shareable"))
    implementation(project(":apps:flipcash:shared:tokens"))

    implementation(project(":libs:datetime"))
    implementation(project(":libs:messaging"))
    implementation(project(":libs:quickresponse"))
    implementation(project(":libs:vibrator:bindings"))
}
