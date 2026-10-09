plugins {
    alias(libs.plugins.flipcash.android.feature)
}

android {
    namespace = "${Gradle.flipcashNamespace}.features.messenger"
}

dependencies {
    implementation(project(":apps:flipcash:shared:analytics"))
    testImplementation(testFixtures(project(":apps:flipcash:shared:analytics")))
    testImplementation(testFixtures(project(":libs:coroutines")))
    implementation(project(":apps:flipcash:shared:blob"))
    implementation(project(":apps:flipcash:shared:bills"))
    implementation(project(":apps:flipcash:shared:blocklist"))
    implementation(project(":apps:flipcash:shared:chat"))
    implementation(project(":apps:flipcash:shared:chat-ui"))
    implementation(project(":apps:flipcash:shared:amount-entry"))
    implementation(project(":apps:flipcash:shared:common-ui"))
    implementation(project(":apps:flipcash:shared:contacts"))
    implementation(project(":apps:flipcash:shared:featureflags"))
    implementation(project(":apps:flipcash:shared:funding"))
    implementation(project(":libs:emojis"))
    implementation(project(":apps:flipcash:shared:menu"))
    implementation(project(":apps:flipcash:shared:payments"))
    // UserProfileDataSource, for the reactors sheet's cached-profile name resolution (decision 4).
    implementation(project(":apps:flipcash:shared:persistence:sources"))
    implementation(project(":apps:flipcash:shared:router"))
    implementation(project(":apps:flipcash:shared:session"))
    implementation(project(":apps:flipcash:shared:shareable"))
    implementation(project(":apps:flipcash:shared:tokens"))
    implementation(project(":apps:flipcash:shared:userflags"))
    implementation(project(":libs:vibrator:bindings"))
    implementation(project(":libs:messaging"))
    implementation(project(":libs:reporting"))
    implementation(project(":services:flipcash"))
    implementation(project(":services:opencode"))
    implementation(project(":libs:datetime"))
    implementation(libs.compose.paging)
    // ProcessLifecycleOwner, for re-asking about a claimable card on every foreground edge.
    implementation(libs.androidx.lifecycle.process)
    implementation(libs.bundles.haze)
    // Stored link previews (PersistedLinkCardMemory) are JSON.
    implementation(libs.kotlinx.serialization.json)
    // HttpUrl parses and normalises outside links (WebLinks).
    implementation(libs.okhttp)
    // The preview picture's own ImageLoader (WebImageLoader), on the client built under the page rules.
    implementation(libs.coil3.core)
    implementation(libs.coil3.network)

    testImplementation(libs.bundles.unit.testing)
    testImplementation(libs.mockito.kotlin)
    // A TLS MockWebServer, to see headers as OkHttp really sends and receives them (WebLinkLookupTest).
    testImplementation(libs.okhttp.mockwebserver)
    testImplementation(libs.okhttp.tls)
    testImplementation(libs.robolectric)
    testImplementation(libs.androidx.paging.testing)
}
