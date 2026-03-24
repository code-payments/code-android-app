plugins {
    alias(libs.plugins.flipcash.android.feature)
}

android {
    namespace = "${Gradle.flipcashNamespace}.shared.onramp.coinbase"
    defaultConfig {
        buildConfigField("String", "COINBASE_ONRAMP_API_KEY", "\"${tryReadProperty(rootProject.rootDir, "COINBASE_ONRAMP_API_KEY")}\"")
    }
}

dependencies {
    implementation(libs.androidx.localbroadcastmanager)
    implementation(libs.kotlinx.serialization.json)

    implementation(project(":apps:flipcash:shared:web"))
    api(project(":libs:network:coinbase:onramp"))
    implementation(project(":libs:network:jwt"))
}
