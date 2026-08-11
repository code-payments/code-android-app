package com.flipcash.app.activityfeed

import com.flipcash.app.core.updater.NetworkUpdater
import javax.inject.Inject

@Deprecated(
    "Phasing out with the v2 (FeatureFlag.NewUi) launch: moving into :shared:transaction-history " +
        "(com.flipcash.shared.transactionhistory) alongside ActivityFeedCoordinator. " +
        "Plan: docs/superpowers/plans/2026-08-11-activityfeed-phaseout.md",
)
@Suppress("DEPRECATION") // constructor references the (also-deprecated) coordinator during phase-out
class ActivityFeedUpdater @Inject constructor(
    private val coordinator: ActivityFeedCoordinator,
): NetworkUpdater() {
    override suspend fun doUpdate() {
        coordinator.fetchSinceLatest(count = 50)
    }
}
