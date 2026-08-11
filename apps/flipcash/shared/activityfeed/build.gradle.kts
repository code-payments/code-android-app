plugins {
    alias(libs.plugins.flipcash.android.feature)
}

android {
    namespace = "${Gradle.flipcashNamespace}.shared.activityfeed"
}

dependencies {
    // Deprecated shim module: it only forwards to the new home (:shared:transaction-history) via
    // @Deprecated typealiases. `api` (not implementation) so the underlying types resolve
    // transitively for callers still importing the old com.flipcash.app.activityfeed package
    // during the phase-out. Removed once all consumers migrate (plan Task 8, gated on v2 launch).
    // See docs/superpowers/plans/2026-08-11-activityfeed-phaseout.md.
    api(project(":apps:flipcash:shared:transaction-history"))
}
