plugins {
    alias(libs.plugins.flipcash.android.feature)
}

android {
    namespace = "${Gradle.flipcashNamespace}.shared.onramp.deeplinks"
}

dependencies {
    implementation(libs.kotlinx.serialization.json)

    implementation(project(":apps:flipcash:shared:analytics"))
    implementation(project(":libs:crypto:solana"))
    implementation(project(":libs:messaging"))
}
