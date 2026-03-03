plugins {
    alias(libs.plugins.flipcash.android.feature)
}

android {
    namespace = "${Gradle.flipcashNamespace}.shared.onramp.common"
}

dependencies {
    implementation(libs.kotlinx.serialization.json)

    implementation(project(":apps:flipcash:shared:analytics"))
    implementation(project(":apps:flipcash:shared:router"))
    implementation(project(":libs:messaging"))
}
