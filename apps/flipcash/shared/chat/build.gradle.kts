plugins {
    alias(libs.plugins.flipcash.android.feature)
}

android {
    namespace = "${Gradle.flipcashNamespace}.shared.chat"
}

// reactions.json is read from the repo's canonical test-vectors/ rather than copied in.
androidComponents {
    onVariants { variant ->
        val vectors = rootProject.file("test-vectors").path
        variant.hostTests.values.forEach { it.sources.resources?.addStaticSourceDirectory(vectors) }
    }
}

dependencies {
    testImplementation(kotlin("test"))
    testImplementation(libs.bundles.unit.testing)
    testImplementation(libs.robolectric)
    testImplementation(libs.androidx.paging.testing)

    implementation(libs.bundles.kotlinx.serialization)

    implementation(libs.androidx.paging.runtime)
    implementation(libs.androidx.datastore)

    implementation(project(":apps:flipcash:shared:blob"))
    implementation(project(":apps:flipcash:shared:persistence:sources"))
    implementation(project(":apps:flipcash:shared:persistence:db"))
    // `api`, not `implementation`: GroupAccess.groupAccess is an extension on TokenCoordinator,
    // so this module's public surface names a type from it.
    api(project(":apps:flipcash:shared:tokens"))
    implementation(project(":apps:flipcash:shared:analytics"))
    testImplementation(project(":apps:flipcash:shared:analytics:test-fixtures"))
    implementation(project(":services:flipcash"))
    testImplementation(project(":services:flipcash:test-fixtures"))
    implementation(project(":libs:network:connectivity:public"))
    implementation(project(":libs:emojis"))
    implementation(libs.androidx.lifecycle.process)
}
