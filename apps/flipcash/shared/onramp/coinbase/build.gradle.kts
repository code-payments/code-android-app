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
    testImplementation(kotlin("test"))
    testImplementation(libs.bundles.unit.testing)
    testImplementation(libs.robolectric)

    implementation(libs.androidx.localbroadcastmanager)
    implementation(libs.kotlinx.serialization.json)

    implementation(libs.play.services.wallet)
    implementation(libs.kotlinx.coroutines.play.services)
    implementation(project(":libs:messaging"))
    implementation(project(":apps:flipcash:shared:featureflags"))
    implementation(project(":apps:flipcash:shared:web"))
    api(project(":libs:network:coinbase:onramp"))
    implementation(project(":libs:network:jwt"))
}
