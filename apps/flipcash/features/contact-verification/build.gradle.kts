plugins {
    alias(libs.plugins.flipcash.android.feature)
}

android {
    namespace = "${Gradle.flipcashNamespace}.features.contact.verification"
}

dependencies {
    implementation(libs.compose.activities)

    implementation(libs.kotlinx.serialization.json)

    implementation(project(":apps:flipcash:shared:analytics"))
    implementation(project(":apps:flipcash:shared:navigation-flow"))
    implementation(project(":apps:flipcash:shared:phone"))
    implementation(project(":libs:messaging"))
}
