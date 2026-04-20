plugins {
    alias(libs.plugins.flipcash.android.feature)
}

android {
    namespace = "${Gradle.flipcashNamespace}.features.onramp"
    defaultConfig {
        buildConfigField("String", "COINBASE_ONRAMP_API_KEY", "\"${tryReadProperty(rootProject.rootDir, "COINBASE_ONRAMP_API_KEY")}\"")
    }
}

dependencies {
    implementation(libs.compose.activities)
    implementation(libs.compose.webview)

    implementation(libs.androidx.localbroadcastmanager)

    implementation(libs.kotlinx.serialization.core)
    implementation(libs.kotlinx.serialization.json)

    implementation(project(":apps:flipcash:shared:onramp:coinbase"))
    implementation(project(":apps:flipcash:shared:onramp:deeplinks"))
    implementation(project(":apps:flipcash:shared:router"))

    implementation(project(":libs:crypto:solana"))
    implementation(project(":libs:datetime"))
    implementation(project(":libs:messaging"))
}
