plugins {
    alias(libs.plugins.flipcash.android.feature)
}

android {
    namespace = "${Gradle.flipcashNamespace}.shared.session"
}

dependencies {
    testImplementation(kotlin("test"))
    testImplementation(libs.bundles.unit.testing)
    testImplementation(project(":libs:coroutines:test-fixtures"))
    testImplementation(project(":ui:resources:test-fixtures"))

    implementation(project(":apps:flipcash:shared:blob"))
    implementation(project(":apps:flipcash:shared:blocklist"))
    implementation(project(":apps:flipcash:shared:chat"))
    implementation(project(":apps:flipcash:shared:contacts"))
    implementation(project(":apps:flipcash:shared:transaction-history"))
    implementation(project(":apps:flipcash:shared:analytics"))
    testImplementation(project(":apps:flipcash:shared:analytics:test-fixtures"))
    implementation(project(":apps:flipcash:shared:appsettings"))
    implementation(project(":apps:flipcash:shared:google-play-billing"))
    implementation(project(":apps:flipcash:shared:featureflags"))
    implementation(project(":apps:flipcash:shared:funding"))
    implementation(project(":apps:flipcash:shared:shareable"))
    implementation(project(":apps:flipcash:shared:tipping"))
    implementation(project(":apps:flipcash:shared:tokens"))
    implementation(project(":services:flipcash"))
    implementation(project(":libs:datetime"))
    implementation(project(":libs:messaging"))
    implementation(project(":libs:vibrator:bindings"))
}
