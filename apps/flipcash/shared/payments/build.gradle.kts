plugins {
    alias(libs.plugins.flipcash.android.feature)
}

android {
    namespace = "${Gradle.flipcashNamespace}.shared.payments"
}

// reaction-boost-split.json is read from the repo's canonical test-vectors/ rather than copied in.
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

    implementation(project(":apps:flipcash:core"))
    implementation(project(":apps:flipcash:shared:chat"))
    implementation(project(":apps:flipcash:shared:contacts"))
    implementation(project(":apps:flipcash:shared:tokens"))
    implementation(project(":apps:flipcash:shared:userflags"))
    implementation(project(":services:flipcash"))
    implementation(project(":services:opencode"))
}
