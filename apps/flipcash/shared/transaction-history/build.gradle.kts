plugins {
    alias(libs.plugins.flipcash.android.feature)
}

android {
    namespace = "com.flipcash.shared.transactionhistory"
}

// Home of the transaction-history data engine (ActivityFeedCoordinator / ActivityFeedUpdater),
// consolidated out of the deprecated :shared:activityfeed. Mirrors that module's dependencies.
// See docs/superpowers/plans/2026-08-11-activityfeed-phaseout.md.
dependencies {
    implementation(libs.bundles.room)
    implementation(libs.androidx.paging.runtime)
    implementation(libs.compose.paging)

    compileOnly(project(":apps:flipcash:shared:persistence:db"))
    implementation(project(":apps:flipcash:shared:persistence:sources"))
    implementation(project(":apps:flipcash:shared:common-ui"))
    implementation(project(":services:flipcash"))
    implementation(project(":libs:datetime"))

    testImplementation(libs.bundles.unit.testing)
    testImplementation(testFixtures(project(":ui:resources")))
}
